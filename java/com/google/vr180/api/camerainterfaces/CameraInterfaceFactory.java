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

/** Interface to get implementations for camera control interfaces. */
public interface CameraInterfaceFactory {
  /** Gets a {@link BatteryStatusProvider} instance for the camera. */
  BatteryStatusProvider getBatteryStatusProvider();

  /** Gets a {@link CameraSettings} instance for the camera. */
  CameraSettings getCameraSettings();

  /** Gets a {@link CapabilitiesProvider} instance for the camera. */
  CapabilitiesProvider getCapabilitiesProvider();

  /** Gets a {@link CaptureManager} instance for the camera. */
  CaptureManager getCaptureManager();

  /** Gets a {@link MediaProvider} instance for the camera. */
  MediaProvider getMediaProvider();

  /** Gets a {@link NetworkManager} instance for the camera. */
  NetworkManager getNetworkManager();

  /** Gets a {@link HotspotManager} instance for the camera. */
  HotspotManager getHotspotManager();

  /** Gets a {@link MobileNetworkManager} instance for the camera. */
  MobileNetworkManager getMobileNetworkManager();

  /** Gets a {@link StorageStatusProvider} instance for the camera. */
  StorageStatusProvider getStorageStatusProvider();

  /** Gets a {@link TemperatureProvider} instance for the camera. */
  TemperatureProvider getTemperatureProvider();

  /** Gets a {@link UpdateManager} instance for the camera. */
  UpdateManager getUpdateManager();

  /** Gets a {@link DebugLogsProvider} instance for the camera. */
  DebugLogsProvider getDebugLogsProvider();

  /** Gets a {@link ViewfinderProvider} instance for the camera. */
  ViewfinderProvider getViewfinderProvider();

  /** Gets a {@link GravityVectorProvider} instance for the camera. */
  GravityVectorProvider getGravityVectorProvider();

  /** Gets a {@link FileProvider} instance for the camera. */
  FileProvider getFileProvider();

  /** Gets a {@link AudioVolumeManager} instance for the camera. */
  AudioVolumeManager getAudioVolumeManager();

  /** Gets a {@link ConnectionTester} instance for the camera. */
  ConnectionTester getConnectionTester();

  /** Gets the {@link PairingManager} for the camera. */
  PairingManager getPairingManager();

  /** Gets a {@link WakeManager} instance for the camera. */
  WakeManager getWakeManager();
}
