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

package com.google.vr180.app.stubs;

import com.google.vr180.CameraApi.CameraCapabilities;
import com.google.vr180.CameraApi.EncodingFormat;
import com.google.vr180.CameraApi.FieldOfView;
import com.google.vr180.CameraApi.FrameSize;
import com.google.vr180.CameraApi.PhotoMode;
import com.google.vr180.CameraApi.ProjectionType;
import com.google.vr180.CameraApi.VideoMode;
import com.google.vr180.CameraApi.ViewfinderMode;
import com.google.vr180.CameraApi.WhiteBalanceMode;
import com.google.vr180.api.camerainterfaces.CapabilitiesProvider;

/** Initial implementation of CapabilitiesProvider. */
public class EmulatorCapabilitiesProvider implements CapabilitiesProvider {

  // The exposure adjustment step.
  public static final float AE_ADJUSTMENT_STEP = 1.0f / 6;
  public static final float FOV_X = 67;
  public static final float FOV_Y = 53;

  @Override
  public CameraCapabilities getCapabilities() {
    CameraCapabilities.Builder builder = CameraCapabilities.newBuilder();
    builder
        .setProtocolVersion(0)
        .setManufacturerName("Google")
        .setModelName("Reference VR180")
        .setSupportsCalibrationUpdate(false)
        .setUpdateCapability(
            CameraCapabilities.UpdateCapability.newBuilder().setSupportsOtaUpdate(false))
        .addSupportedVideoModes(
            buildVideoMode(3840, 2160, 30, 45 * 1024 * 1024, false, FOV_X, FOV_Y))
        .addSupportedVideoModes(
            buildVideoMode(3840, 2160, 30, 45 * 1024 * 1024, false, FOV_X, FOV_Y)
                .setProjectionType(ProjectionType.EQUIRECT))
        .addSupportedVideoModes(
            buildVideoMode(2560, 1440, 30, 30 * 1024 * 1024, false, FOV_X, FOV_Y))
        .addSupportedLiveModes(
            buildVideoMode(3840, 2160, 30, 20 * 1024 * 1024, false, FOV_X, FOV_Y))
        .addSupportedLiveModes(
            buildVideoMode(3840, 2160, 30, 20 * 1024 * 1024, false, FOV_X, FOV_Y)
                .setProjectionType(ProjectionType.EQUIRECT))
        .addSupportedPhotoModes(
            PhotoMode.newBuilder()
                .setFrameSize(FrameSize.newBuilder().setFrameWidth(4000).setFrameHeight(3000))
                .setFieldOfView(
                    FieldOfView.newBuilder().setHorizontalFov(FOV_Y).setVerticalFov(FOV_Y)))
        .addSupportedWhiteBalanceModes(WhiteBalanceMode.AUTO)
        .addSupportedWhiteBalanceModes(WhiteBalanceMode.DAYLIGHT)
        .addSupportedWhiteBalanceModes(WhiteBalanceMode.FLUORESCENT)
        .addSupportedWhiteBalanceModes(WhiteBalanceMode.TWILIGHT)
        .addSupportedWhiteBalanceModes(WhiteBalanceMode.WARM_FLUORESCENT)
        .addSupportedWhiteBalanceModes(WhiteBalanceMode.SHADE)
        .addSupportedIsoLevels(0)
        .addSupportedIsoLevels(100)
        .addSupportedIsoLevels(200)
        .addSupportedIsoLevels(400)
        .setSupportsExposureValueAdjustment(true)
        .setExposureAdjustmentStep(AE_ADJUSTMENT_STEP)
        .addSupportedViewfinderModes(
            ViewfinderMode.newBuilder()
                .setFrameSize(FrameSize.newBuilder().setFrameWidth(640).setFrameHeight(480))
                .setFramesPerSecond(30f));

    return builder.build();
  }

  protected static VideoMode.Builder buildVideoMode(
      int width,
      int height,
      float fps,
      long bitsPerSecond,
      boolean useH265,
      float fovx,
      float fovy) {
    return VideoMode.newBuilder()
        .setFrameSize(FrameSize.newBuilder().setFrameWidth(width).setFrameHeight(height))
        .setFramesPerSecond(fps)
        .setBitsPerSecond(bitsPerSecond)
        .setEncodingFormat(useH265 ? EncodingFormat.H265 : EncodingFormat.H264)
        .setFieldOfView(FieldOfView.newBuilder().setHorizontalFov(fovx).setVerticalFov(fovy))
        .setProjectionType(ProjectionType.DEFAULT_FISHEYE);
  }
}
