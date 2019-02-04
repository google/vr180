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

package com.google.vr180.capture.camera;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Size;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.PhotoMode;
import com.google.vr180.CameraApi.WhiteBalanceMode;
import com.google.vr180.common.logging.Log;
import com.google.vr180.device.DebugConfig;

/** Generate PreviewConfig from CaptureMode. Customize this class for OEM-specific preview config */
public class DefaultPreviewConfigProvider implements PreviewConfigProvider {
  private static final String TAG = "DefaultPreviewConfigProvider";
  private static final int PREFERRED_FRAMERATE = 30;

  @Override
  public PreviewConfig getPreviewConfigForCaptureMode(
      CameraCharacteristics cameraCharacteristics, CaptureMode mode) {
    switch (mode.getActiveCaptureType()) {
      case VIDEO:
      case LIVE:
        return getPreviewConfigForVideoMode(cameraCharacteristics, mode);
      case PHOTO:
        return getPreviewConfigForPhotoMode(cameraCharacteristics, mode);
      default:
        return null;
    }
  }

  protected PreviewConfig getPreviewConfigForVideoMode(
      CameraCharacteristics cameraCharacteristics, CaptureMode captureMode) {
    PreviewConfig.Builder builder = PreviewConfig.builder();
    // Use the default preview size.
    Size previewSize =
        PreviewSizeChooser.choose(
            cameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                .getOutputSizes(SurfaceTexture.class));
    builder
        .setPreviewWidth(previewSize.getWidth())
        .setPreviewHeight(previewSize.getHeight())
        .setFramesPerSecond(PREFERRED_FRAMERATE)
        .setWhiteBalanceMode(mapWhiteBalanceMode(captureMode.getWhiteBalanceMode()))
        .setExposureAdjustmentValue(
            getExposureAdjustmentValue(cameraCharacteristics, captureMode));

    setDebugSettings(builder);
    return builder.build();
  }

  protected PreviewConfig getPreviewConfigForPhotoMode(
      CameraCharacteristics cameraCharacteristics, CaptureMode captureMode) {
    PhotoMode mode = captureMode.getConfiguredPhotoMode();
    // Set a low resolution preview during photo capture.
    PreviewConfig.Builder builder =
        PreviewConfig.builder()
            .setPreviewWidth(1280)
            .setPreviewHeight(960)
            .setFramesPerSecond(PREFERRED_FRAMERATE)
            .setPhotoWidth(mode.getFrameSize().getFrameWidth())
            .setPhotoHeight(mode.getFrameSize().getFrameHeight())
            .setWhiteBalanceMode(mapWhiteBalanceMode(captureMode.getWhiteBalanceMode()))
            .setExposureAdjustmentValue(
                getExposureAdjustmentValue(cameraCharacteristics, captureMode));

    setDebugSettings(builder);
    return builder.build();
  }

  protected static void setDebugSettings(PreviewConfig.Builder builder) {
    // Load debug settings.
    Size debugPreviewSize = DebugConfig.getPreviewSize();
    if (debugPreviewSize != null) {
      Log.i(TAG, "Select preview size from DebugConfig " + debugPreviewSize);
      builder
          .setPreviewWidth(debugPreviewSize.getWidth())
          .setPreviewHeight(debugPreviewSize.getHeight());
    }

    Rect debugPreviewCrop = DebugConfig.getPreviewCrop();
    if (debugPreviewCrop != null) {
      Log.i(TAG, "Set crop from DebugConfig : " + debugPreviewCrop);
      builder.setScalarCrop(debugPreviewCrop);
    }

    int debugPreviewFps = DebugConfig.getPreviewFps();
    if (debugPreviewFps > 0) {
      Log.i(TAG, "Set fps from DebugConfig : " + debugPreviewFps);
      builder.setFramesPerSecond(debugPreviewFps);
    }

    int debugExposureTimeMs = DebugConfig.getSensorExposureTimeMs();
    int debugSensitivity = DebugConfig.getSensorSensitivity();
    if (debugSensitivity > 0 && debugExposureTimeMs > 0) {
      builder.setExposureTimeMs(debugExposureTimeMs).setSensitivity(debugSensitivity);
      Log.i(TAG, "Manual exposure time = " + debugExposureTimeMs + "; iso = " + debugSensitivity);
    }
  }

  protected static int getExposureAdjustmentValue(
      CameraCharacteristics cameraCharacteristics, CaptureMode captureMode) {
    float aeAdjustmentStep =
        cameraCharacteristics
            .get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
            .floatValue();
    return Math.round(
        captureMode.getExposureValueAdjustment() / aeAdjustmentStep);
  }

  /**
   * Convert The {@link CaptureRequest} white balance mode value from {@link WhiteBalanceMode}
   *
   * <p>The {@link WhiteBalanceMode} is defined in the same order as {@link CaptureRequest}
   */
  protected static int mapWhiteBalanceMode(WhiteBalanceMode whiteBalanceMode) {
    switch (whiteBalanceMode) {
      case UNKNOWN_WHITE_BALANCE_MODE:
        return CaptureRequest.CONTROL_AWB_MODE_AUTO;
      case AUTO:
      case INCANDESCENT:
      case FLUORESCENT:
      case WARM_FLUORESCENT:
      case DAYLIGHT:
      case CLOUDY_DAYLIGHT:
      case TWILIGHT:
      case SHADE:
        return whiteBalanceMode.getNumber();
    }
    Log.e(TAG, "Unexpected white balance mode");
    return CaptureRequest.CONTROL_AWB_MODE_AUTO;
  }
}
