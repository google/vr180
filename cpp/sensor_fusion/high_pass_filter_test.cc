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
#include "cpp/sensor_fusion/high_pass_filter.h"

namespace mahony_filter {

TEST(HighPassFilterTest, CreateFilter) {
  HighPassFilter filter(1.0);
  EXPECT_FALSE(filter.IsInitialized());
  EXPECT_EQ(filter.GetLastData(), Eigen::Vector3d::Zero());
  EXPECT_EQ(filter.GetFilteredData(), Eigen::Vector3d::Zero());
}

TEST(HighPassFilterTest, AddSampleDataToNewFilter) {
  HighPassFilter filter(1.0 / (2.0 * M_PI));
  const Eigen::Vector3d sample(3.0, 4.0, 0.0);
  filter.AddSampleData(sample, 1.0);
  EXPECT_TRUE(filter.IsInitialized());
  EXPECT_EQ(filter.GetFilteredData(), sample);
  EXPECT_EQ(filter.GetFilteredDataNorm(), 5.0);
  EXPECT_EQ(filter.GetFilteredDataDirection(), Eigen::Vector3d(0.6, 0.8, 0.0));
  EXPECT_EQ(filter.GetLastData(), Eigen::Vector3d::Zero());

  // The filter we implemented will cut the filtered data by half if it's feed
  // by the same sample. Eventually it will fast converge to 0.
  filter.AddSampleData(sample, 1.0);
  EXPECT_EQ(filter.GetFilteredData(), sample);
  EXPECT_EQ(filter.GetLastData(), sample);
  filter.AddSampleData(sample, 1.0);
  EXPECT_EQ(filter.GetFilteredData(), sample/2.0);
  EXPECT_EQ(filter.GetLastData(), sample);
  filter.AddSampleData(sample, 1.0);
  EXPECT_EQ(filter.GetFilteredData(), sample/4.0);
  EXPECT_EQ(filter.GetLastData(), sample);
}

}  // namespace mahony_filter
