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

import com.google.vr180.CameraApi.CameraCapabilities;
import com.google.vr180.CameraApi.EncodingFormat;
import com.google.vr180.CameraApi.FieldOfView;
import com.google.vr180.CameraApi.FrameSize;
import com.google.vr180.CameraApi.PhotoMode;
import com.google.vr180.CameraApi.VideoMode;
import com.google.vr180.CameraApi.ViewfinderMode;
import com.google.vr180.CameraApi.WhiteBalanceMode;

/** Provides a default CameraCapabilities to use where needed in tests. */
public class TestCameraCapabilities {

  /** Approximate bitrate of video recording in bytes per second. */
  public static final long VIDEO_BITRATE = 5 * 1024 * 1024;

  public static final CameraCapabilities TEST_CAPABILITIES =
      CameraCapabilities.newBuilder()
          .setProtocolVersion(0)
          .setManufacturerName("Google")
          .setModelName("Emulator")
          .addSupportedVideoModes(buildVideoMode(3840, 2160, 29.97f))
          .addSupportedVideoModes(buildVideoMode(1920, 1080, 29.97f))
          .addSupportedPhotoModes(buildPhotoMode(3840, 2160))
          .addSupportedPhotoModes(buildPhotoMode(2560, 1920))
          .addSupportedLiveModes(buildVideoMode(3840, 2160, 29.97f))
          .addSupportedLiveModes(buildVideoMode(2560, 1440, 29.97f))
          .addSupportedViewfinderModes(buildViewfinderMode(640, 480, 30f))
          .addSupportedWhiteBalanceModes(WhiteBalanceMode.AUTO)
          .addSupportedWhiteBalanceModes(WhiteBalanceMode.DAYLIGHT)
          .addSupportedWhiteBalanceModes(WhiteBalanceMode.FLUORESCENT)
          .addSupportedIsoLevels(0)
          .addSupportedIsoLevels(100)
          .addSupportedIsoLevels(200)
          .addSupportedIsoLevels(400)
          .setSupportsExposureValueAdjustment(true)
          .setSupportsCalibrationUpdate(false)
          .build();

  private static VideoMode buildVideoMode(int width, int height, float frameRate) {
    return VideoMode.newBuilder()
        .setFrameSize(FrameSize.newBuilder().setFrameWidth(width).setFrameHeight(height))
        .setFramesPerSecond(frameRate)
        .setBitsPerSecond(VIDEO_BITRATE * 8)
        .setEncodingFormat(EncodingFormat.H264)
        .setFieldOfView(FieldOfView.newBuilder().setHorizontalFov(68).setVerticalFov(49).build())
        .build();
  }

  private static PhotoMode buildPhotoMode(int width, int height) {
    return PhotoMode.newBuilder()
        .setFrameSize(FrameSize.newBuilder().setFrameWidth(width).setFrameHeight(height))
        .build();
  }

  private static ViewfinderMode buildViewfinderMode(int width, int height, float frameRate) {
    return ViewfinderMode.newBuilder()
        .setFrameSize(FrameSize.newBuilder().setFrameWidth(width).setFrameHeight(height))
        .setFramesPerSecond(frameRate)
        .build();
  }
}
