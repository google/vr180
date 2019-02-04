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

#ifndef VR180_CPP_SENSOR_FUSION_STATIONARY_DETECTOR_H_
#define VR180_CPP_SENSOR_FUSION_STATIONARY_DETECTOR_H_

#include <Eigen/Core>
#include "cpp/sensor_fusion/delayed_low_pass_filter.h"
#include "cpp/sensor_fusion/geometry_toolbox_mahony.h"
#include "cpp/sensor_fusion/high_pass_filter.h"
#include "cpp/sensor_fusion/low_pass_filter.h"

namespace mahony_filter {

// Stationary detector estimates if the device is stationary and provides an
// estimate of the gyro bias correction. This class is not thread-safe.
class StationaryDetector {
 public:
  // Contains all parameters of the stationary detector.
  struct StationaryDetectorConfiguration {
    // Cutoff frequency for accel low pass filter. Used to determine entry into
    // stationary state.
    double accel_low_pass_cutoff_frequency = 1.0;
    // Cutoff frequency for gyro low pass filter. Used to determine entry into
    // stationary state.
    double gyro_low_pass_cutoff_frequency = 1.0;
    // Cutoff frequency for accel high pass filter. Used to determine exit from
    // stationary state.
    double accel_high_pass_cutoff_frequency = 1.0;
    // Cutoff frequency for gyro high pass filter. Used to determine exit from
    // stationary state.
    double gyro_high_pass_cutoff_frequency = 1.0;

    // Gyro norm threshold for exiting stationary state.
    double gyro_norm_threshold_rad_per_sec = 0.15;

    // Thresholds to determine exit from stationary state.
    double accel_high_pass_threshold = 0.15;
    double gyro_high_pass_threshold = 0.02;

    // Thresholds to determine entry into stationary state.
    double accel_low_pass_threshold = 0.0025;
    double gyro_low_pass_threshold = 0.001;

    // Maximum correction in rad/sec that the detector can apply after
    // convergence. If the correction is larger than this the detector exits
    // stationary state.
    double max_stationary_gyro_bias_correction = 0.0015;

    // The low pass filter cutoff frequency for computing the gyro bias in the
    // stationary state.
    double gyro_correct_low_pass_cutoff_frequency = 0.05;

    // The low pass filter cutoff frequency for computing the gyro bias in the
    // stationary state at initialization.
    double init_gyro_correct_low_pass_cutoff_frequency_hz = 0.5;

    // Number of seconds to wait after exit condition is true before entering
    // stationary state.
    double no_exit_condition_stable_secs = 10.0;

    // Number of seconds to wait after exit condition is true before entering
    // stationary state during initialization.
    double init_no_exit_condition_stable_secs = 1.0;

    // Number of seconds to ensure correction is within threshold for testing
    // convergence.
    double convergence_condition_stable_secs = 0.1;

    // Number of seconds to delay gyro for calculating correction.
    double gyro_correction_delay_secs = 1.0;

    // The number of seconds in initialization mode.
    double initialization_period = 7.0;

    // The multiplier for stationary bias correction gain during initialization
    // period.
    double init_bias_correction_gain_multiplier = 10.0;

    // Controls the gain applied to the correction of the gyroscope bias.
    double stationary_bias_correction_gain = 0.0;
  };

  explicit StationaryDetector(const StationaryDetectorConfiguration& config);

  // Adds accelerometer data to the stationary detector.
  void AddAccelMeasurement(const Eigen::Vector3d& accel_sample,
                           double timestamp_s);

  // Adds gyroscope data to the stationary detector. The detector updates state
  // on gyroscope data.
  void AddGyroMeasurement(const Eigen::Vector3d& gyro_sample,
                          double timestamp_s);

  // Returns whether the device is stationary.
  bool IsStationary() const { return is_stationary_; }

  // Computes the bias correction provided by the stationary detector.
  // @param current_external_bias The previous bias in the filter.
  // @param timestamp_s The timestamp in seconds of the current bias.
  // @return correction The difference between bias computed by the stationary
  // detector and the external bias. The correction is 0 if the device is not
  // stationary. Is typically used in the integral bias estimation feedback loop
  // of the mahony filter.
  Eigen::Vector3d GetGyroBiasCorrection(
      const Eigen::Vector3d& current_external_bias, double timestamp_s);

  // Reset the stationary detector.
  void Reset();

  // Is the detector undergoing initialization. The detector is more aggressive
  // during initilization to enable faster convergence of bias when user is
  // stationary during controller pairing screens.
  bool IsInitializing() const;

  EIGEN_MAKE_ALIGNED_OPERATOR_NEW

 private:
  // Helper class for testing stability of a condition.
  class ConditionTester {
   public:
    ConditionTester() : n_static_(0), static_start_timestamp_s_(0) {}

    // Checks is condition is true for a specified time period.
    bool IsStable(bool condition, double timestamp_s, double number_of_secs);

    // Reset the condition tester.
    void Reset() {
      n_static_ = 0;
      static_start_timestamp_s_ = 0;
    }

   private:
    // Number of runs a true condition is static.
    double n_static_;
    // The starting timestamp when a condition becomes true.
    double static_start_timestamp_s_;
  };

  // Update the state of the detector.
  void Update(double timestamp_s);

  // Current stationary detector default configuration.
  StationaryDetectorConfiguration config_;

  // Filters that provide the signal for the entry and exit criterion of the
  // stationary detector.
  mahony_filter::LowPassFilter accel_low_pass_filter_;
  mahony_filter::LowPassFilter gyro_low_pass_filter_;
  mahony_filter::HighPassFilter accel_high_pass_filter_;
  mahony_filter::HighPassFilter gyro_high_pass_filter_;

  // Filter that computes bias when stationary.
  mahony_filter::DelayedLowPassFilter gyro_bias_delayed_low_pass_filter_;

  // Is the accel timestamp initialized.
  bool is_last_accel_initialized_;
  // Is the gyro timestamp initialized.
  bool is_last_gyro_initialized_;

  // State of the detector.
  bool is_stationary_;
  // Is the max correction threshold crossed. If true, the detector is reset in
  // the next update.
  bool is_max_correction_threshold_crossed_;

  // Track the previous accel and gyro samples.
  Eigen::Vector3d last_gyro_sample_;
  double last_gyro_timestamp_;
  double first_gyro_timestamp_;
  Eigen::Vector3d last_accel_sample_;
  double last_accel_timestamp_;

  // Has the bias estimator converged.
  bool has_gyro_bias_correction_converged_;

  // Tests the stability of the exit condition.
  ConditionTester exit_condition_tester_;

  // Tests the stability of the entry condition.
  ConditionTester convergence_condition_tester_;

  // The stationary bias correction gain.
  double stationary_bias_correction_gain_;
};

}  // namespace mahony_filter
#endif  // VR180_CPP_SENSOR_FUSION_STATIONARY_DETECTOR_H_
