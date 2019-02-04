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

import com.google.vr180.CameraApi.CameraStatus.MobileNetworkStatus;

/**
 * Interface to provide information about the LTE / Mobile Data network connectivity of the phone,
 * and allow the user to manage the connection.
 */
public interface MobileNetworkManager {
  /** Gets information about the mobile data network connectivity. */
  MobileNetworkStatus getMobileNetworkStatus();

  /** Sets whether the mobile network is enabled. */
  void setMobileNetworkEnabled(boolean enabled);
}
