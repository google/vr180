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

import com.google.vr180.CameraApi.CameraApiRequest.ConnectionTestRequest;
import com.google.vr180.CameraApi.CameraApiResponse.ConnectionTestResponse;

/** Interface to implement requests to test the camera's internet connectivity. */
public interface ConnectionTester {
  /**
   * Attempts to fetch from the url specified in the request and returns the response info.
   *
   * @param request Details of the connection test to run.
   * @return The results of the connection test.
   */
  ConnectionTestResponse testConnection(ConnectionTestRequest request);
}
