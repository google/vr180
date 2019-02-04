// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <stdint.h>  // NOLINT
#include <algorithm>
#include <iomanip>

#include <Eigen/Dense>
#include "cpp/sensor_fusion/geometry_toolbox_mahony.h"
#include "cpp/sensor_fusion/low_pass_filter.h"
#include "cpp/sensor_fusion/orientation_filter.h"
#include "cpp/sensor_fusion/stationary_detector.h"

namespace {

// Minimum time step between sensor updates. This corresponds to 1000 Hz.
static const double kMinTimestepS = 0.001f;

// Maximum time step between sensor updates. This corresponds to 1 Hz.
static const double kMaxTimestepS = 1.0f;

// Gravity constant in m.s^(-2).
static const double kMagnitudeOfGravity = 9.81;

// Small threshold to check if close to zero.
static const double kEpsilon = 1e-9;

// Number of runs for bias correction to remain static.
// This is less than 1 sec.
static const int kMagBiasCorrectionStaticCount = 40;

// Small threshold used for mag initialization.
static const double kMagInitTolerance = 1e-6;

// Number of mag samples used for mag measurement initialization. 25 samples
// correspond to ~.4 seconds for mag at 60 Hz.
static const int kNumMagForInitialization = 25;

// Number of continuous invalid mag measurement that results in temporarily
// disabling mag fusion, unless new mag calibration is performed.
static const int kNumMagForFilterOutlierRejection = 10;

// Threshold of rejecting using one mag measurement. The value for this
// threshold is in rad, which corresponds to 5 degree.
static const double kMaxAllowedMagDeviationRadians = 0.0872665;

// Threshold of using mag measurements on its timestamp. If the timestamp
// between mag and gyro is larger than the given value, we simply do not use
// the current mag measurement. This behavior happens in this multi-threaded
// sensor fusion system, but not often.
static const double kMaxTimeDifferentInMagAndGyroInSeconds = 0.003f;

// Helper method to validate the time steps of sensor timestamps.
bool IsTimestampDeltaValid(double timestamp_delta_s) {
  if (timestamp_delta_s <= kMinTimestepS) {
    return false;
  }
  if (timestamp_delta_s > kMaxTimestepS) {
    return false;
  }
  return true;
}

mahony_filter::StationaryDetector::StationaryDetectorConfiguration
GetStationaryDetectorConfig(double stationary_bias_correction_gain) {
  mahony_filter::StationaryDetector::StationaryDetectorConfiguration
      stationary_config;
  stationary_config.stationary_bias_correction_gain =
      stationary_bias_correction_gain;
  return stationary_config;
}

}  // namespace

OrientationFilter::OrientationFilter(
    const OrientationFilterConfiguration& config)
    : config_(config),
      state_(Eigen::Matrix<double, 7, 1>::Zero()),
      next_state_(Eigen::Matrix<double, 7, 1>::Zero()),
      is_orientation_initialized_(false),
      first_accel_timestamp_s_(0.0),
      has_received_gyro_sample_(false),
      mag_is_available_(false),
      mag_low_pass_filter_(config.magnetometer_low_pass_cutoff_frequency),
      accel_aligned_R_yaw_mag_aligned_(Eigen::Matrix3d::Identity()),
      num_mag_measurements_for_yaw_initialization_(kNumMagForInitialization),
      mag_meas_for_init_index_(0),
      mag_bias_(Eigen::Vector3d::Zero()),
      new_mag_calibration_available_(false),
      mag_status_(MagStatus::kInitial),
      accumulated_num_of_outlier_mag_measurement_(0),
      current_mag_sample_fits_calibration_(false),
      stationary_detector_(
          GetStationaryDetectorConfig(config.stationary_bias_correction_gain)) {
  // Initialize quaternions to start with the identity rotation.
  state_(3) = 1.0;
  next_state_(3) = 1.0;

  // Initialize the data buffer for mag initialization.
  projected_mag_measurement_vector_.resize(
      Eigen::NoChange, num_mag_measurements_for_yaw_initialization_);

  // Set bias of magnetometer calibrator when it's provided in configuration.
  if (config_.init_mag_bias != Eigen::Vector3d::Zero()) {
    SetMagBias(config_.init_mag_bias);
  }
}

