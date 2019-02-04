/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef VR180_CPP_SENSOR_FUSION_ORIENTATION_FILTER_H_
#define VR180_CPP_SENSOR_FUSION_ORIENTATION_FILTER_H_

#include <queue>
#include <set>

#include <Eigen/Core>
#include "cpp/sensor_fusion/quaternion_integrator.h"
#include "cpp/sensor_fusion/stationary_detector.h"

class OrientationFilter {
 public:
  // Contains all parameters of the filter.
  struct OrientationFilterConfiguration {
    // Configuration flag to decide whether to use the magnetometer to
    // initialize the coordinates system.
    enum InitialOrientationConfiguration {
      kUseMagToInitOrientation,
      kDontUseMagToInitOrientation
    };
    // Controls the gravity estimation feedback. A high value increases the
    // influence of the gravity estimation on the orientation.
    double attitude_correction_gain = 2.0;
    // Controls the bias estimation feedback. A high value decreases the time to
    // adopt to gyroscope bias but can result in a tilting horizon.
    double gyroscope_bias_correction_gain = .1;
    // Control the yaw correction feedback for the magnetometer. By default this
    // is not active.
    double mag_yaw_correction_gain = 0.0;
    // Control the yaw correction feedback for the accelerometer.
    double accel_yaw_correction_gain = 1.0;
    // Controls the gain applied to the correction of the gyroscope bias
    // estimated from magnetometer.
    double magnetometer_gain_for_gyroscope_bias_estimation = 1.0;
    // Controls the gain applied to the correction of the gyroscope bias
    // estimated from stationary detector.
    double stationary_bias_correction_gain = 0.0;
    // Specific gain used to controls the gravity feedback from accel. This is
    // usually used to help the filter converge faster.
    double attitude_correction_gain_during_initialization = 15.0;
    // Time period during initialization in which
    // attitude_correction_gain_during_initialization is used instead of
    // attitude_correction_gain.
    double initialization_period_s = 1.0;
    // Maximum allowed change in magnitude of magnetometer for it to be used in
    // bias estimation (in micro Tesla)
    double maximum_allowed_magnitude_magnetometer_change_mt = 2.0;
    // Cutoff frequency for the magnetometer low pass filter.
    double magnetometer_low_pass_cutoff_frequency = 1.0;
    // Maximum change in gyro norm to apply mag bias correction.
    double maximum_allowed_gyro_norm_changed_for_mag_bias_correction = 0.1;
    // Flag to decide whether to use the mag when setting up
    //  the coordinate system.
    InitialOrientationConfiguration init_config =
        InitialOrientationConfiguration::kDontUseMagToInitOrientation;
    // Initial magnetometer bias (x, y, z) in micro Tesla.
    Eigen::Vector3d init_mag_bias = Eigen::Vector3d::Zero();
  };

  // Status of Magnetometer alignment.
  enum class MagStatus { kInitial, kAligning, kAligned };

  // OrientationFilter constructor.
  // @param config set of parameters for the filter to be created.
  explicit OrientationFilter(const OrientationFilterConfiguration& config);

  // Registers a callback that will be called every time a bad calibration
  // is detected.
  // OrientationFilter does not manage lifecycle of the pointer.
  //
  // @param on_bad_mag_calibration_detected_callback: function that should
  // be called on every time a bad calibration is detected.
  void RegisterOnBadMagnetometerCalibrationDetectedCallback(
      std::function<void()>* on_bad_mag_calibration_detected_callback);

  // Unregisters on_bad_mag_calibration_detected_callback from the list
  // of callbacks.

  // @param on_bad_mag_calibration_detected_callback function to be
  // unregistered from callback list.
  void UnRegisterOnBadMagnetometerCalibrationDetectedCallback(
      std::function<void()>* on_bad_mag_calibration_detected_callback);

  void Run();

  void AddAccelMeasurement(const Eigen::Vector3d& sample, double timestamp_s);
  void AddGyroMeasurement(const Eigen::Vector3d& sample, double timestamp_s);

