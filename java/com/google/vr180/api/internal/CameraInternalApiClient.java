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

package com.google.vr180.api.internal;

import android.opengl.GLSurfaceView;
import com.google.vr180.CameraInternalApi.CameraInternalApiRequest;
import com.google.vr180.CameraInternalApi.CameraInternalStatus;
import com.google.vr180.CameraInternalApi.CameraState;
import com.google.vr180.api.CameraCore;

/**
 * A client sending camera internal API requests.
 */
public class CameraInternalApiClient {

  private final CameraCore cameraCore;

  public CameraInternalApiClient(CameraCore cameraCore) {
    this.cameraCore = cameraCore;
  }

  public void startPairing() {
    cameraCore.doInternalRequest(
        CameraInternalApiRequest.newBuilder()
            .setRequestType(CameraInternalApiRequest.RequestType.START_PAIRING)
            .build());
  }

  public void confirmPairing() {
    cameraCore.doInternalRequest(
        CameraInternalApiRequest.newBuilder()
            .setRequestType(CameraInternalApiRequest.RequestType.CONFIRM_PAIRING)
            .build());
  }

  public void cancelPairing() {
    cameraCore.doInternalRequest(
        CameraInternalApiRequest.newBuilder()
            .setRequestType(CameraInternalApiRequest.RequestType.CANCEL_PAIRING)
            .build());
  }

  public void setCameraState(CameraState cameraState) {
    cameraCore.doInternalRequest(
        CameraInternalApiRequest.newBuilder()
            .setRequestType(CameraInternalApiRequest.RequestType.CONFIGURE)
            .setConfigurationRequest(
                CameraInternalApiRequest.ConfigurationRequest.newBuilder()
                    .setCameraState(cameraState)
                    .build())
            .build());
  }

  public CameraInternalStatus getInternalStatus() {
    return
        cameraCore
            .doInternalRequest(
                CameraInternalApiRequest.newBuilder()
                    .setRequestType(CameraInternalApiRequest.RequestType.INTERNAL_STATUS)
                    .build())
            .getInternalStatus();
  }

  public void setViewfinderView(GLSurfaceView viewfinderView) {
    cameraCore.setViewfinderView(viewfinderView);
  }
}