void OrientationFilter::Run() {
  // Initialization for the filter orientation.
  if (!is_orientation_initialized_ &&
      current_accel_measurement_.timestamp_s > 0.0 &&
      (config_.init_config ==
           OrientationFilterConfiguration::InitialOrientationConfiguration::
               kDontUseMagToInitOrientation ||
       current_mag_measurement_.timestamp_s > 0.0)) {
    is_orientation_initialized_ = OrientationFromAccelAndMag();
  }

  // Only start to propagate once the orientation has already been initialized
  // by the accel / mag.
  if (is_orientation_initialized_) {
    FilterPropagate();
  }
}

void OrientationFilter::RegisterOnBadMagnetometerCalibrationDetectedCallback(
    std::function<void()>* on_bad_mag_calibration_detected_callback) {
  on_bad_mag_calibration_detected_callbacks_.insert(
      on_bad_mag_calibration_detected_callback);
}

void OrientationFilter::UnRegisterOnBadMagnetometerCalibrationDetectedCallback(
    std::function<void()>* on_bad_mag_calibration_detected_callback) {
  on_bad_mag_calibration_detected_callbacks_.erase(
      on_bad_mag_calibration_detected_callback);
}

void OrientationFilter::BadMagnetometerCalibrationDetectedBroadcast() const {
  for (auto callback : on_bad_mag_calibration_detected_callbacks_) {
    if (callback) {
      (*callback)();
    }
  }
}

void OrientationFilter::AddAccelMeasurement(const Eigen::Vector3d& sample,
                                            double timestamp_s) {
  if (first_accel_timestamp_s_ == 0.0) {
    first_accel_timestamp_s_ = timestamp_s;
    if (is_orientation_initialized_) {
      // Check if the state is aligned with gravity and fix it if it is not.
      const Eigen::Vector3d g_from_acc = sample.normalized();
      const Eigen::Vector3d g_est = ComputeGravityEstimate();

      double dot_product = g_from_acc.dot(g_est);
      // Clamp dot_prodcut.
      if (std::abs(dot_product) > 1.0) {
        dot_product = (dot_product > 0.0 ? 1.0 : -1.0);
      }
      const double angular_error_deg = 180. / M_PI * acos(dot_product);
      // TODO: Add a better gravity / acceleration check here.
      if (std::abs(angular_error_deg) > 44) {
        // The error after SetPose is too large. Correct directly the aligment
        // with gravity.
        const Eigen::Vector4d correction_quat =
            geometry_toolbox::RotateInto(g_est, g_from_acc);

        state_.head<4>() = geometry_toolbox::QuaternionMultiplication(
            correction_quat, state_.head<4>());
      }
    }
  }

  current_accel_measurement_ = SensorSample(sample, timestamp_s);

  if (IsStationaryBiasCorrectionEnabled()) {
    stationary_detector_.AddAccelMeasurement(current_accel_measurement_.sample,
                                             timestamp_s);
  }

  if (!has_received_gyro_sample_) {
    // If we have not received a gyro sample supply fake gyro data to make use
    // of every accel sample.
    current_gyro_measurement_ =
        SensorSample(Eigen::Vector3d::Zero(), timestamp_s);
    Run();
  }
}

void OrientationFilter::AddGyroMeasurement(const Eigen::Vector3d& sample,
                                           double timestamp_s) {
  current_gyro_measurement_ = SensorSample(sample, timestamp_s);

  if (IsStationaryBiasCorrectionEnabled()) {
    stationary_detector_.AddGyroMeasurement(current_gyro_measurement_.sample,
                                            timestamp_s);
  }

  const double delta_t_s =
      (timestamp_s - previous_gyro_measurement_.timestamp_s);
  if (IsTimestampDeltaValid(delta_t_s)) {
    if (!has_received_gyro_sample_) {
      has_received_gyro_sample_ = true;
    }
    Run();
  }

  previous_gyro_measurement_ = current_gyro_measurement_;
}

