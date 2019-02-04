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

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;

/** Device-specific util functions for configuring the CameraPreview */
public class CameraConfigurator {

  // Set necessary OEM-specific system property for preview.
  public void preparePreview(PreviewConfig config) {}

  // OEM-speicfic keys for CaptureRequest.Builder.
  public void setDeviceSpecificKeys(CaptureRequest.Builder builder) {}

  // Return the OEM-specific camera ID. Default to 0.
  public String getPreviewCameraId(CameraManager cameraManager) throws CameraAccessException {
    return cameraManager.getCameraIdList()[0];
  }
}
