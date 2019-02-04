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

package com.google.vr180.app;

import android.content.Context;
import com.google.vr180.CameraApi.CameraCapabilities;
import com.google.vr180.CameraApi.CameraStatus;
import com.google.vr180.CameraInternalApi.CameraInternalStatus;
import com.google.vr180.CameraInternalApi.CameraState;
import com.google.vr180.api.CameraApiClient;
import com.google.vr180.api.CameraApiClient.CameraApiException;
import com.google.vr180.api.CameraCore;
import com.google.vr180.api.CameraCore.CameraCoreListener;
import com.google.vr180.api.internal.CameraInternalApiClient;
import com.google.vr180.common.logging.Log;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import java.io.IOException;

/**
 * A class representing the camera on the device which provides clients for requests and
 * observables for camera status.
 */
public class Camera {

  private static final String TAG = "Camera";

  private final CameraCore cameraCore;
  private final CameraApiClient cameraApiClient;
  private final CameraInternalApiClient cameraInternalApiClient;
  private final CameraCapabilities capabilities;

  private final BehaviorSubject<CameraStatus> cameraStatusSubject = BehaviorSubject.create();
  private final BehaviorSubject<CameraInternalStatus> cameraInternalStatusObservable =
      BehaviorSubject.create();
  private final BehaviorSubject<Boolean> cameraInternalErrorObservable = BehaviorSubject.create();

  public Camera(Context context) {
    cameraCore = new CameraController(context);
    cameraApiClient = new CameraApiClient((request, priority) -> cameraCore.doRequest(request));
    cameraInternalApiClient = new CameraInternalApiClient(cameraCore);

    try {
      capabilities = cameraApiClient.getCapabilities();
    } catch (CameraApiException | IOException e) {
      Log.e(TAG, "Failed to get camera capabilities");
      throw new RuntimeException(e);
    }
    refreshStatus();
    refreshInternalStatus();

    cameraCore.setListener(
        new CameraCoreListener() {
          @Override
          public void onStatusChanged() {
            refreshStatus();
          }

          @Override
          public void onInternalStatusChanged() {
            refreshInternalStatus();
          }

          @Override
          public void onInternalError() {
            cameraInternalErrorObservable.onNext(true);
          }
        });
  }

  /**
   * Returns an Api Client for the camera which chooses the best transport mechanism for the
   * request.
   */
  public CameraApiClient getCameraApiClient() {
    return cameraApiClient;
  }

  public CameraInternalApiClient getInternalApiClient() {
    return cameraInternalApiClient;
  }

  public CameraCapabilities getCameraCapabilities() {
    return capabilities;
  }

  public Observable<CameraStatus> getCameraStatusObservable() {
    return cameraStatusSubject;
  }

  public Observable<CameraInternalStatus> getInternalStatusObservable() {
    return cameraInternalStatusObservable;
  }

  public Observable<Boolean> getInternalErrorNotificationObservable() {
    return cameraInternalErrorObservable;
  }

  public synchronized CameraStatus getCameraStatus() {
    return cameraStatusSubject.getValue();
  }

  public synchronized CameraState getCameraState() {
    return cameraInternalStatusObservable.getValue().getCameraState();
  }

  public synchronized void onResume() {
    cameraInternalApiClient.setCameraState(CameraState.DEFAULT_ACTIVE);
  }

  public synchronized void onPause() {
    cameraInternalApiClient.setCameraState(CameraState.INACTIVE);
  }

  private synchronized void refreshStatus() {
    try {
      CameraStatus status = cameraApiClient.getStatus();
      if (status != null) {
        cameraStatusSubject.onNext(status);
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to refresh camera status.", e);
    }
  }

  private synchronized void refreshInternalStatus() {
    cameraInternalStatusObservable.onNext(cameraInternalApiClient.getInternalStatus());
  }
}