void OrientationFilter::AddMagMeasurement(const Eigen::Vector3d& sample,
                                          double timestamp_s,
                                          bool fits_calibration) {
  // First sample is only used for setting up the timeline.
  if (current_mag_measurement_.timestamp_s == 0.0) {
    current_mag_measurement_ = SensorSample(sample, timestamp_s);
    current_mag_sample_fits_calibration_ = fits_calibration;
    return;
  }

  const double mag_delta_time_s =
      (timestamp_s - previous_mag_measurement_.timestamp_s);

  // Use uncalibrated mag correction only if stationary correction is not
  // enabled.
  if (!IsStationaryBiasCorrectionEnabled()) {
    // Now that the timestep is valid we can initialize the low_pass filter.
    mag_low_pass_filter_.AddSampleData(sample, mag_delta_time_s);
    if (mag_low_pass_filter_.IsInitialized()) {
      previous_mag_measurement_ =
          SensorSample(mag_low_pass_filter_.GetFilteredData(),
                       current_mag_measurement_.timestamp_s);
    }
  } else {
    previous_mag_measurement_ = current_mag_measurement_;
  }

  current_mag_measurement_ = SensorSample(sample, timestamp_s);
  current_mag_sample_fits_calibration_ = fits_calibration;

  // Only process valid mag sample.
  mag_is_available_ = IsTimestampDeltaValid(mag_delta_time_s);
}

bool OrientationFilter::ComputeIterativeSolution(
    const Eigen::VectorXd& mag_vector, const double initial_solution,
    Eigen::Matrix3d* accel_aligned_R_yaw_mag_aligned) {
  CHECK(accel_aligned_R_yaw_mag_aligned != nullptr);
  double current_solution = initial_solution;

  // Parameters for Gauss-Newton algorithm.
  constexpr int kMaxIteration = 25;
  constexpr double kNormCorrectionForConvergence = 1e-5;

  int current_iteration = 0;
  for (; current_iteration < kMaxIteration; ++current_iteration) {
    // Estimated measurement and Jacobian for Gauss-Newton.
    Eigen::Vector2d z_est(-std::sin(current_solution),
                          std::cos(current_solution));
    Eigen::Vector2d Jacobian(-std::cos(current_solution),
                             -std::sin(current_solution));

    double residual = 0;
    double hessian = 0;

    for (int i = 0; i < mag_vector.rows() / 2; ++i) {
      const Eigen::Vector2d residual_i = z_est - mag_vector.segment<2>(i * 2);
      residual += Jacobian.transpose() * residual_i;
      hessian += Jacobian.transpose() * Jacobian;
    }

    double correction = -residual / hessian;
    if (hessian < kMagInitTolerance) {
      // Invalid Hessian, mag initialization fails.
      return false;
    }

    current_solution += correction;

    if (std::abs(correction) < kNormCorrectionForConvergence) {
      break;
    }
  }

  if (current_iteration >= kMaxIteration) {
    // Maximum iteration reached, mag initialization fails.
    return false;
  }

  *accel_aligned_R_yaw_mag_aligned << std::cos(current_solution),
      -std::sin(current_solution), 0, std::sin(current_solution),
      std::cos(current_solution), 0, 0, 0, 1;

  return true;
}

bool OrientationFilter::ComputeYawAlignmentMatrix(
    const Eigen::Matrix3Xd& mag_proj,
    Eigen::Matrix3d* accel_aligned_R_yaw_mag_aligned) {
  CHECK(accel_aligned_R_yaw_mag_aligned != nullptr);
  const int num_rows = 2 * mag_proj.cols();
  Eigen::VectorXd mag_vector(num_rows, 1);
  for (int i = 0; i < mag_proj.cols(); ++i) {
    if (mag_proj.col(i).head<2>().squaredNorm() < kMagInitTolerance) {
      return false;
    }

    mag_vector.segment<2>(i * 2) = mag_proj.col(i).head<2>().normalized();
  }

  const double init_solution = -std::atan2(mag_vector(0), mag_vector(1));

  return ComputeIterativeSolution(mag_vector, init_solution,
                                  accel_aligned_R_yaw_mag_aligned);
}

