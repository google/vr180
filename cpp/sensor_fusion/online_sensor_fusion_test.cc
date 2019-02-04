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

#include "cpp/sensor_fusion/online_sensor_fusion.h"

#include <memory>
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include <Eigen/Core>
#include <Eigen/Geometry>

namespace vr180 {

namespace {

static const double kEpsilon = 1e-7;
static const double kGravity = 9.81;  // m/s^2
static const Eigen::Vector3d kDown(0, 1, 0);

Eigen::Vector3d GetEulerAngles(const Eigen::Matrix3d& coeff) {
  Eigen::Vector3d result;
  result[0] = std::atan2(coeff(0, 2), coeff(2, 2));
  const double c2 = Eigen::Vector2d(coeff(1, 1), coeff(1, 0)).norm();
  result[1] = std::atan2(-coeff(1, 2), c2);
  const double s1 = std::sin(result[0]);
  const double c1 = std::cos(result[0]);
  result[2] = std::atan2(s1 * coeff(2, 1) - c1 * coeff(0, 1),
                         c1 * coeff(0, 0) - s1 * coeff(2, 0));
  return result;
}

Eigen::Matrix3d GetRotationMatrix(const Eigen::Vector3f& orientation) {
  Eigen::Vector3d aa = orientation.cast<double>();
  return Eigen::Quaterniond(Eigen::AngleAxisd(aa.norm(), aa.normalized()))
      .toRotationMatrix();
}

OnlineSensorFusion Init(const Eigen::Vector3d& gravity) {
  OnlineSensorFusion filter = OnlineSensorFusion(OnlineSensorFusion::Options());
  for (int i = 0; i < 2; i++) {
    filter.AddAccelMeasurement(gravity, i);
    filter.AddGyroMeasurement(Eigen::Vector3d::Zero(), i);
  }
  return filter;
}
}  // namespace

TEST(OnlineSensorFusionTest, GetStaticOrientation) {
  OnlineSensorFusion filter = Init(Eigen::Vector3d(0, 0, -kGravity));
  const Eigen::Matrix3d orientation =
      GetRotationMatrix(filter.GetOrientation());
  // Orientation tranform should cause z-axis to point down.
  const Eigen::Vector3d down = orientation * Eigen::Vector3d::UnitZ();
  EXPECT_TRUE(down.isApprox(kDown, kEpsilon));
}

TEST(OnlineSensorFusionTest, GetStaticPortraitOrientation) {
  const OnlineSensorFusion filter = Init(Eigen::Vector3d(0, -kGravity, 0));
  const Eigen::Matrix3d orientation =
      GetRotationMatrix(filter.GetOrientation());
  // Orientation tranform should cause y-axis to point down.
  const Eigen::Vector3d down = orientation * Eigen::Vector3d::UnitY();
  EXPECT_TRUE(down.isApprox(kDown, kEpsilon));
}

TEST(OnlineSensorFusionTest, GetStaticLandscapeOrientation) {
  const OnlineSensorFusion filter = Init(Eigen::Vector3d(-kGravity, 0, 0));
  const Eigen::Matrix3d orientation =
      GetRotationMatrix(filter.GetOrientation());
  // Orientation tranform should cause x-axis to point down.
  const Eigen::Vector3d down = orientation * Eigen::Vector3d::UnitX();
  EXPECT_TRUE(down.isApprox(kDown, kEpsilon));
}

TEST(OnlineSensorFusionTest, Recenter) {
  OnlineSensorFusion filter = Init(Eigen::Vector3d(0, -kGravity, 0));
  // Rotate ~M_PI/4 about each axis.
  for (int i = 200; i > 0; i--) {
    filter.AddAccelMeasurement(Eigen::Vector3d(0, -kGravity, 0), 1 + 1.0 / i);
    filter.AddGyroMeasurement(Eigen::Vector3d(0, M_PI / 4, 0), 1 + 1.0 / i);
  }
  for (int i = 200; i > 0; i--) {
    filter.AddAccelMeasurement(Eigen::Vector3d(0, -kGravity / 2, -kGravity / 2),
                               2 + 1.0 / i);
    filter.AddGyroMeasurement(Eigen::Vector3d(M_PI / 4, 0, 0), 2 + 1.0 / i);
  }
  for (int i = 200; i > 0; i--) {
    filter.AddAccelMeasurement(
        Eigen::Vector3d(-kGravity / 3, -kGravity / 3, -kGravity / 3),
        3 + 1.0 / i);
    filter.AddGyroMeasurement(Eigen::Vector3d(0, 0, M_PI / 4), 3 + 1.0 / i);
  }
  const Eigen::Vector3d angles =
      GetEulerAngles(GetRotationMatrix(filter.GetOrientation()));
  // Verify there is non-zero rotation about all axes.
  for (int i = 0; i < 3; i++) {
    EXPECT_GT(fabs(angles[i]), kEpsilon);
  }
  filter.Recenter();
  const Eigen::Vector3d recentered_angles =
      GetEulerAngles(GetRotationMatrix(filter.GetOrientation()));
  // Expect the recentered orientation to have no rotation about the y-axis.
  EXPECT_NEAR(recentered_angles[0], 0, kEpsilon);
  // Expect the recentered orientation to have the same rotations about the
  // x- z-axes as the non-recentered orientation.
  EXPECT_NEAR(recentered_angles[1], angles[1], kEpsilon);
  EXPECT_NEAR(recentered_angles[2], angles[2], kEpsilon);
}

}  // namespace vr180
