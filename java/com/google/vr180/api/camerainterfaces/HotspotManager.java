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

import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiHotspotConfiguration.ChannelPreference;
import com.google.vr180.CameraApi.WifiAccessPointInfo;

/** Interface for configuring an Access Point on the camera. */
public interface HotspotManager {
  /**
   * Returns the connection details about the camera's access point if it is available.
   * Returns null if the camera is not hosting a Wifi hotspot (or it is not ready).
   */
  WifiAccessPointInfo getHotspotAccessPointInfo();

  /**
   * Start hosting a camera wifi hotspot.
   * Ignore if the hotspot is already started.
   *
   * @param channelPreference The preferred WiFi channel settings of the client. Optional.
   */
  void startHotspot(ChannelPreference channelPreference);

  /**
   * Instructs the camera to stop the wifi hotspot.
   * Ignore if the hotspot is already stopped.
   */
  void shutdownHotspot();

  /**
   * Returns the IP address of the camera in the hotspot network.
   */
  String getIpAddress();
}
