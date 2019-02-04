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

#ifndef VR180_CPP_SENSOR_FUSION_DELAYED_LOW_PASS_FILTER_H_
#define VR180_CPP_SENSOR_FUSION_DELAYED_LOW_PASS_FILTER_H_

#include <algorithm>
#include <queue>

#include <Eigen/Core>
#include "cpp/sensor_fusion/low_pass_filter.h"

namespace mahony_filter {

// Low pass filter that includes a buffer so that it can operate on delayed
// data.
class DelayedLowPassFilter {
 public:
  DelayedLowPassFilter(double delay_time_s, double low_pass_cutoff_frequency)
      : delay_time_s_(delay_time_s),
        buffer_accumulated_time_s_(0),
        low_pass_filter_(low_pass_cutoff_frequency) {}

  // Add samples to the buffer.
  void AddSampleData(const Eigen::Vector3d& value, double delta_time_s) {
    if (buffer_accumulated_time_s_ >= delay_time_s_) {
      Sample sample = delay_buffer_.front();
      low_pass_filter_.AddSampleData(sample.value, sample.delta_time_s);
      // Remove the value from buffer.
      delay_buffer_.pop();
      buffer_accumulated_time_s_ -= sample.delta_time_s;
    }
    buffer_accumulated_time_s_ += delta_time_s;
    delay_buffer_.push(Sample(value, delta_time_s));
  }

  // Get values from the buffer.
  // @param value_ptr Pointer to variable to store popped value.
  // @return bool Whether a valid value is available. True, if the buffer is
  // full and the low pass filter has settled.
  bool GetFilteredData(Eigen::Vector3d* value_ptr) const {
    if (buffer_accumulated_time_s_ < delay_time_s_ ||
        !low_pass_filter_.HasSettled()) {
      return false;
    }
    *value_ptr = low_pass_filter_.GetFilteredData();
    return true;
  }

  // Reset the buffer and low pass filter.
  void Reset() {
    std::queue<Sample> empty;
    std::swap(delay_buffer_, empty);
    buffer_accumulated_time_s_ = 0;
    low_pass_filter_.Reset();
  }

  // Set the cutoff frequency of the filter.
  void SetCutoffFrequency(double cutoff_frequency) {
    Reset();
    low_pass_filter_.SetCutoffFrequency(cutoff_frequency);
  }

  double GetBufferAccumulatedTime() const { return buffer_accumulated_time_s_; }

  struct Sample {
    Sample(const Eigen::Vector3d& _value, double _delta_time_s)
        : value(_value), delta_time_s(_delta_time_s) {}

    Eigen::Vector3d value;
    double delta_time_s;
  };

  const std::queue<Sample>& GetDelayBuffer() const { return delay_buffer_; }

  EIGEN_MAKE_ALIGNED_OPERATOR_NEW

 private:
  // Number of seconds of delay.
  double delay_time_s_;

  // Number of seconds of buffer accumulated.
  double buffer_accumulated_time_s_;

  // Buffer to store the delayed values.
  std::queue<Sample> delay_buffer_;

  // Low pass filter.
  LowPassFilter low_pass_filter_;
};

}  // namespace mahony_filter

#endif  // VR180_CPP_SENSOR_FUSION_DELAYED_LOW_PASS_FILTER_H_
        // //NOLINT