bool OrientationFilter::OrientationFromAccelAndMag() {
  Eigen::Vector3d L_x;
  Eigen::Vector3d L_y;
  Eigen::Vector3d L_z;
  Eigen::Matrix<double, 3, 3> L_R_G;

  // East-North-Up frame of reference:
  //   - Gravity vector lies along +z axis
  //   - Horizontal component of Mag vector lies along +y axis
  //   - x-axis points east
  L_z = current_accel_measurement_.sample.normalized();

  switch (config_.init_config) {
    case OrientationFilterConfiguration::InitialOrientationConfiguration::
        kDontUseMagToInitOrientation: {
      // Depending on whether we are portrait or landscape, this will
      // be the direction we expect gravity to be pointing if the device
      // is right-side-up.
      Eigen::Vector3d canonical_down;
      // Is it more in landscape or in portrait mode?
      if (std::abs(L_z.dot(Eigen::Vector3d::UnitY())) <
          std::abs(L_z.dot(Eigen::Vector3d::UnitX()))) {
        // Landscape
        L_y = Eigen::Vector3d::UnitY();
        canonical_down = Eigen::Vector3d::UnitX();
      } else {
        // Portrait
        L_y = -Eigen::Vector3d::UnitX();
        canonical_down = Eigen::Vector3d::UnitY();
      }
      // Is it right-side-up or upside-down?
      if (L_z.dot(canonical_down) < 0) {
        L_y = -L_y;
      }
      break;
    }
    case OrientationFilterConfiguration::InitialOrientationConfiguration::
        kUseMagToInitOrientation:
      L_y = current_mag_measurement_.sample.normalized();
      L_y -= L_z * L_z.transpose() * L_y;
  }

  L_x = -L_z.cross(L_y);
  if (L_x.norm() == 0.0) {
    return false;
  }

  L_x = L_x / L_x.norm();
  L_y = L_z.cross(L_x);
  if (L_y.norm() == 0.0) {
    return false;
  }

  L_R_G.block<3, 1>(0, 0) = L_x;
  L_R_G.block<3, 1>(0, 1) = L_y;
  L_R_G.block<3, 1>(0, 2) = L_z;

  // Compute and assign the quaternion of orientation from the resulting
  // rotation matrix.
  state_.block<4, 1>(0, 0) =
      geometry_toolbox::RotationMatrixToQuaternion(L_R_G);

  state_from_previous_mag_ = state_;
  mag_is_available_ = false;
  return true;
}

void OrientationFilter::FilterPropagate() {
  // TODO: Orientation filter should be robust to different update
  // frequency.
  const double delta_t = current_gyro_measurement_.timestamp_s -
                         previous_gyro_measurement_.timestamp_s;
  if (!IsTimestampDeltaValid(delta_t)) {
    return;
  }

  // Ignore the current mag measurement if it is too old.
  if (mag_is_available_) {
    const double gyro_time_ahead = current_gyro_measurement_.timestamp_s -
                                   current_mag_measurement_.timestamp_s;

    if (gyro_time_ahead > kMaxTimeDifferentInMagAndGyroInSeconds) {
      // If this behavior happens very often, we should project previous mag
      // measurement onto current timestamp, to do not waste too much data.
      // In practice, in current experiments, this happens at a very low
      // frequency.
      mag_is_available_ = false;
    }
  }

  Eigen::Matrix<double, 6, 1> gyro_measurements;
  gyro_measurements.head<3>() = previous_gyro_measurement_.sample;
  gyro_measurements.tail<3>() = current_gyro_measurement_.sample;
  // Bias compensate the gyro data. Since imu_measurement format is
  // [prev_x, prev_y, prev_z, curr_x, curr_y, curr_z] this can be accomplished
  // by subtracting the bias_x, bias_y, bias_z from the appropriate elements.
  const Eigen::Vector3d rate_correction = ComputeAccelAndMagRateCorrection();

  gyro_measurements.head<3>() +=
      (-state_.tail<3>() +
       (IsInitializing()
            ? config_.attitude_correction_gain_during_initialization
            : config_.attitude_correction_gain) *
           rate_correction);
  gyro_measurements.tail<3>() +=
      (-state_.tail<3>() +
       (IsInitializing()
            ? config_.attitude_correction_gain_during_initialization
            : config_.attitude_correction_gain) *
           rate_correction);

  Eigen::Matrix<double, 4, 1> current_q, next_q;
  current_q = state_.head<4>();
  next_q = state_.head<4>();
  quaternion_integrator_.Integrate(current_q, gyro_measurements, delta_t,
                                   &next_q);
  next_state_.head<4>() = next_q;
  next_state_.tail<3>() =
      state_.tail<3>() -
      ((IsInitializing() ? 0.0 : config_.gyroscope_bias_correction_gain) *
       delta_t * rate_correction);

  // Bias correction if stationary.
  if (IsStationaryBiasCorrectionEnabled()) {
    next_state_.tail<3>() -=
        delta_t *
        stationary_detector_.GetGyroBiasCorrection(
            next_state_.tail<3>(), current_gyro_measurement_.timestamp_s);
  }
  state_ = next_state_;

  if (mag_is_available_) {
    const double mag_delta_t = (current_mag_measurement_.timestamp_s -
                                previous_mag_measurement_.timestamp_s);
    if (!IsStationaryBiasCorrectionEnabled()) {
      // Only apply this correction if the stationary bias correction is not
      // enabled.
      const Eigen::Vector3d mag_correction = EstimateBiasUpdateUsingMag();
      state_.tail<3>() -=
          mag_delta_t *
          config_.magnetometer_gain_for_gyroscope_bias_estimation *
          mag_correction;
    }

    state_from_previous_mag_ = state_;
    mag_is_available_ = false;
  }
}

