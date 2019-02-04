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

// The methods in this toolbox are derived using the convensions and derivations
// in the following technical report:
// [1] N. Trawny and S. I. Roumeliotis. Indirect Kalman Filter for 3D Attitude
//     Estimation. University of Minnesota, Dept. of Comp. Sci. & Eng., Tech.
//     Rep. 2005-002, March 2005.

#ifndef VR180_CPP_SENSOR_FUSION_GEOMETRY_TOOLBOX_MAHONY_H_
#define VR180_CPP_SENSOR_FUSION_GEOMETRY_TOOLBOX_MAHONY_H_

#include <algorithm>
#include <cmath>

#include <glog/logging.h>
#include <Eigen/Core>
#include <Eigen/Geometry>

namespace geometry_toolbox {
// Omega matrix used in the computation of the quaternion time derivative.
inline Eigen::Matrix4d Omega(const Eigen::Vector3d& w) {
  Eigen::Matrix4d omega;
  omega << 0., w(2), -w(1), w(0), -w(2), 0., w(0), w(1), w(1), -w(0), 0., w(2),
      -w(0), -w(1), -w(2), 0.;
  return omega;
}

// Skew-symmetric (cross-product) matrix.
inline Eigen::Matrix<double, 3, 3> SkewSymmetricMatrix(
    const Eigen::Vector3d& x) {
  Eigen::Matrix<double, 3, 3> s;
  s << 0, -x(2), x(1), x(2), 0, -x(0), -x(1), x(0), 0;
  return s;
}

// Quaternion inverse.
inline Eigen::Matrix<double, 4, 1> QuaternionInverse(
    const Eigen::Matrix<double, 4, 1>& q) {
  Eigen::Matrix<double, 4, 1> inverse_q = q;
  inverse_q.block<3, 1>(0, 0) = -q.block<3, 1>(0, 0);
  return inverse_q;
}

// Conversion from quaternion to rotation matrix.
inline Eigen::Matrix<double, 3, 3> QuaternionToRotationMatrix(
    const Eigen::Vector4d& q) {
  Eigen::Matrix<double, 3, 3> R;
  R(0, 0) = q(0) * q(0) - q(1) * q(1) - q(2) * q(2) + q(3) * q(3);
  R(0, 1) = 2 * (q(0) * q(1) + q(2) * q(3));
  R(0, 2) = 2 * (q(0) * q(2) - q(1) * q(3));

  R(1, 0) = 2 * (q(0) * q(1) - q(2) * q(3));
  R(1, 1) = -q(0) * q(0) + q(1) * q(1) - q(2) * q(2) + q(3) * q(3);
  R(1, 2) = 2 * (q(1) * q(2) + q(0) * q(3));

  R(2, 0) = 2 * (q(0) * q(2) + q(1) * q(3));
  R(2, 1) = 2 * (q(1) * q(2) - q(0) * q(3));
  R(2, 2) = -q(0) * q(0) - q(1) * q(1) + q(2) * q(2) + q(3) * q(3);
  return R;
}

// Compute the product q1 * q2.
inline Eigen::Vector4d QuaternionMultiplication(const Eigen::Vector4d& q1,
                                                const Eigen::Vector4d& q2) {
  Eigen::Matrix4d L;
  L.block<3, 3>(0, 0) = q1(3) * Eigen::Matrix<double, 3, 3>::Identity() -
                        SkewSymmetricMatrix(q1.block<3, 1>(0, 0));
  L.block<1, 3>(3, 0) = -q1.block<3, 1>(0, 0).transpose();
  L.block<4, 1>(0, 3) = q1;

  Eigen::Vector4d result = static_cast<Eigen::Vector4d>(L * q2);
  result = result / result.norm();

  if (result(3) < 0) {
    result = -result;
  }
  return result;
}

// Computes axis angle rotation from a unit quaternion.
//
// This code is not numerically stables and should not be used in production.
// This is only used for testing and debugging.
inline Eigen::Vector3d QuaternionToAxisAngle(
    const Eigen::Matrix<double, 4, 1>& quat) {
  if ((1 - std::abs(quat[3])) < 1e-15) {
    return Eigen::Vector3d::Zero();
  }

  // Convert from JPL to Hamilton.
  const Eigen::Matrix<double, 4, 1>& quat_inv = QuaternionInverse(quat);
  const double angle = 2 * std::acos(quat_inv(3));
  // TODO: check when s ~ 1.0.
  const double s = 1.0 / sqrt(1 - quat_inv(3) * quat_inv(3));

  return angle * s * quat_inv.head<3>();
}

// Conversion from rotation matrix to quaternion.
// This implementation is borrowed from ion, and has been adapted to produce
// JPL quaternions: http://shortn/_HJ3EtQvbkW
inline Eigen::Vector4d RotationMatrixToQuaternion(
    const Eigen::Matrix<double, 3, 3>& mat) {
  static const double kOne = 1.0;
  static const double kFour = 4.0;

  const double d0 = mat(0, 0), d1 = mat(1, 1), d2 = mat(2, 2);
  const double ww = kOne + d0 + d1 + d2;
  const double xx = kOne + d0 - d1 - d2;
  const double yy = kOne - d0 + d1 - d2;
  const double zz = kOne - d0 - d1 + d2;

  const double max = std::max(ww, std::max(xx, std::max(yy, zz)));
  if (ww == max) {
    const double w4 = std::sqrt(ww * kFour);
    return Eigen::Vector4d(-(mat(2, 1) - mat(1, 2)) / w4,
                           -(mat(0, 2) - mat(2, 0)) / w4,
                           -(mat(1, 0) - mat(0, 1)) / w4, w4 / kFour);
  }

  if (xx == max) {
    const double x4 = std::sqrt(xx * kFour);
    return Eigen::Vector4d(-x4 / kFour, -(mat(0, 1) + mat(1, 0)) / x4,
                           -(mat(0, 2) + mat(2, 0)) / x4,
                           (mat(2, 1) - mat(1, 2)) / x4);
  }

  if (yy == max) {
    const double y4 = std::sqrt(yy * kFour);
    return Eigen::Vector4d(-(mat(0, 1) + mat(1, 0)) / y4, -y4 / kFour,
                           -(mat(1, 2) + mat(2, 1)) / y4,
                           (mat(0, 2) - mat(2, 0)) / y4);
  }

  // zz is the largest component.
  const double z4 = std::sqrt(zz * kFour);
  return Eigen::Vector4d(-(mat(0, 2) + mat(2, 0)) / z4,
                         -(mat(1, 2) + mat(2, 1)) / z4, -z4 / kFour,
                         (mat(1, 0) - mat(0, 1)) / z4);
}

// This implementation is borrowed from ion but follows the JPL notation.
// https://google.github.io/ion/rotation_8cc_source.html#l00150
inline Eigen::Vector4d RotateInto(const Eigen::Vector3d& from,
                                  const Eigen::Vector3d& to) {
  constexpr double kEpsilon = 1e-13;

  // Directly build the quaternion using the following technique:
  // http://lolengine.net/blog/2014/02/24/quaternion-from-two-vectors-final
  const double norm_u_norm_v = from.norm() * to.norm();
  double real_part = norm_u_norm_v + from.dot(to);

  Eigen::Vector3d w;
  if (real_part < kEpsilon * norm_u_norm_v) {
    // If |from| and |to| are exactly opposite, rotate 180 degrees around an
    // arbitrary orthogonal axis. Axis normalization can happen later, when we
    // normalize the quaternion.
    real_part = 0.0;
    w = (std::abs(from[0]) > std::abs(from[2]))
            ? Eigen::Vector3d(-from[1], from[0], 0)
            : Eigen::Vector3d(0, -from[2], from[1]);
  } else {
    // Otherwise, build the quaternion the standard way.
    w = from.cross(to);
  }

  // Convert to JPL.
  w = -w;

  // Build and return a normalized quaternion.
  return Eigen::Vector4d(w[0], w[1], w[2], real_part).normalized();
}
// Function to get euler angle from quaternion.
inline Eigen::Vector3d QuaternionToEulers(
    const Eigen::Matrix<double, 4, 1>& quat) {
  // Because we are in JPL format we do not need to invert.

  // Verify that the pitch is not up or done.
  const double pitch_test = quat(2) * quat(1) + quat(0) * quat(3);
  if (pitch_test > 0.4999) {
    // There is a singularity when the pitch is directly up, so calculate
    // the angles another way.
    return Eigen::Vector3d(2. * std::atan2(quat(2), quat(3)), M_PI_2, 0);
  }

  if (pitch_test < -0.4999) {
    // There is a singularity when the pitch is directly down, so calculate
    // the angles another way.
    return Eigen::Vector3d(-2. * std::atan2(quat(2), quat(3)), M_PI_2, 0);
  }

  // There is no singularity, so calculate angles normally.
  // std::atan2(2 * qy * qw - 2 * qz * qx, 1 - 2 * qy * qy - 2 * qx * qx),
  // asin(2 * pitch_test),
  // std::atan2(2 * qz * qw - 2 * qy * qx , 1. - 2. * qz * qz - 2. * qx * qx)
  return Eigen::Vector3d(
      std::atan2(2. * quat(1) * quat(3) - 2. * quat(2) * quat(0),
                 1. - 2. * quat(1) * quat(1) - 2. * quat(0) * quat(0)),
      std::asin(2. * pitch_test),
      std::atan2(2. * quat(2) * quat(3) - 2. * quat(1) * quat(0),
                 1. - 2. * quat(2) * quat(2) - 2. * quat(0) * quat(0)));
}

inline Eigen::Matrix<double, 4, 1> EulersToQuaternion(
    const Eigen::Vector3d& eulers) {
  Eigen::Matrix<double, 4, 1> quat;

  const double c1 = cos(eulers[0]);
  const double s1 = sin(eulers[0]);
  const double c2 = cos(eulers[1]);
  const double s2 = sin(eulers[1]);
  const double c3 = cos(eulers[2]);
  const double s3 = sin(eulers[2]);

  quat(3) = std::sqrt(1.0 + c1 * c2 + c1 * c3 - s1 * s2 * s3 + c2 * c3) * .5;
  const double w4 = 0.25 / quat(3);

  quat(2) = (c2 * s3 + c1 * s3 + s1 * s2 * c3) * w4;
  quat(1) = (s1 * c2 + s1 * c3 + c1 * s2 * s3) * w4;
  quat(0) = (-s1 * s3 + c1 * s2 * c3 + s2) * w4;

  return quat;
}

inline Eigen::Vector2d GetRollandPitchFromQuat(
    const Eigen::Matrix<double, 4, 1>& quat) {
  // Compute gravity from the current pose.
  // A = [0 1 0; 1 0 0; 0 0 -1];
  Eigen::Matrix3d coordinate_swap;
  coordinate_swap << 0.0, 1.0, 0.0, 1, 0.0, 0.0, 0.0, 0.0, -1.0;
  const Eigen::Matrix3d L_R_G =
      geometry_toolbox::QuaternionToRotationMatrix(quat);
  // Since A =A^T we don't need to transpose coordinate_swap.
  // Using auto even though it is a Vector3d as it creates a compiler error with
  // blaze in opt.
  const auto gravity = coordinate_swap * L_R_G * Eigen::Vector3d::UnitZ();

  Eigen::Vector2d roll_pitch;
  roll_pitch(1) = std::atan2(gravity(1), gravity(2));
  roll_pitch(0) = std::atan2(-gravity(0), gravity(1) * sin(roll_pitch(1)) +
                                              gravity(2) * cos(roll_pitch(1)));

  return roll_pitch;
}

}  // namespace geometry_toolbox

#endif  // VR180_CPP_SENSOR_FUSION_GEOMETRY_TOOLBOX_MAHONY_H_
