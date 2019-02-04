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

#ifndef VR180_CPP_SENSOR_FUSION_ONLINE_SENSOR_FUSION_H_
#define VR180_CPP_SENSOR_FUSION_ONLINE_SENSOR_FUSION_H_

#include <memory>

#include <Eigen/Core>
#include <Eigen/Geometry>
#include "cpp/sensor_fusion/orientation_filter.h"

namespace vr180 {
// Class for fusing gyroscope and accelerometer readings to produce orientation
// for a device with IMU.
//
// Example usage:
//
// * Construct the filter
//   OnlineSensorFusion::Options options;
//   LoadPersistentImuOrientation(persistent_calibration_result_path,
//                                options.device_to_imu_transform);
//   OnlineSensorFusion filter(options);
//
// * Function for streaming processing of events in timestamp order:
//   void OnSensorEventAvailable(filter, event) {
//     if (event.type == accel) {
//        filter.AddAccelMeasurement(event.sample, event.timestamp_s);
//     }  else if (event.type == gyro) {
//        filter.AddGyroMeasurement(event.sample, event.timestamp_s);
//        SaveCammMetadata(event.timestamp_s, filter.GetOrientation());
//     }
//   }
//
// * Other recommended logic:
//   - Right before each video capture, call filter.Recenter() so video starts
//     with the same heading.
//   - Use uncalibrated gyro data, and call filter.SetGyroBias right before
//     each video capture to update the bias once.
class OnlineSensorFusion {
 public:
  struct Options {
    // Controls the bias estimation feedback. A high value decreases the time to
    // adopt to gyroscope bias but can result in a tilting horizon.
    // See OrientationFilter::OrientationFilterConfiguration for details.
    double gyroscope_bias_correction_gain = 0.1;
    // Control the yaw correction feedback for the accelerometer.
    // See OrientationFilter::OrientationFilterConfiguration for details.
    double accel_yaw_correction_gain = 1.0;
    // Control the stationary bias correcion feedback.
    // See OrientationFilter::OrientationFilterConfiguration for details.
    double stationary_bias_correction_gain = 0.1;
    // Rotation between device orientation and imu orientation, which
    // should be calibrated in a factory or approximated according to CAD
    // design.
    //
    // Typically this is close to 0 or 90 degree rotation around Z-axis.
    Eigen::Matrix3d device_to_imu_transform = Eigen::Matrix3d::Identity();
  };

  // Constructs a filter with specified fusion options.
  explicit OnlineSensorFusion(const Options& options);

  // Adds a gyroscope measurement, timesamps should be monotonically increasing.
  //
  // @param sample the rotation around each axis, in rad/s
  // @param timestamp_s the measurement timestamp, in seconds
  void AddGyroMeasurement(const Eigen::Vector3d& sample, double timestamp_s);

  // Adds an accelerometer measurement, timestamps should be monotonically
  // increasing.
  //
  // @param sample the acceleration along each axis, in m/s^2
  // @param timestamp_s the measurement timestamp, in seconds
  void AddAccelMeasurement(const Eigen::Vector3d& sample, double timestamp_s);

  // Returns the sensor-fused orientation of the device in global coordinates as
  // an angle axis; this is the result of applying a gravity transform and
  // sensor-fused orientation to the device_to_imu_transform.
  Eigen::Vector3f GetOrientation() const;

  // Sets the gyro bias to use within the orientation filter. This could be used
  // before a continuous data capture, but should not be used during the
  // capture.
  void SetGyroBias(const Eigen::Vector3d& bias);

  // Resets the yaw of the orientation filter.
  void Recenter();

 private:
  std::unique_ptr<OrientationFilter> orientation_filter_;
  Eigen::Matrix3d calibrated_imu_orientation_;
  double last_timestamp_s_;
};
}  // namespace vr180

#endif  // VR180_CPP_SENSOR_FUSION_ONLINE_SENSOR_FUSION_H_