Eigen::Vector3d OrientationFilter::EstimateBiasUpdateUsingMag() {
  if (!mag_low_pass_filter_.IsInitialized()) {
    return Eigen::Vector3d::Zero();
  }
  // Check that gyro norm is below threshold. This is to ensure we only estimate
  // drift when device is still and gyro signal is most likely due to drift.
  const bool is_gyro_static =
      current_gyro_measurement_.sample.norm() <
      config_.maximum_allowed_gyro_norm_changed_for_mag_bias_correction;

  // Check that mag norm is below threshold. This is to ensure we only estimate
  // drift when device is still and mag signal is similar to noise.
  const bool is_mag_static =
      (previous_mag_measurement_.sample -
       mag_low_pass_filter_.GetFilteredData())
          .norm() < config_.maximum_allowed_magnitude_magnetometer_change_mt;
  mag_low_pass_filter_.SetIsStatic(is_gyro_static && is_mag_static);

  if (!mag_low_pass_filter_.GetIsStaticForN(kMagBiasCorrectionStaticCount)) {
    return Eigen::Vector3d::Zero();
  }

  // Get down direction from both previous mag and current state.
  const Eigen::Vector3d previous_accel_est =
      geometry_toolbox::QuaternionToRotationMatrix(
          state_from_previous_mag_.head<4>())
          .col(2);
  const Eigen::Vector3d previous_mag_est =
      geometry_toolbox::QuaternionToRotationMatrix(
          state_from_previous_mag_.head<4>())
          .col(1);
  const Eigen::Vector3d current_accel_est =
      geometry_toolbox::QuaternionToRotationMatrix(state_.head<4>()).col(2);
  const Eigen::Vector3d current_mag_est =
      geometry_toolbox::QuaternionToRotationMatrix(state_.head<4>()).col(1);

  // Grab the previous mag, remove gravity and normalize.
  Eigen::Vector3d previous_mag_meas = previous_mag_measurement_.sample;
  previous_mag_meas -=
      previous_accel_est * previous_accel_est.transpose() * previous_mag_meas;
  previous_mag_meas.normalize();

  // Grab the current mag, remove gravity and normalize.
  Eigen::Vector3d mag_meas = mag_low_pass_filter_.GetFilteredData();
  mag_meas -= current_accel_est * current_accel_est.transpose() * mag_meas;
  mag_meas.normalize();

  return mag_meas.cross(previous_mag_meas) -
         current_mag_est.cross(previous_mag_est);
}

