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

package com.google.vr180.testhelpers;

import com.google.protobuf.ByteString;
import com.google.vr180.CameraApi.CameraCalibration;

/**
 * Provides a CameraCapabilities with a stereo mode for testing the thumbnail stereo mode cropping.
 */
public class TestCameraCalibration {

  /**
   * A test calibration setting which specifies top-bottom stereo (but doens't include a sv3d box).
   */
  public static final CameraCalibration TOP_BOTTOM_STEREO =
      CameraCalibration.newBuilder()
          .setSt3DBox(
              ByteString.copyFrom(new byte[] {0, 0, 0, 13, 115, 116, 51, 100, 0, 0, 0, 0, 1}))
          .setSv3DBox(
              ByteString.copyFrom(new byte[] {19, 20, 21, 22, 23, 24, 55, 66, 77, 88, 99, 0}))
          .build();
}
