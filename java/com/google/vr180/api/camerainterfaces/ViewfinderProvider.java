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

package com.google.vr180.api.camerainterfaces;

import com.google.vr180.CameraApi.CameraApiRequest.WebRtcRequest;
import com.google.vr180.CameraApi.WebRtcSessionDescription;
import java.io.IOException;

/**
 * Interface for fetching viewfinder data.
 */
public interface ViewfinderProvider {
  /**
   * Handles a request to create or update a webrtc session. Returns a session description of the
   * created session.
   * @throws IOException if there is an error handling the request.
   */
  WebRtcSessionDescription startViewfinderWebrtc(WebRtcRequest request) throws IOException;

  /**
   * Handles a request to stop a webrtc session.
   */
  void stopViewfinderWebrtc(WebRtcRequest request);
}