Eigen::Vector3d OrientationFilter::ComputeAccelAndMagRateCorrection() {
  Eigen::Vector3d accel_meas = current_accel_measurement_.sample;
  const double accel_magnitude = accel_meas.norm();
  if (accel_magnitude < 1e-6) {
    return Eigen::Vector3d::Zero();
  }

  accel_meas.normalize();
  Eigen::Matrix<double, 3, 3> L_R_G_accel_aligned =
      geometry_toolbox::QuaternionToRotationMatrix(state_.head<4>());
  Eigen::Vector3d accel_est = L_R_G_accel_aligned.col(2);

  // Dampen the effect of body acceleration. This is only applied when not
  // initializing because it would otherwise slow down convergence.
  double gain = 1.0;
  const double gyro_norm = current_gyro_measurement_.sample.norm();
  if (!IsInitializing()) {
    // Don't update filter while moving too fast.
    // TODO: Consider using something different than this, since most
    // accelerometer sensors don't report 1G at rest.
    gain /= (1.0 + std::abs(accel_magnitude - kMagnitudeOfGravity));

    // Don't update filter while moving to fast.
    // Use full gain between 0 and 0.1, dampen toward 0 between 0.1 to
    // 0.3 rad.s^(-1).
    // 1 __                  //
    //     \                 //
    //      \                //
    //       \______ 0.0     //
    //  ^  ^  ^              //
    //  0 .1  .3             //
    gain *= std::max(0.0, std::min(1.0, 1.5 - 5.0 * gyro_norm));
  } else {
    // Use full gain between 0 and 0.04, dampen toward 0 between 0.04 to
    // 0.1 rad.s^(-1).
    // 1 __                  //
    //     \                 //
    //      \                //
    //       \______ 0.0     //
    //  ^  ^  ^              //
    //  0 .04  .1            //
    gain *= std::max(0.0, std::min(1.0, 1.5 - 15.0 * gyro_norm));
  }

  Eigen::Vector3d rate_correction_vector = gain / 2 *
                                           config_.accel_yaw_correction_gain *
                                           accel_meas.cross(accel_est);

  // Check if there is a valid mag sample ready to be consumed.
  if (mag_is_available_ && current_mag_sample_fits_calibration_ &&
      config_.mag_yaw_correction_gain > 0 &&
      current_mag_measurement_.timestamp_s > 0) {
    // First check if received a new mag calibration.
    if (new_mag_calibration_available_) {
      // Invalidate the existing mag alignement and force recompute.
      mag_status_ = MagStatus::kAligning;
      mag_meas_for_init_index_ = 0;
      LOG(INFO) << "SensorFusion: Received new bias, estimating alignment.";
      new_mag_calibration_available_ = false;
    }

    const Eigen::Vector3d current_calibrated_mag_measurement =
        current_mag_measurement_.sample - mag_bias_;

    // Compute estimated mag value, projected on yaw and represented locally.
    Eigen::Vector3d mag_est_projection = current_calibrated_mag_measurement;
    mag_est_projection -=
        accel_est * accel_est.transpose() * mag_est_projection;
    mag_est_projection.normalize();

    // Estimate alignment between the filter orientation and magnetic north.
    if (mag_status_ == MagStatus::kAligning) {
      // Store the projected mag in a buffer that will be used by the iterative
      // solver.
      projected_mag_measurement_vector_.col(mag_meas_for_init_index_) =
          L_R_G_accel_aligned.transpose() * mag_est_projection;

      ++mag_meas_for_init_index_;

      // Check if we have enough sample to run the iterative solver.
      if (mag_meas_for_init_index_ ==
          num_mag_measurements_for_yaw_initialization_) {
        if (ComputeYawAlignmentMatrix(projected_mag_measurement_vector_,
                                      &accel_aligned_R_yaw_mag_aligned_)) {
          // In this case, mag is successfully initialized.
          mag_status_ = MagStatus::kAligned;
        } else {
          // Mag initialization fails. Clear mag measurements, and re-start data
          // collection.
          mag_meas_for_init_index_ = 0;
          LOG(INFO) << "SensorFusion: Mag alignment failed in orientation "
                       "tracker. Will retry.";
        }
      }
    }

    Eigen::Vector3d mag_meas = current_calibrated_mag_measurement;
    mag_meas -= accel_meas * accel_meas.transpose() * mag_meas;
    if (mag_meas.norm() < 1e-6) {
      return Eigen::Vector3d();
    }
    mag_meas.normalize();

    if (mag_status_ == MagStatus::kAligned) {
      // Magnetometer is calibrated and aligned with the filter now so we can
      // use it for yaw correction.
      mag_is_available_ = false;

      const Eigen::Vector3d mag_est =
          (L_R_G_accel_aligned * accel_aligned_R_yaw_mag_aligned_).col(1);

      // Test whether the current mag estimate and mag measurement diverge too
      // much.
      const double angle_between_est_and_meas =
          std::acos(mag_est_projection.dot(mag_est));

      if (angle_between_est_and_meas > kMaxAllowedMagDeviationRadians) {
        ++accumulated_num_of_outlier_mag_measurement_;
        if (accumulated_num_of_outlier_mag_measurement_ >
            kNumMagForFilterOutlierRejection) {
          // Invalidate the existing mag alignement and force recompute.
          mag_status_ = MagStatus::kAligning;
          state_from_previous_mag_ = state_;
          mag_meas_for_init_index_ = 0;
          LOG(INFO) << "Consistently received outlier measurements, resetting.";
          BadMagnetometerCalibrationDetectedBroadcast();
          accumulated_num_of_outlier_mag_measurement_ = 0;
        }
      } else {
        rate_correction_vector +=
            config_.mag_yaw_correction_gain * mag_meas.cross(mag_est);

        accumulated_num_of_outlier_mag_measurement_ = 0;
      }
    }
  }

  return rate_correction_vector;
}

