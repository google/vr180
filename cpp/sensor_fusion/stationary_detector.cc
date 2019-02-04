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

#include "cpp/sensor_fusion/stationary_detector.h"
#include <glog/logging.h>

namespace mahony_filter {
namespace {
// Helper method to check if timestep between samples is valid.
bool IsTimeDeltaBetweenSamplesValid(double delta_t) { return (delta_t >= 0); }
}  // namespace

StationaryDetector::StationaryDetector(
    const StationaryDetectorConfiguration& config)
    : config_(config),
      accel_low_pass_filter_(config.accel_low_pass_cutoff_frequency),
      gyro_low_pass_filter_(config.gyro_low_pass_cutoff_frequency),
      accel_high_pass_filter_(config.accel_high_pass_cutoff_frequency),
      gyro_high_pass_filter_(config.gyro_high_pass_cutoff_frequency),
      gyro_bias_delayed_low_pass_filter_(
          config.gyro_correction_delay_secs,
          config.init_gyro_correct_low_pass_cutoff_frequency_hz),
      is_last_accel_initialized_(false),
      is_last_gyro_initialized_(false),
      is_stationary_(false),
      is_max_correction_threshold_crossed_(false),
      last_gyro_sample_(Eigen::Vector3d::Zero()),
      last_gyro_timestamp_(0),
      first_gyro_timestamp_(0),
      last_accel_sample_(Eigen::Vector3d::Zero()),
      last_accel_timestamp_(0),
      has_gyro_bias_correction_converged_(false),
      exit_condition_tester_(),
      convergence_condition_tester_(),
      stationary_bias_correction_gain_(config.stationary_bias_correction_gain) {
}

// Adds accelerometer data to the stationary detector.
void StationaryDetector::AddAccelMeasurement(
    const Eigen::Vector3d& accel_sample, double timestamp_s) {
  if (!is_last_accel_initialized_) {
    last_accel_sample_ = accel_sample;
    last_accel_timestamp_ = timestamp_s;
    is_last_accel_initialized_ = true;
  }
  const double delta_t = timestamp_s - last_accel_timestamp_;
  const Eigen::Vector3d delta_accel_sample = accel_sample - last_accel_sample_;

  if (IsTimeDeltaBetweenSamplesValid(delta_t)) {
    accel_low_pass_filter_.AddSampleData(delta_accel_sample, delta_t);
    accel_high_pass_filter_.AddSampleData(accel_sample, delta_t);
  }
  last_accel_timestamp_ = timestamp_s;
  last_accel_sample_ = accel_sample;
}

void StationaryDetector::AddGyroMeasurement(const Eigen::Vector3d& gyro_sample,
                                            double timestamp_s) {
  if (!is_last_gyro_initialized_) {
    last_gyro_sample_ = gyro_sample;
    last_gyro_timestamp_ = timestamp_s;
    first_gyro_timestamp_ = timestamp_s;
    is_last_gyro_initialized_ = true;
  }
  const double delta_t = timestamp_s - last_gyro_timestamp_;
  const Eigen::Vector3d delta_gyro_sample = gyro_sample - last_gyro_sample_;

  if (IsTimeDeltaBetweenSamplesValid(delta_t)) {
    gyro_low_pass_filter_.AddSampleData(delta_gyro_sample, delta_t);
    gyro_high_pass_filter_.AddSampleData(gyro_sample, delta_t);

    if (is_stationary_) {
      gyro_bias_delayed_low_pass_filter_.AddSampleData(gyro_sample, delta_t);
    }
    Update(timestamp_s);
  }
  last_gyro_timestamp_ = timestamp_s;
  last_gyro_sample_ = gyro_sample;
}

Eigen::Vector3d StationaryDetector::GetGyroBiasCorrection(
    const Eigen::Vector3d& current_external_bias, double timestamp_s) {
  Eigen::Vector3d stationary_bias;
  bool bias_is_available =
      gyro_bias_delayed_low_pass_filter_.GetFilteredData(&stationary_bias);

  // Apply no correction if device is not stationary or bias is not available.
  if (!(is_stationary_ && bias_is_available)) {
    return Eigen::Vector3d::Zero();
  }

  const Eigen::Vector3d stationary_correction =
      current_external_bias - stationary_bias;

  // Check if the correction has converged by testing whether it lies in a
  //  specified bound for X seconds.
  if (convergence_condition_tester_.IsStable(
          stationary_correction.norm() <
              config_.max_stationary_gyro_bias_correction,
          timestamp_s, config_.convergence_condition_stable_secs)) {
    has_gyro_bias_correction_converged_ = true;
  }

  // The detector allows larger correction when the detector first enters
  // stationary state. It does not expect bias to change drastically after
  // convergence. The detector exits out of stationary state if the bias
  // correction diverges.
  if (!IsInitializing() && has_gyro_bias_correction_converged_ &&
      stationary_correction.norm() >
          config_.max_stationary_gyro_bias_correction) {
    LOG(INFO) << "SensorFusion: Stat correction threshold crossed: "
              << stationary_correction.norm();
    is_max_correction_threshold_crossed_ = true;
    return Eigen::Vector3d::Zero();
  }

  // Limit the max correction that can be applied.
  if (!IsInitializing()) {
    stationary_correction.cwiseMin(config_.max_stationary_gyro_bias_correction);
    stationary_correction.cwiseMax(
        -config_.max_stationary_gyro_bias_correction);
  }

  const double gain = IsInitializing()
                          ? config_.init_bias_correction_gain_multiplier *
                                stationary_bias_correction_gain_
                          : stationary_bias_correction_gain_;
  return stationary_correction * gain;
}

bool StationaryDetector::ConditionTester::IsStable(bool condition,
                                                   double timestamp_s,
                                                   double number_of_secs) {
  if (condition) {
    ++n_static_;
    if (n_static_ == 1) {
      static_start_timestamp_s_ = timestamp_s;
    }
    return (timestamp_s - static_start_timestamp_s_) > number_of_secs;
  } else {
    Reset();
    return false;
  }
}

// Update the state of the detector.
void StationaryDetector::Update(double timestamp_s) {
  // Do not update if any filters have not been initialized.
  if (!(gyro_low_pass_filter_.HasSettled() &&
        accel_low_pass_filter_.HasSettled() &&
        gyro_high_pass_filter_.IsInitialized() &&
        accel_high_pass_filter_.IsInitialized())) {
    return;
  }

  // Condition for exiting the stationary state.
  const bool exit_condition =
      std::abs(accel_high_pass_filter_.GetFilteredDataNorm()) >
          config_.accel_high_pass_threshold ||
      std::abs(gyro_high_pass_filter_.GetFilteredDataNorm()) >
          config_.gyro_high_pass_threshold ||
      last_gyro_sample_.norm() > config_.gyro_norm_threshold_rad_per_sec ||
      is_max_correction_threshold_crossed_;

  // Main condition for entering the stationary state.
  const bool entry_condition =
      std::abs(accel_low_pass_filter_.GetFilteredDataNorm()) <
          config_.accel_low_pass_threshold &&
      std::abs(gyro_low_pass_filter_.GetFilteredDataNorm()) <
          config_.gyro_low_pass_threshold;

  // Stability condition ensuring that we wait X seconds before entering the
  // stationary state after the exit condition is true.
  const double stability_secs = IsInitializing()
                                    ? config_.init_no_exit_condition_stable_secs
                                    : config_.no_exit_condition_stable_secs;
  const bool no_exit_stable_condition = exit_condition_tester_.IsStable(
      !exit_condition, timestamp_s, stability_secs);

  // State machine update.
  if (is_stationary_) {
    if (exit_condition) {
      LOG(INFO) << "SensorFusion: Exit stationary state:"
                << " Gyro HP:"
                << (std::abs(accel_high_pass_filter_.GetFilteredDataNorm()) >
                    config_.accel_high_pass_threshold)
                << " Acc HP: "
                << (std::abs(gyro_high_pass_filter_.GetFilteredDataNorm()) >
                    config_.gyro_high_pass_threshold)
                << " Gyro Norm: "
                << (last_gyro_sample_.norm() >
                    config_.gyro_norm_threshold_rad_per_sec);
      // Reset everything.
      Reset();
    }
  } else if (entry_condition && no_exit_stable_condition) {
    LOG(INFO) << "SensorFusion: Enter stationary state. Stability time: "
              << stability_secs;
    is_stationary_ = true;
  }
}

void StationaryDetector::Reset() {
  // Set stationary to false.
  is_stationary_ = false;
  // Reset the threshold crossed condition.
  is_max_correction_threshold_crossed_ = false;
  // Reset the has converged flag.
  has_gyro_bias_correction_converged_ = false;
  // Reset the bias low pass filter.
  gyro_bias_delayed_low_pass_filter_.Reset();
  if (!IsInitializing()) {
    gyro_bias_delayed_low_pass_filter_.SetCutoffFrequency(
        config_.gyro_correct_low_pass_cutoff_frequency);
  }
  // Reset all condition testers.
  convergence_condition_tester_.Reset();
  exit_condition_tester_.Reset();
}

bool StationaryDetector::IsInitializing() const {
  return (last_gyro_timestamp_ - first_gyro_timestamp_) <
         config_.initialization_period;
}

}  // namespace mahony_filter
