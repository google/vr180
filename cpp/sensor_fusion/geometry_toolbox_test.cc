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
#include <Eigen/Dense>
#include "cpp/sensor_fusion/geometry_toolbox_mahony.h"

namespace geometry_toolbox {

namespace {
static const double kEpsilon = 1e-6;

static void ExpectVector3dNear(const Eigen::Vector3d& actual,
                               const double* expected) {
  EXPECT_NEAR(expected[0], actual[0], kEpsilon);
  EXPECT_NEAR(expected[1], actual[1], kEpsilon);
  EXPECT_NEAR(expected[2], actual[2], kEpsilon);
}

static void ExpectVector4dNear(const Eigen::Matrix<double, 4, 1>& expected,
                               const Eigen::Matrix<double, 4, 1>& actual) {
  EXPECT_NEAR(expected[0], actual[0], kEpsilon);
  EXPECT_NEAR(expected[1], actual[1], kEpsilon);
  EXPECT_NEAR(expected[2], actual[2], kEpsilon);
  EXPECT_NEAR(expected[3], actual[3], kEpsilon);
}

}  // namespace

TEST(GeometryToolbox, QuaternionToEulers) {
  Eigen::Matrix<double, 4, 1> quat;
  // All expected value were computed using a matlab implementation.
  {
    quat << 0.0, 0.0, 0.0, 1.0;
    constexpr double expected[] = {0.0, 0.0, 0.0};
    ExpectVector3dNear(QuaternionToEulers(quat), expected);
  }

  {
    quat << -0.024657850829989, -0.624700039864367, 0.018989587093746,
        0.780244388005632;
    constexpr double exptected[3] = {-1.350306844037366, -0.062244071932762,
                                     -0.001176762317642};
    ExpectVector3dNear(QuaternionToEulers(quat), exptected);
  }

  {
    quat << 0.009547657792896, -0.631397888280270, 0.064728280346221,
        0.772694041764037;
    constexpr double expected[3] = {-1.366429575928525, -0.067033935568638,
                                    0.112577003275372};
    ExpectVector3dNear(QuaternionToEulers(quat), expected);
  }

  {
    quat << -0.024211118816450, -0.614705994342074, 0.032490402400773,
        0.787714504243445;
    constexpr double expected[3] = {-1.324467924930578, -0.078166611264986,
                                    0.021488144308779};
    ExpectVector3dNear(QuaternionToEulers(quat), expected);
  }

  {
    quat << 0.211497143365984, -0.583550548530721, 0.328703138468388,
        0.711822827248442;
    constexpr double expected[3] = {-1.338450622173412, -0.082626809054041,
                                    0.799836954059947};
    ExpectVector3dNear(QuaternionToEulers(quat), expected);
  }

  {
    quat << 0.633201638997103, -0.102410498152033, 0.763506409749276,
        0.074998622715013;
    constexpr double expected[3] = {-1.392381633141328, -0.061442351635838,
                                    2.894404259544006};
    ExpectVector3dNear(QuaternionToEulers(quat), expected);
  }

  {
    quat << -0.382824158093611, -0.517651528756865, -0.424864487710911,
        0.636374528756865;
    constexpr double expected[3] = {-1.398792297665422, -0.047393331936760,
                                    -1.217214425566953};
    ExpectVector3dNear(QuaternionToEulers(quat), expected);
  }
}

TEST(GeometryToolbox, EulersToQuaternion) {
  Eigen::Matrix<double, 4, 1> quat;
  // All expected value were computed using a matlab implementation.
  {
    quat << 0.0, 0.0, 0.0, 1.0;
    ExpectVector4dNear(quat, EulersToQuaternion(QuaternionToEulers(quat)));
  }

  {
    quat << -0.024657850829989, -0.624700039864367, 0.018989587093746,
        0.780244388005632;
    ExpectVector4dNear(quat, EulersToQuaternion(QuaternionToEulers(quat)));
  }

  {
    quat << 0.009547657792896, -0.631397888280270, 0.064728280346221,
        0.772694041764037;
    ExpectVector4dNear(quat, EulersToQuaternion(QuaternionToEulers(quat)));
  }
  {
    quat << -0.024211118816450, -0.614705994342074, 0.032490402400773,
        0.787714504243445;
    ExpectVector4dNear(quat, EulersToQuaternion(QuaternionToEulers(quat)));
  }

  {
    quat << 0.211497143365984, -0.583550548530721, 0.328703138468388,
        0.711822827248442;
    ExpectVector4dNear(quat, EulersToQuaternion(QuaternionToEulers(quat)));
  }

  {
    quat << 0.633201638997103, -0.102410498152033, 0.763506409749276,
        0.074998622715013;
    ExpectVector4dNear(quat, EulersToQuaternion(QuaternionToEulers(quat)));
  }

  {
    quat << -0.382824158093611, -0.517651528756865, -0.424864487710911,
        0.636374528756865;
    ExpectVector4dNear(quat, EulersToQuaternion(QuaternionToEulers(quat)));
  }
}

TEST(GeometryToolbox, GetRollandPitchFromQuat) {
  Eigen::Matrix<double, 4, 1> quat;
  Eigen::Vector2d roll_pitch;
  // All expected value were computed using a matlab implementation.
  quat << 0.0, 0.0, 0.0, 1.0;
  roll_pitch = GetRollandPitchFromQuat(quat);
  EXPECT_NEAR(0.0, roll_pitch(0), kEpsilon);
  EXPECT_NEAR(M_PI, roll_pitch(1), kEpsilon);

  quat << 0.211497143365984, -0.583550548530721, 0.328703138468388,
      0.711822827248442;
  roll_pitch = GetRollandPitchFromQuat(quat);
  EXPECT_NEAR(0.082626804777616, roll_pitch(0), kEpsilon);
  EXPECT_NEAR(1.803142251145949, roll_pitch(1), kEpsilon);

  quat << -0.382824158093611, -0.517651528756865, -0.424864487710911,
      0.636374528756865;
  roll_pitch = GetRollandPitchFromQuat(quat);
  EXPECT_NEAR(0.047393330436759, roll_pitch(0), kEpsilon);
  EXPECT_NEAR(1.742800538386780, roll_pitch(1), kEpsilon);
}

}  // namespace geometry_toolbox