  // Add a magnetometer measurement.
  // @param sample a magnetometer sample x, y, z
  // @param timestamp_s timestamp in seconds
  // @param fits_calibration True if the sample fits the current calibration.
  // False if the sample is an outlier or there is no calibration yet.
  void AddMagMeasurement(const Eigen::Vector3d& sample, double timestamp_s,
                         bool fits_calibration = false);

  Eigen::Matrix<double, 4, 1> GetOrientation() {
    return state_.block<4, 1>(0, 0);
  }
  Eigen::Vector3d GetRotationalVelocity() {
    return current_gyro_measurement_.sample - GetGyroBias();
  }

  Eigen::Vector3d GetGyroBias() { return state_.block<3, 1>(4, 0); }

  // Sets the pose in the orientation filter and re-initializes the tracker.
  // @param orientation quaternion to set orientation in JPL.
  void SetOrientation(const Eigen::Matrix<double, 4, 1>& orientation) {
    state_.block<4, 1>(0, 0) = orientation;
    is_orientation_initialized_ = true;
    first_accel_timestamp_s_ = 0.0;
  }

  void SetGyroBias(const Eigen::Vector3d& gyro_bias) {
    state_.block<3, 1>(4, 0) = gyro_bias;
  }

  // TODO: Create a function to invalidate current bias.
  void SetMagBias(const Eigen::Vector3d& mag_bias) {
    mag_bias_ = mag_bias;
    new_mag_calibration_available_ = true;
    state_from_previous_mag_ = state_;
  }

  const Eigen::Vector3d GetMagBias() const { return mag_bias_; }

  void SetLastGyroscopeMeasurement(const Eigen::Vector3d& gyro_sample,
                                   double timestamp_s) {
    previous_gyro_measurement_ = SensorSample(gyro_sample, timestamp_s);
  }

  // This method should only be called when tracker is paused.
  Eigen::Vector3d GetLastGyroscopeSample() const {
    return current_gyro_measurement_.sample;
  }

  bool IsLastMagSampleFitCalibration() const {
    return current_mag_sample_fits_calibration_;
  }

  // This method should only be called when tracker is paused.
  double GetLastGyroscopeTimestamp() const {
    return current_gyro_measurement_.timestamp_s;
  }

  // @return true if a first orientation was computed. Note that a
  // valid accelerometer and magnetometer measurement are required for
  // the computation to happen.
  bool IsOrientationSet() const { return is_orientation_initialized_; }

  // Return whether the initialization phase is finished or not.
  bool IsFullyInitialized() const {
    return IsOrientationSet() && (!IsInitializing());
  }

  // Computes gravity estimate from current pose.
  Eigen::Vector3d ComputeGravityEstimate() const {
    return geometry_toolbox::QuaternionToRotationMatrix(state_.head<4>())
        .block<3, 1>(0, 2);
  }

  // Resets yaw angle to zero while keeping pitch and roll the identical.
  void Recenter();

  EIGEN_MAKE_ALIGNED_OPERATOR_NEW;

 private:
  struct SensorSample {
    SensorSample() : sample(Eigen::Vector3d::Zero()), timestamp_s() {}

    SensorSample(const Eigen::Vector3d& _sample, double _timestamp_s)
        : sample(_sample), timestamp_s(_timestamp_s) {}

    Eigen::Vector3d sample;
    double timestamp_s;
  };

  bool OrientationFromAccelAndMag();
  void FilterPropagate();
  bool ProcessAccelMeasurement();
  bool ProcessGyroMeasurement();
  bool ProcessMagMeasurement();

  // Computes correction using available mag and accel.
  Eigen::Vector3d ComputeAccelAndMagRateCorrection();

  // Estimates an update to be applied to the current gyro bias based
  // on the mag data.
  Eigen::Vector3d EstimateBiasUpdateUsingMag();

  // Returns true if the orientation was set and the duration between current
  // time and first sample is under the initialization period.
  bool IsInitializing() const {
    return IsOrientationSet() &&
           (current_accel_measurement_.timestamp_s - first_accel_timestamp_s_ <
            config_.initialization_period_s);
  }

