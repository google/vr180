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

#include "cpp/sensor_fusion/geometry_toolbox_mahony.h"
#include "cpp/sensor_fusion/orientation_filter.h"

namespace mahony_filter {

class OrientationFilterTest : public ::testing::Test {
 protected:
  OrientationFilterTest()
      : filter_(OrientationFilter::OrientationFilterConfiguration()) {}

  OrientationFilter filter_;
};

TEST_F(OrientationFilterTest, SetAndGetGyroBias) {
  const Eigen::Vector3d gyro_bias(1., 2., 3.);
  filter_.SetGyroBias(gyro_bias);
  EXPECT_EQ(filter_.GetGyroBias(), gyro_bias);
}

TEST_F(OrientationFilterTest, SetAndGetMagBias) {
  const Eigen::Vector3d mag_bias(1., 2., 3.);
  filter_.SetMagBias(mag_bias);
  EXPECT_EQ(filter_.GetMagBias(), mag_bias);
}

TEST_F(OrientationFilterTest, SetAndGetOrientation) {
  const Eigen::Matrix<double, 4, 1> orientation(1., 2., 3., 4.);
  filter_.SetOrientation(orientation);

  EXPECT_EQ(filter_.GetOrientation(), orientation);
  EXPECT_TRUE(filter_.IsOrientationSet());
}

TEST_F(OrientationFilterTest, AddGyroMeasurement) {
  const Eigen::Vector3d gyro_sample(1., 2., 3.);
  const double timestamp_s = 0.1;
  filter_.AddGyroMeasurement(gyro_sample, timestamp_s);

  EXPECT_EQ(filter_.GetLastGyroscopeSample(), gyro_sample);
  EXPECT_EQ(filter_.GetLastGyroscopeTimestamp(), timestamp_s);
}

TEST_F(OrientationFilterTest, AddMagMeasurementAndCheckFit) {
  const Eigen::Vector3d mag_sample(1., 2., 3.);
  filter_.AddMagMeasurement(mag_sample, 0.1);
  EXPECT_FALSE(filter_.IsLastMagSampleFitCalibration());
  filter_.AddMagMeasurement(mag_sample, 0.2, true /* fit_calibration */);
  EXPECT_TRUE(filter_.IsLastMagSampleFitCalibration());
}

TEST_F(OrientationFilterTest, Recenter) {
  const Eigen::Matrix<double, 4, 1> orientation =
      geometry_toolbox::EulersToQuaternion(Eigen::Vector3d(1.2, 2.5, 0.0));
  filter_.SetOrientation(orientation);
  const Eigen::Vector3d gravity_before_recenter =
      filter_.ComputeGravityEstimate();
  filter_.Recenter();
  const Eigen::Vector3d gravity_after_recenter =
      filter_.ComputeGravityEstimate();
  EXPECT_LE((gravity_before_recenter - gravity_after_recenter).norm(), 0.01)
      << "Gravity in the new device frame after recentering deviates too much.";

  EXPECT_NEAR(geometry_toolbox::QuaternionToRotationMatrix(
                  filter_.GetOrientation())(2, 1),
              0.0, 0.001);
}

}  // namespace mahony_filter
