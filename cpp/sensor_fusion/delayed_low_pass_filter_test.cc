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

#include "gtest/gtest.h"

#include <Eigen/Core>
#include "cpp/sensor_fusion/delayed_low_pass_filter.h"

namespace mahony_filter {

TEST(DelayedLowPassFilterTest, CreateFilter) {
  DelayedLowPassFilter filter(1., 1.);
  EXPECT_EQ(filter.GetBufferAccumulatedTime(), 0.);
  EXPECT_TRUE(filter.GetDelayBuffer().empty());
}

TEST(DelayedLowPassFilterTest, AddSampleDataToNewFilter) {
  DelayedLowPassFilter filter(10., 1.);
  const Eigen::Vector3d sample(1., 2., 3.);
  filter.AddSampleData(sample, 1.);
  EXPECT_EQ(filter.GetBufferAccumulatedTime(), 1.);
  EXPECT_EQ(filter.GetDelayBuffer().size(), 1);
  EXPECT_EQ(filter.GetDelayBuffer().front().value, sample);
}

// Test the first sample of delay_buffer is popped if the delay_buffer is full
// and receives a new sample.
TEST(DelayedLowPassFilterTest, AddSampleDataToFullFilter) {
  DelayedLowPassFilter filter(10., 1.);
  const Eigen::Vector3d sample1(1., 2., 3.), sample2(4., 5., 6.),
      sample3(7., 8., 9.), sample4(10., 11., 12.);
  filter.AddSampleData(sample1, 5.);
  filter.AddSampleData(sample2, 4.);
  filter.AddSampleData(sample3, 3.);
  EXPECT_EQ(filter.GetDelayBuffer().size(), 3);
  EXPECT_EQ(filter.GetDelayBuffer().front().value, sample1);
  EXPECT_EQ(filter.GetBufferAccumulatedTime(), 12.);

  filter.AddSampleData(sample4, 2.);
  EXPECT_EQ(filter.GetDelayBuffer().size(), 3);
  EXPECT_EQ(filter.GetDelayBuffer().front().value, sample2);
  EXPECT_EQ(filter.GetBufferAccumulatedTime(), 9.);
}

TEST(DelayedLowPassFilterTest, GetFilteredData) {
  Eigen::Vector3d filtered_sample;
  DelayedLowPassFilter filter(10., 1.);
  EXPECT_FALSE(filter.GetFilteredData(&filtered_sample));

  const Eigen::Vector3d sample(1., 2., 3.);
  for (int i = 0; i < 100; ++i) {
    filter.AddSampleData(sample, 0.1);
  }
  EXPECT_FALSE(filter.GetFilteredData(&filtered_sample));

  for (int i = 0; i < 100; i++) {
    filter.AddSampleData(sample, 0.1);
  }
  EXPECT_TRUE(filter.GetFilteredData(&filtered_sample));
  EXPECT_EQ(sample, filtered_sample);
}

TEST(DelayedLowPassFilterTest, ResetFilter) {
  DelayedLowPassFilter filter(10., 1.);
  const Eigen::Vector3d sample(1., 2., 3.);
  filter.AddSampleData(sample, 1.);
  filter.Reset();
  EXPECT_EQ(filter.GetBufferAccumulatedTime(), 0.);
  EXPECT_TRUE(filter.GetDelayBuffer().empty());
}

}  // namespace mahony_filter
