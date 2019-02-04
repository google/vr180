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

#include "absl/memory/memory.h"
#include <Eigen/Core>
#include <Eigen/Geometry>

namespace vr180 {
namespace {
Eigen::Matrix3d LoadGravityAdjustMatrix() {
  Eigen::Matrix3d rotation;
  rotation.row(0) = Eigen::RowVector3d(1, 0, 0);
  rotation.row(1) = Eigen::RowVector3d(0, 0, -1);
  rotation.row(2) = Eigen::RowVector3d(0, 1, 0);
  return rotation;
}

OrientationFilter::OrientationFilterConfiguration GetFilterConfiguration(
    const OnlineSensorFusion::Options& options) {
  OrientationFilter::OrientationFilterConfiguration config;
  config.stationary_bias_correction_gain =
      options.stationary_bias_correction_gain;
  config.gyroscope_bias_correction_gain =
      options.gyroscope_bias_correction_gain;
  config.accel_yaw_correction_gain = options.accel_yaw_correction_gain;
  return config;
}

}  // namespace

OnlineSensorFusion::OnlineSensorFusion(const Options& options) {
  orientation_filter_ =
      absl::make_unique<OrientationFilter>(GetFilterConfiguration(options));
  calibrated_imu_orientation_ = options.device_to_imu_transform;
  last_timestamp_s_ = 0.0;
}

void OnlineSensorFusion::AddGyroMeasurement(const Eigen::Vector3d& sample,
                                            double timestamp_s) {
  if (timestamp_s < last_timestamp_s_) {
    LOG(WARNING) << "gyro timestamps not monotonically increasing";
  }
  orientation_filter_->AddGyroMeasurement(sample, timestamp_s);
  last_timestamp_s_ = timestamp_s;
}

void OnlineSensorFusion::AddAccelMeasurement(const Eigen::Vector3d& sample,
                                             double timestamp_s) {
  if (timestamp_s < last_timestamp_s_) {
    LOG(WARNING) << "accel timestamps not monotonically increasing";
  }
  orientation_filter_->AddAccelMeasurement(sample, timestamp_s);
  last_timestamp_s_ = timestamp_s;
}

Eigen::Vector3f OnlineSensorFusion::GetOrientation() const {
  const Eigen::Matrix3d orientation =
      Eigen::Quaterniond(orientation_filter_->GetOrientation())
          .toRotationMatrix();
  const Eigen::Matrix3d matrix =
      (LoadGravityAdjustMatrix() * orientation * calibrated_imu_orientation_);
  const Eigen::AngleAxisd angle_axis(matrix);
  const Eigen::Vector3d aa_double = angle_axis.angle() * angle_axis.axis();
  return aa_double.cast<float>();
}

void OnlineSensorFusion::SetGyroBias(const Eigen::Vector3d& bias) {
  orientation_filter_->SetGyroBias(bias);
}

void OnlineSensorFusion::Recenter() {
  const Eigen::Vector4d q = orientation_filter_->GetOrientation();
  const Eigen::Quaterniond quaternion(q[3], q[0], q[1], q[2]);
  const Eigen::Matrix3d m = quaternion.toRotationMatrix();
  Eigen::Matrix3d adjust = Eigen::Matrix3d::Identity();
  adjust.row(1) = m.col(2).transpose();
  if (adjust.row(1).cross(adjust.row(2)).squaredNorm() <
      std::numeric_limits<double>::epsilon()) {
    return;
  }
  adjust.row(0) = adjust.row(1).cross(adjust.row(2)).normalized();
  adjust.row(1) = adjust.row(2).cross(adjust.row(0)).normalized();
  const Eigen::Quaterniond aq(adjust * m);
  orientation_filter_->SetOrientation(
      Eigen::Vector4d(aq.x(), aq.y(), aq.z(), aq.w()));
}
}  // namespace vr180