  // Refines the north angle based on using multiple magnetometer samples.
  bool ComputeIterativeSolution(
      const Eigen::VectorXd& mag_vector, const double initial_solution,
      Eigen::Matrix3d* accel_aligned_R_yaw_mag_aligned);

  // Computes the magnetic north location from the magnetometer sample and the
  // orientation estimate.
  bool ComputeYawAlignmentMatrix(
      const Eigen::Matrix3Xd& mag_proj,
      Eigen::Matrix3d* accel_aligned_R_yaw_mag_aligned);

  // Broadcasts the event that a bad calibration is detected to the registered
  // listeners.
  void BadMagnetometerCalibrationDetectedBroadcast() const;

  // Helper method for checking if stationary bias correction is enabled. When
  // stationary bias correction is enabled uncalibrated mag bias
  // estimation is not used.
  bool IsStationaryBiasCorrectionEnabled() const {
    return config_.stationary_bias_correction_gain > 0.0;
  }

  // Current mahony configuration.
  const OrientationFilterConfiguration config_;

  // Integrator for processing the gyro.
  QuaternionIntegrator quaternion_integrator_;

  // State and covariance for the filter.
  // state_[0...3] quaternion world to sensor
  // state_[4..6] gyroscope bias in rad.s-1
  Eigen::Matrix<double, 7, 1> state_;
  Eigen::Matrix<double, 7, 1> next_state_;
  Eigen::Matrix<double, 7, 1> state_from_previous_mag_;

  // It's useful to also reference the previous measurement, this should be done
  // by using a different storage container.
  SensorSample current_accel_measurement_;
  SensorSample current_gyro_measurement_;
  SensorSample current_mag_measurement_;
  SensorSample previous_mag_measurement_;
  SensorSample previous_gyro_measurement_;

  // Was a pose computed from the first accelerometer and magnetometer sample.
  bool is_orientation_initialized_;

  // Timestamp in seconds of the first accelerometer measurement.
  double first_accel_timestamp_s_;
  // Has a gyroscope sample be received yet.
  bool has_received_gyro_sample_;
  // Has a new mag measurement been received.
  bool mag_is_available_;

  // Registered Callbacks of event when a bad calibration is detected.
  std::set<std::function<void()>*> on_bad_mag_calibration_detected_callbacks_;

  // Low pass filters for the mag data.
  mahony_filter::LowPassFilter mag_low_pass_filter_;

  // Data and variables used for initializing mag measurement, in case mag
  // measurements are not available in the beginning of the estimation. This is
  // possible in case we start the sensor fusion with uncalibrated mag. The
  // first step is to perform online mag calibration, once it is calibrated,
  // we then begin to use mag measurement in the orientation estimator.

  // Rotation about yaw between mag aligned global frame (local East-North-Up)
  // and accel aligned frame (a gravity aligned frame generated during filter
  // initialization).
  Eigen::Matrix3d accel_aligned_R_yaw_mag_aligned_;

  // Number of mag measurements used for mag initialization.
  int num_mag_measurements_for_yaw_initialization_;

  // Currect collected mag measurement for initialization.
  int mag_meas_for_init_index_;

  // Data buffer for mag data initialization.
  Eigen::Matrix3Xd projected_mag_measurement_vector_;

  // Current magnetometer bias.
  Eigen::Vector3d mag_bias_;

  // Has a new magnetometer bias been received?
  bool new_mag_calibration_available_;

  // Flag indicating whether we have a valid aligned mag measurement.
  MagStatus mag_status_;

  // Current number of invalid mag measurement, after mag data is initialized
  // and used in the filter.
  int accumulated_num_of_outlier_mag_measurement_;

  // Whether the current mag sample fits the calibration set as mag bias.
  bool current_mag_sample_fits_calibration_;

  // Detector for estimating when device is stationary.
  mahony_filter::StationaryDetector stationary_detector_;
};

#endif  // VR180_CPP_SENSOR_FUSION_ORIENTATION_FILTER_H_
