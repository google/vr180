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

#include <cmath>

#include "cpp/sensor_fusion/geometry_toolbox_mahony.h"
#include "cpp/sensor_fusion/quaternion_integrator.h"

QuaternionIntegrator::QuaternionIntegrator() {}

void QuaternionIntegrator::Integrate(
    const Eigen::Matrix<double, 4, 1>& state,
    const Eigen::Matrix<double, 6, 1>& gyro_measurements, const double delta_t,
    Eigen::Matrix<double, 4, 1>* next_state) const {
  EulerStateTransition(state, gyro_measurements, delta_t, next_state);

  // Normalize the quaternion.
  next_state->normalize();
  if ((*next_state)(3, 0) < 0) {
    *next_state = -*next_state;
  }
}

void QuaternionIntegrator::EulerStateTransition(
    const Eigen::Matrix<double, 4, 1>& previous_state,
    const Eigen::Matrix<double, 6, 1>& gyro_measurements,
    const double step_size, Eigen::Matrix<double, 4, 1>* next_state) const {
  Eigen::Matrix<double, 4, 1> K1_state;
  StateTimeDerivative(0, step_size, previous_state, gyro_measurements,
                      &K1_state);
  *next_state = previous_state + K1_state;
}

void QuaternionIntegrator::RungeKuttaSecondOrderStateTransition(
    const Eigen::Matrix<double, 4, 1>& previous_state,
    const Eigen::Matrix<double, 6, 1>& gyro_measurements,
    const double step_size, Eigen::Matrix<double, 4, 1>* next_state) const {
  Eigen::Matrix<double, 4, 1> K1_state;
  Eigen::Matrix<double, 4, 1> K2_state;
  StateTimeDerivative(0, step_size, previous_state, gyro_measurements,
                      &K1_state);
  // There is a family of second order methods, and this uses alpha = 2/3.
  StateTimeDerivative(2.0 / 3.0 * step_size, step_size,
                      previous_state + 2.0 / 3.0 * K1_state, gyro_measurements,
                      &K2_state);
  *next_state = previous_state + (K1_state + 3. * K2_state) / 4.;
}

void QuaternionIntegrator::RungeKuttaFourthOrderStateTransition(
    const Eigen::Matrix<double, 4, 1>& previous_state,
    const Eigen::Matrix<double, 6, 1>& gyro_measurements,
    const double step_size, Eigen::Matrix<double, 4, 1>* next_state) const {
  Eigen::Matrix<double, 4, 1> K1_state;
  Eigen::Matrix<double, 4, 1> K2_state;
  Eigen::Matrix<double, 4, 1> K3_state;
  Eigen::Matrix<double, 4, 1> K4_state;
  StateTimeDerivative(0, step_size, previous_state, gyro_measurements,
                      &K1_state);
  StateTimeDerivative(0.5 * step_size, step_size,
                      previous_state + 0.5 * K1_state, gyro_measurements,
                      &K2_state);
  StateTimeDerivative(0.5 * step_size, step_size,
                      previous_state + 0.5 * K2_state, gyro_measurements,
                      &K3_state);
  StateTimeDerivative(step_size, step_size, previous_state + K3_state,
                      gyro_measurements, &K4_state);

  *next_state =
      previous_state + (K1_state + 2 * K2_state + 2 * K3_state + K4_state) / 6.;
}

void QuaternionIntegrator::StateTimeDerivative(
    const double t, const double step_size,
    const Eigen::Matrix<double, 4, 1>& state,
    const Eigen::Matrix<double, 6, 1>& gyro_measurements,
    Eigen::Matrix<double, 4, 1>* state_derivative) const {
  state_derivative->setZero();
  // Quaternion time derivative.
  state_derivative->block<4, 1>(0, 0) =
      0.5 * geometry_toolbox::Omega(gyro_measurements.block<3, 1>(0, 0) +
                                    (gyro_measurements.block<3, 1>(3, 0) -
                                     gyro_measurements.block<3, 1>(0, 0)) *
                                        t / step_size) *
      state.block<4, 1>(0, 0);

  // This is a scaling factor that applies to each step of Runge-Kutta. We
  // perform it here to save duplicating code in the Runge-Kutta function.
  *state_derivative *= step_size;
}
