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

#ifndef VR180_CPP_SENSOR_FUSION_QUATERNION_INTEGRATOR_H_
#define VR180_CPP_SENSOR_FUSION_QUATERNION_INTEGRATOR_H_

#include <Eigen/Core>

class QuaternionIntegrator {
 public:
  QuaternionIntegrator();

  // Integrate state over the time interval dt between time-steps k and k+1.
  void Integrate(const Eigen::Matrix<double, 4, 1>& state,
                 const Eigen::Matrix<double, 6, 1>& gyro_measurement,
                 const double delta_t,
                 Eigen::Matrix<double, 4, 1>* next_state) const;
  EIGEN_MAKE_ALIGNED_OPERATOR_NEW

 private:
  void EulerStateTransition(
      const Eigen::Matrix<double, 4, 1>& previous_state,
      const Eigen::Matrix<double, 6, 1>& gyro_measurements,
      const double step_size, Eigen::Matrix<double, 4, 1>* next_state) const;

  // Runge-Kutta numerical integrator.
  void RungeKuttaSecondOrderStateTransition(
      const Eigen::Matrix<double, 4, 1>& previous_state,
      const Eigen::Matrix<double, 6, 1>& gyro_measurements,
      const double step_size, Eigen::Matrix<double, 4, 1>* next_state) const;

  void RungeKuttaFourthOrderStateTransition(
      const Eigen::Matrix<double, 4, 1>& previous_state,
      const Eigen::Matrix<double, 6, 1>& gyro_measurements,
      const double step_size, Eigen::Matrix<double, 4, 1>* next_state) const;

  // Compute the time derivative for the state.
  void StateTimeDerivative(const double time, const double step_size,
                           const Eigen::Matrix<double, 4, 1>& state,
                           const Eigen::Matrix<double, 6, 1>& gyro_measurements,
                           Eigen::Matrix<double, 4, 1>* state_derivative) const;
};

#endif  // VR180_CPP_SENSOR_FUSION_QUATERNION_INTEGRATOR_H_
