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

import android.support.annotation.Nullable;
import com.google.vr180.CameraInternalApi.CameraInternalApiRequest;
import com.google.vr180.CameraInternalApi.CameraInternalApiResponse;
import com.google.vr180.CameraInternalApi.CameraInternalApiResponse.ResponseStatus;
import com.google.vr180.CameraInternalApi.CameraInternalApiResponse.ResponseStatus.StatusCode;
import com.google.vr180.api.camerainterfaces.PairingManager;

/** Handler of camera internal API requests. */
public class CameraInternalApiHandler {

  private final PairingManager pairingManager;
  private final CameraInternalStatusManager cameraInternalStatusManager;

  public CameraInternalApiHandler(
      @Nullable PairingManager pairingManager,
      CameraInternalStatusManager cameraInternalStatusManager) {
    this.pairingManager = pairingManager;
    this.cameraInternalStatusManager = cameraInternalStatusManager;
  }

  /** Handles camera internal API requests. */
  public CameraInternalApiResponse handleInternalRequest(
      CameraInternalApiRequest request) {
    switch (request.getRequestType()) {
      case UNKNOWN:
        return getInvalidResponse();
      case INTERNAL_STATUS:
        return CameraInternalApiResponse.newBuilder()
            .setResponseStatus(ResponseStatus.newBuilder().setStatusCode(StatusCode.OK).build())
            .setInternalStatus(cameraInternalStatusManager.getCameraInternalStatus())
            .build();
      case START_PAIRING:
        if (pairingManager != null) {
          pairingManager.startPairing();
          return getOkResponse();
        } else {
          return getNotSupportedResponse();
        }
      case CONFIRM_PAIRING:
        if (pairingManager != null) {
          pairingManager.confirmPairing();
          return getOkResponse();
        } else {
          return getNotSupportedResponse();
        }
      case CANCEL_PAIRING:
        if (pairingManager != null) {
          pairingManager.stopPairing();
          return getOkResponse();
        } else {
          return getNotSupportedResponse();
        }
      case CONFIGURE:
        if (!request.hasConfigurationRequest()) {
          return getInvalidResponse();
        }
        CameraInternalApiRequest.ConfigurationRequest configurationRequest =
            request.getConfigurationRequest();
        if (configurationRequest.hasCameraState()) {
          cameraInternalStatusManager.onCameraStateChanged(configurationRequest.getCameraState());
        }
        return getOkResponse();
    }
    return getInvalidResponse();
  }

  private static CameraInternalApiResponse getOkResponse() {
    return CameraInternalApiResponse.newBuilder()
        .setResponseStatus(ResponseStatus.newBuilder().setStatusCode(StatusCode.OK).build())
        .build();
  }

  private static CameraInternalApiResponse getInvalidResponse() {
    return CameraInternalApiResponse.newBuilder()
        .setResponseStatus(
            ResponseStatus.newBuilder().setStatusCode(StatusCode.INVALID_REQUEST).build())
        .build();
  }

  private static CameraInternalApiResponse getNotSupportedResponse() {
    return CameraInternalApiResponse.newBuilder()
        .setResponseStatus(
            ResponseStatus.newBuilder().setStatusCode(StatusCode.NOT_SUPPORTED).build())
        .build();
  }
}