void OrientationFilter::Recenter() {
  // This function enforces two constraints on the updated L_R_G_recenter
  // - The gravity direction is kept
  //    L_R_G_recenter * z = L_R_G * z
  // - yaw is equal to zero
  //    L_R_G_recenter^{-1} * -z  =[sin(pitch) 0 cos(pitch)]
  // Notation:
  // L_R_G^{-1} * -z =  [cos(yaw) * sin(pitch) sin(yaw) cos(yaw) * cos(pitch)]

  //
  // We can deduce that
  // L_R_G_recenter(:,3) == L_R_G(:,3) (1)
  // L_R_G_recenter(3,2) = 0.0 (2)
  //
  // As L_R_G is an orthonormal matrix
  // |L_R_G(:,2)| = 1 (3)
  //
  // Combining (2) and (3)
  // |L_R_G(1:2,2)| = 1 (4)
  // L_R_G(1:2,2) . L_R_G(1:2,3) = 0  (5) Orthogonality
  //
  // if L_R_G(1,3) >  Eps
  // L_R_G(2,2) =  +/- sqrt( 1 / ( 1 + (L_R_G(2,3) / L_R_G(1,3))^2)) (6)
  // L_R_G(1,2) = - L_R_G(2,2) * L_R_G(2,3) / L_R_G(1,3)
  //
  // Finally we use the orthogonality constraint for the first column
  // L_R_G(:,1) = L_R_G(:,2) x L_R_G(:,3)

  const Eigen::Matrix3d L_R_G =
      geometry_toolbox::QuaternionToRotationMatrix(state_.head<4>());

  Eigen::Matrix3d L_R_G_recentered;
  // Keep down direction the same (1).
  L_R_G_recentered.block<3, 1>(0, 2) = L_R_G.block<3, 1>(0, 2);

  if (std::abs(L_R_G(0, 2)) < kEpsilon) {
    // Arbitrary deciding to use X axis.
    L_R_G_recentered(0, 1) = 1.0;
    L_R_G_recentered(1, 1) = 0.0;
  } else {
    const double x_y_ratio = L_R_G(1, 2) / L_R_G(0, 2);  // Eq. (6)
    // The sign  is kept positive to enforce "forwardness".
    L_R_G_recentered(1, 1) = sqrt(1.0 / (1 + x_y_ratio * x_y_ratio));
    L_R_G_recentered(0, 1) = -L_R_G_recentered(1, 1) * x_y_ratio;
  }
  L_R_G_recentered(2, 1) = 0.0;  // Eq. (2)

  // Enforce orthogonality.
  L_R_G_recentered.block<3, 1>(0, 0) = L_R_G_recentered.block<3, 1>(0, 1).cross(
      L_R_G_recentered.block<3, 1>(0, 2));

  state_.head<4>() =
      geometry_toolbox::RotationMatrixToQuaternion(L_R_G_recentered);

  // Reset the mag based bias update by setting the previous state to current
  // state. This avoids using the change in pose due to recentering for bias
  // update.
  state_from_previous_mag_ = state_;
}
