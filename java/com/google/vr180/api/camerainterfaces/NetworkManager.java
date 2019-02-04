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

import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiNetworkConfiguration;
import com.google.vr180.CameraApi.CameraStatus.HttpServerStatus;
import com.google.vr180.CameraApi.CameraStatus.WifiStatus;
import com.google.vr180.CameraApi.WifiAccessPointInfo;
import com.google.vr180.CameraApi.WifiNetworkStatus;

/**
 * Interface for managing the network connectivity of the camera.
 */
public interface NetworkManager {
  /** Returns the current network info for the camera. */
  HttpServerStatus getHttpServerStatus();

  /** Returns the SSID of the Wifi network to which the camera is currently connected. */
  String getActiveWifiSsid();

  /** Returns the camera's Wifi status. */
  WifiNetworkStatus getWifiNetworkStatus();

  /** Starts trying to connect to the provided wifi access point. */
  void startWifiConnect(WifiAccessPointInfo accessPointInfo);

  /** Updates network configuration with the given request. */
  void updateWifiNetworkConfiguration(WifiNetworkConfiguration wifiNetworkConfiguration);

  /** Returns status of the wifi adapter (including the supplicant state). */
  WifiStatus getWifiStatus();
}
