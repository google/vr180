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
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.support.annotation.Nullable;
import com.google.auto.value.AutoValue;

/** Configuration for CameraPreview */
@AutoValue
public abstract class PreviewConfig {
  // Width for texture preview
  public abstract int previewWidth();
  // Height for texture preview.
  public abstract int previewHeight();
  // Sensor crop region for preview
  @Nullable
  public abstract Rect scalarCrop();
  // Frame rate for preview.
  public abstract int framesPerSecond();
  // Width for photo capture.
  public abstract int photoWidth();
  // Height for photo capture.
  public abstract int photoHeight();
  // Sensor exposure time in ms. Must also set sensitivity to be used.
  public abstract int exposureTimeMs();
  // Sensor sensitivity (ISO). Must also set exposureTimeMs to be used.
  public abstract int sensitivity();
  /** White balance mode. Defined in {@link CameraMetadata} */
  public abstract int whiteBalanceMode();
  // Exposure adjustment value.
  public abstract int exposureAdjustmentValue();

  public static Builder builder() {
    return new AutoValue_PreviewConfig.Builder()
        .setPreviewWidth(0)
        .setPreviewHeight(0)
        .setScalarCrop(null)
        .setFramesPerSecond(0)
        .setPhotoWidth(0)
        .setPhotoHeight(0)
        .setExposureTimeMs(0)
        .setSensitivity(0)
        .setWhiteBalanceMode(CaptureRequest.CONTROL_AWB_MODE_AUTO)
        .setExposureAdjustmentValue(0);
  }

  /** Builder for PreviewConfig */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setPreviewWidth(int value);
    public abstract Builder setPreviewHeight(int value);
    public abstract Builder setScalarCrop(Rect value);
    public abstract Builder setFramesPerSecond(int value);
    public abstract Builder setPhotoWidth(int value);
    public abstract Builder setPhotoHeight(int value);
    public abstract Builder setExposureTimeMs(int value);
    public abstract Builder setSensitivity(int value);
    public abstract Builder setWhiteBalanceMode(int value);
    public abstract Builder setExposureAdjustmentValue(int value);
    public abstract PreviewConfig build();
  }

  public boolean isPhotoMode() {
    return photoWidth() > 0 && photoHeight() > 0;
  }

  public boolean isManualExposure() {
    return exposureTimeMs() > 0 && sensitivity() > 0;
  }
}
