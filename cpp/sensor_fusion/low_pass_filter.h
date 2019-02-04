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

#ifndef VR180_CPP_SENSOR_FUSION_LOW_PASS_FILTER_H_
#define VR180_CPP_SENSOR_FUSION_LOW_PASS_FILTER_H_

#include <Eigen/Core>
#include "cpp/sensor_fusion/geometry_toolbox_mahony.h"

namespace mahony_filter {

// This number of contiguous static samples is required to confirm there is no
// motion.
constexpr int kContiguousStaticSamples = 11;

// This class filters out sigals which have frequency higher than cutoff
// frequency. It will filter out noise and still respond to motion.
class LowPassFilter {
 public:
  explicit LowPassFilter(double cutoff_frequency)
      : cutoff_frequency_(cutoff_frequency),
        is_initialized_(false),
        n_samples_(0),
        n_static_(0),
        startup_time_s_(0),
        run_time_s_(0) {
    time_constant_ = 1. / (2. * M_PI * cutoff_frequency_);
    last_data_ = Eigen::Vector3d::Zero();
    filtered_data_ = Eigen::Vector3d::Zero();
    startup_time_s_ = 1. / cutoff_frequency_;
  }

  Eigen::Matrix<double, 3, 1> GetFilteredData() const { return filtered_data_; }

  Eigen::Matrix<double, 3, 1> GetFilteredDataDirection() const {
    return filtered_data_ / filtered_data_.norm();
  }

  double GetFilteredDataNorm() const { return filtered_data_.norm(); }

  Eigen::Matrix<double, 3, 1> GetLastData() const { return last_data_; }

  Eigen::Matrix<double, 3, 1> GetLastDataDirection() const {
    return last_data_ / last_data_.norm();
  }

  void AddSampleData(const Eigen::Matrix<double, 3, 1>& sample_data,
                     double delta_t) {
    if (!is_initialized_) {
      filtered_data_ = sample_data;
      is_initialized_ = true;
      return;
    }

    run_time_s_ += delta_t;

    double alpha = delta_t / (time_constant_ + delta_t);
    filtered_data_ = alpha * sample_data + (1. - alpha) * filtered_data_;
    ++n_samples_;
    last_data_ = sample_data;
  }

  // Returns if enough time has passed for the low pass filter data to be valid.
  bool HasSettled() const {
    return is_initialized_ && (run_time_s_ > startup_time_s_);
  }

  bool IsInitialized() const { return is_initialized_; }

  // Records that whether the last sample was static.
  void SetIsStatic(bool is_static) {
    if (is_static) {
      ++n_static_;
    } else {
      n_static_ = 0;
    }
  }

  bool GetIsStatic() const { return GetIsStaticForN(kContiguousStaticSamples); }

  // Returns true if all of the previous 'n' samples were static, as indicated
  // by calls to SetIsStatic().
  bool GetIsStaticForN(int number_of_runs) const {
    return n_static_ >= number_of_runs;
  }

  // Returns the number of contiguous previous samples that have been static.
  // This resets to zero when a non-static run occurs.
  int GetNStatic() const { return n_static_; }

  double GetRunTime() const { return run_time_s_; }

  // Resets the filter.
  void Reset() {
    is_initialized_ = false;
    last_data_ = Eigen::Vector3d::Zero();
    filtered_data_ = Eigen::Vector3d::Zero();
    n_samples_ = 0;
    n_static_ = 0;
    run_time_s_ = 0;
  }

  // Set the cutoff frequency of the filter.
  void SetCutoffFrequency(double cutoff_frequency) {
    Reset();
    cutoff_frequency_ = cutoff_frequency;
    startup_time_s_ = 1.0 / cutoff_frequency_;
  }

  EIGEN_MAKE_ALIGNED_OPERATOR_NEW

 private:
  double cutoff_frequency_;
  bool is_initialized_;
  int n_samples_;
  int n_static_;
  double time_constant_;
  double startup_time_s_;
  double run_time_s_;
  Eigen::Matrix<double, 3, 1> filtered_data_;
  Eigen::Matrix<double, 3, 1> last_data_;
};

}  // namespace mahony_filter
#endif  // VR180_CPP_SENSOR_FUSION_LOW_PASS_FILTER_H_
