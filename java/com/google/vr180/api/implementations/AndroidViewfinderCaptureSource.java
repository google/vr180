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

package com.google.vr180.api.implementations;

import com.google.vr180.api.camerainterfaces.ViewfinderCaptureSource;
import com.google.vr180.common.logging.Log;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.VideoCapturer;

/** Basic implementation of ViewfinderCaptureSource that uses the first back-facing camera. */
public class AndroidViewfinderCaptureSource implements ViewfinderCaptureSource {
  private static final String TAG = "AndroidViewfinderCaptureSource";

  @Override
  public VideoCapturer getVideoCapturer() {
    CameraEnumerator cameraEnumerator = new Camera1Enumerator(false /* captureToTexture */);
    String[] deviceNames = cameraEnumerator.getDeviceNames();
    String cameraDeviceName = deviceNames.length > 0 ? deviceNames[0] : null;
    Log.i(TAG, "opening camera: " + cameraDeviceName);
    CameraVideoCapturer capturer =
        cameraEnumerator.createCapturer(cameraDeviceName, null /* eventsHandler */);
    if (capturer == null) {
      Log.e(TAG, "failed to open capturer");
    }
    return capturer;
  }
}
