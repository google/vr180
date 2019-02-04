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

#ifndef VR180_CPP_SENSOR_FUSION_HIGH_PASS_FILTER_H_
#define VR180_CPP_SENSOR_FUSION_HIGH_PASS_FILTER_H_

#include <Eigen/Core>
#include "cpp/sensor_fusion/geometry_toolbox_mahony.h"

namespace mahony_filter {

// A high pass filter for filtering Vector3d sensor data.
class HighPassFilter {
 public:
  explicit HighPassFilter(double cutoff_frequency)
      : cutoff_frequency_(cutoff_frequency), is_initialized_(false) {
    time_constant_ = 1. / (2. * M_PI * cutoff_frequency_);
    last_data_ = Eigen::Vector3d::Zero();
    filtered_data_ = Eigen::Vector3d::Zero();
  }

  Eigen::Matrix<double, 3, 1> GetFilteredData() const { return filtered_data_; }

  Eigen::Matrix<double, 3, 1> GetFilteredDataDirection() const {
    return filtered_data_ / filtered_data_.norm();
  }

  double GetFilteredDataNorm() const { return filtered_data_.norm(); }

  Eigen::Matrix<double, 3, 1> GetLastData() const { return last_data_; }

  void AddSampleData(const Eigen::Matrix<double, 3, 1>& sample_data,
                     const double delta_t) {
    if (!is_initialized_) {
      filtered_data_ = sample_data;
      is_initialized_ = true;
      return;
    }

    double alpha = delta_t / (time_constant_ + delta_t);
    filtered_data_ = alpha * (sample_data - last_data_ + filtered_data_);
    last_data_ = sample_data;
  }

  // Is the high-pass filter initialized.
  bool IsInitialized() const { return is_initialized_; }

  EIGEN_MAKE_ALIGNED_OPERATOR_NEW

 private:
  double cutoff_frequency_;
  bool is_initialized_;
  double time_constant_;
  Eigen::Matrix<double, 3, 1> filtered_data_;
  Eigen::Matrix<double, 3, 1> last_data_;
};

}  // namespace mahony_filter
#endif  // VR180_CPP_SENSOR_FUSION_HIGH_PASS_FILTER_H_
