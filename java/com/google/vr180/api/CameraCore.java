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

package com.google.vr180.api;

import android.opengl.GLSurfaceView;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraInternalApi.CameraInternalApiRequest;
import com.google.vr180.CameraInternalApi.CameraInternalApiResponse;

/**
 * A generic interface to execute commands on the camera.
 */
public interface CameraCore {
  /** Performs camera api request. */
  CameraApiResponse doRequest(CameraApiRequest request);
  /** Performs camera internal api request. */
  CameraInternalApiResponse doInternalRequest(CameraInternalApiRequest request);
  /** Sets the GLSurfaceView for view finder. */
  void setViewfinderView(GLSurfaceView viewfinderView);
  /** Sets the listener for camera status changes. */
  void setListener(CameraCoreListener listener);

  /**
   * Listener for camera status changes.
   */
  interface CameraCoreListener {
    /** Called when the client needs to refresh camera status. */
    void onStatusChanged();
    /** Called when the client needs to refresh camera internal status. */
    void onInternalStatusChanged();
    /** Called when there is an internal error not reported in status.*/
    void onInternalError();
  }
}
