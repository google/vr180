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
#include "cpp/sensor_fusion/low_pass_filter.h"

namespace mahony_filter {

TEST(LowPassFilterTest, CreateFilter) {
  LowPassFilter filter(1.0);
  EXPECT_FALSE(filter.IsInitialized());
  EXPECT_FALSE(filter.GetIsStatic());
  EXPECT_EQ(filter.GetNStatic(), 0);
  EXPECT_EQ(filter.GetRunTime(), 0.0);
}

TEST(LowPassFilterTest, AddSampleDataToNewFilter) {
  LowPassFilter filter(1.0);
  Eigen::Vector3d sample(1.0, 0.0, 0.0);
  filter.AddSampleData(sample, 1.0);

  EXPECT_EQ(filter.GetFilteredData(), sample);
  EXPECT_EQ(filter.GetFilteredDataNorm(), 1.0);
  EXPECT_EQ(filter.GetFilteredDataDirection(), sample);
  EXPECT_NE(filter.GetLastData(), sample);
  EXPECT_EQ(filter.GetRunTime(), 0.0)
      << "The first sample shouldn't change run_time_s_.";

  // Adds another sample
  filter.AddSampleData(Eigen::Vector3d(3.0, 4.0, 0.0), 0.5);
  EXPECT_EQ(filter.GetRunTime(), 0.5);
  EXPECT_EQ(filter.GetLastData(), Eigen::Vector3d(3.0, 4.0, 0.0));
  EXPECT_EQ(filter.GetLastDataDirection(), Eigen::Vector3d(0.6, 0.8, 0.0));
}

TEST(LowPassFilterTest, CheckStatic) {
  LowPassFilter filter(1.0);
  filter.SetIsStatic(true);
  EXPECT_EQ(filter.GetNStatic(), 1);

  for (int i = 0; i < kContiguousStaticSamples - 2; i++) {
    filter.SetIsStatic(true);
    EXPECT_FALSE(filter.GetIsStatic());
  }
  filter.SetIsStatic(true);
  EXPECT_TRUE(filter.GetIsStatic());
  EXPECT_EQ(filter.GetNStatic(), kContiguousStaticSamples);
  EXPECT_FALSE(filter.GetIsStaticForN(kContiguousStaticSamples + 1));

  filter.SetIsStatic(false);

  for (int i = 0; i < kContiguousStaticSamples - 1; i++) {
    filter.SetIsStatic(true);
    EXPECT_FALSE(filter.GetIsStatic());
  }
  filter.SetIsStatic(true);
  EXPECT_TRUE(filter.GetIsStatic());
  EXPECT_EQ(filter.GetNStatic(), kContiguousStaticSamples);
}

TEST(LowPassFilterTest, AddSampleDataTillSettled) {
  // startup_time_s_ is 1.0.
  LowPassFilter filter(1.0);
  filter.AddSampleData(Eigen::Vector3d(1.0, 0.0, 0.0), 1.0);
  EXPECT_FALSE(filter.HasSettled());
  filter.AddSampleData(Eigen::Vector3d(1.0, 0.0, 0.0), 2.0);
  EXPECT_TRUE(filter.HasSettled());
}

TEST(LowPassFilterTest, ResetSettledFilter) {
  LowPassFilter filter(1.0);
  filter.AddSampleData(Eigen::Vector3d(1.0, 0.0, 0.0), 1.0);
  filter.AddSampleData(Eigen::Vector3d(1.0, 0.0, 0.0), 2.0);
  EXPECT_TRUE(filter.HasSettled());

  filter.Reset();

  EXPECT_FALSE(filter.HasSettled());
  EXPECT_FALSE(filter.IsInitialized());
  EXPECT_FALSE(filter.GetIsStatic());
  EXPECT_EQ(filter.GetNStatic(), 0);
  EXPECT_EQ(filter.GetRunTime(), 0);
}

}  // namespace mahony_filter

