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

#include <functional>

#include "gtest/gtest.h"
#include "cpp/sensor_fusion/stationary_detector.h"

namespace mahony_filter {

namespace {
const double kGravityAcceleration = 9.8;
const Eigen::Vector3d kAccReadingWhenInPortrait(0.0, kGravityAcceleration, 0.0);
const Eigen::Vector3d kAccReadingWhenInLandscape(kGravityAcceleration, 0.0,
                                                 0.0);

const Eigen::Vector3d kGyroBiasedReadingWhenNoMotion(0.001, 0., 0.);
const Eigen::Vector3d kGyroReadingWhenInMotion(0.5, 0., 0.);

constexpr double kTimestepSeconds = 0.01;

StationaryDetector::StationaryDetectorConfiguration
GetStationaryDetectorConfig() {
  StationaryDetector::StationaryDetectorConfiguration stationary_config;
  stationary_config.stationary_bias_correction_gain = 1.0;
  return stationary_config;
}

}  // namespace

class StationaryDetectorTest : public ::testing::Test {
 public:
  StationaryDetectorTest() : detector_(GetStationaryDetectorConfig()) {}

 protected:
  void AddStationarySampleData(double duration_s) {
    AddSampleData(duration_s, [this](int i) {
      detector_.AddAccelMeasurement(kAccReadingWhenInLandscape, timestamp_s_);
      detector_.AddGyroMeasurement(kGyroBiasedReadingWhenNoMotion,
                                   timestamp_s_);
    });
  }

  void AddSampleDataWithNonstationaryAccel(double duration_s) {
    AddSampleData(duration_s, [this](int i) {
      // Generates nonstationary accelerometer by switching the pose between
      // landscape and portraint.
      if (i % 2 == 0) {
        detector_.AddAccelMeasurement(kAccReadingWhenInLandscape, timestamp_s_);
      } else {
        detector_.AddAccelMeasurement(kAccReadingWhenInPortrait, timestamp_s_);
      }
      detector_.AddGyroMeasurement(kGyroBiasedReadingWhenNoMotion,
                                   timestamp_s_);
    });
  }

  void AddSampleDataWithNonstationaryGyro(double duration_s) {
    AddSampleData(duration_s, [this](int i) {
      detector_.AddAccelMeasurement(kAccReadingWhenInLandscape, timestamp_s_);
      if (i % 2 == 0) {
        detector_.AddGyroMeasurement(kGyroBiasedReadingWhenNoMotion,
                                     timestamp_s_);
      } else {
        detector_.AddGyroMeasurement(kGyroReadingWhenInMotion, timestamp_s_);
      }
    });
  }

  StationaryDetector detector_;

 private:
  void AddSampleData(double duration_s,
                     const std::function<void(int)>& action) {
    int num_of_sample = duration_s / kTimestepSeconds;
    for (int i = 0; i < num_of_sample; i++) {
      action(i);
      timestamp_s_ += kTimestepSeconds;
    }
  }

  double timestamp_s_ = 0.;
};

TEST_F(StationaryDetectorTest, CreateDetector) {
  EXPECT_FALSE(detector_.IsStationary()) << "New detector is stationary.";
}

// Test entering stationary state after being stable for long enough.
TEST_F(StationaryDetectorTest, EnterStationaryState) {
  AddStationarySampleData(/*duration_s=*/2);
  EXPECT_FALSE(detector_.IsStationary());

  // Need extra time to enter stationary state.
  AddStationarySampleData(/*duration_s=*/10);
  EXPECT_TRUE(detector_.IsStationary());
}

TEST_F(StationaryDetectorTest, GetGyroBiasCorrectionAfterStationary) {
  AddStationarySampleData(/*duration_s=*/40);
  EXPECT_TRUE(detector_.IsStationary());

  EXPECT_EQ(detector_.GetGyroBiasCorrection(Eigen::Vector3d::Zero(), 40),
            -kGyroBiasedReadingWhenNoMotion);
}

TEST_F(StationaryDetectorTest, ExitStationaryStateWhenReceiveShakyAccelSignal) {
  AddStationarySampleData(/*duration_s=*/12);
  EXPECT_TRUE(detector_.IsStationary());

  AddSampleDataWithNonstationaryAccel(/*duration_s=*/1);
  EXPECT_FALSE(detector_.IsStationary());
}

TEST_F(StationaryDetectorTest, ExitStationaryStateWhenReceiveShakyGyroSignal) {
  AddStationarySampleData(/*duration_s=*/12);
  EXPECT_TRUE(detector_.IsStationary());

  AddSampleDataWithNonstationaryGyro(/*duration_s=*/1);
  EXPECT_FALSE(detector_.IsStationary());
}

TEST_F(StationaryDetectorTest, ExitStationaryStateByReset) {
  AddStationarySampleData(/*duration_s=*/12);
  EXPECT_TRUE(detector_.IsStationary());

  detector_.Reset();
  EXPECT_FALSE(detector_.IsStationary());
}

}  // namespace mahony_filter
