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

package com.google.vr180.api.implementations;


import com.google.vr180.api.camerainterfaces.AudioVolumeManager;
import com.google.vr180.api.camerainterfaces.BatteryStatusProvider;
import com.google.vr180.api.camerainterfaces.CameraInterfaceFactory;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.CapabilitiesProvider;
import com.google.vr180.api.camerainterfaces.CaptureManager;
import com.google.vr180.api.camerainterfaces.ConnectionTester;
import com.google.vr180.api.camerainterfaces.DebugLogsProvider;
import com.google.vr180.api.camerainterfaces.FileProvider;
import com.google.vr180.api.camerainterfaces.GravityVectorProvider;
import com.google.vr180.api.camerainterfaces.HotspotManager;
import com.google.vr180.api.camerainterfaces.MediaProvider;
import com.google.vr180.api.camerainterfaces.MobileNetworkManager;
import com.google.vr180.api.camerainterfaces.NetworkManager;
import com.google.vr180.api.camerainterfaces.PairingManager;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.api.camerainterfaces.TemperatureProvider;
import com.google.vr180.api.camerainterfaces.UpdateManager;
import com.google.vr180.api.camerainterfaces.ViewfinderProvider;
import com.google.vr180.api.camerainterfaces.WakeManager;

/** Implementation of CameraInterfaceFactory that uses injected components. */
public class BaseCameraInterfaceFactory implements CameraInterfaceFactory {

  private final AudioVolumeManager audioVolumeManager;
  private final BatteryStatusProvider batteryStatusProvider;
  private final CameraSettings cameraSettings;
  private final CapabilitiesProvider capabilitiesProvider;
  private final ConnectionTester connectionTester;
  private final DebugLogsProvider debugLogsProvider;
  private final FileProvider fileProvider;
  private final GravityVectorProvider gravityVectorProvider;
  private final HotspotManager hotspotManager;
  private final CaptureManager captureManager;
  private final MediaProvider mediaProvider;
  private final NetworkManager networkManager;
  private final MobileNetworkManager mobileNetworkManager;
  private final PairingManager pairingManager;
  private final StorageStatusProvider storageStatusProvider;
  private final TemperatureProvider temperatureProvider;
  private final UpdateManager updateManager;
  private final ViewfinderProvider viewfinderProvider;
  private final WakeManager wakeManager;

  /**
   * Constructor of BaseCameraInterfaceFactory with injected components. Note that null values are
   * not allowed. All managers/providers must have real or stub implementations.
   */
  public BaseCameraInterfaceFactory(
      AudioVolumeManager audioVolumeManager,
      BatteryStatusProvider batteryStatusProvider,
      CameraSettings cameraSettings,
      CapabilitiesProvider capabilitiesProvider,
      ConnectionTester connectionTester,
      DebugLogsProvider debugLogsProvider,
      FileProvider fileProvider,
      GravityVectorProvider gravityVectorProvider,
      HotspotManager hotspotManager,
      CaptureManager captureManager,
      MediaProvider mediaProvider,
      NetworkManager networkManager,
      MobileNetworkManager mobileNetworkManager,
      PairingManager pairingManager,
      StorageStatusProvider storageStatusProvider,
      TemperatureProvider temperatureProvider,
      UpdateManager updateManager,
      ViewfinderProvider viewfinderProvider,
      WakeManager wakeManager) {
    this.audioVolumeManager = audioVolumeManager;
    this.batteryStatusProvider = batteryStatusProvider;
    this.cameraSettings = cameraSettings;
    this.capabilitiesProvider = capabilitiesProvider;
    this.connectionTester = connectionTester;
    this.debugLogsProvider = debugLogsProvider;
    this.fileProvider = fileProvider;
    this.gravityVectorProvider = gravityVectorProvider;
    this.hotspotManager = hotspotManager;
    this.captureManager = captureManager;
    this.mediaProvider = mediaProvider;
    this.networkManager = networkManager;
    this.mobileNetworkManager = mobileNetworkManager;
    this.pairingManager = pairingManager;
    this.storageStatusProvider = storageStatusProvider;
    this.temperatureProvider = temperatureProvider;
    this.updateManager = updateManager;
    this.viewfinderProvider = viewfinderProvider;
    this.wakeManager = wakeManager;
  }

  @Override
  public AudioVolumeManager getAudioVolumeManager() {
    return audioVolumeManager;
  }

  @Override
  public BatteryStatusProvider getBatteryStatusProvider() {
    return batteryStatusProvider;
  }

  @Override
  public CameraSettings getCameraSettings() {
    return cameraSettings;
  }

  @Override
  public CapabilitiesProvider getCapabilitiesProvider() {
    return capabilitiesProvider;
  }

  @Override
  public CaptureManager getCaptureManager() {
    return captureManager;
  }

  @Override
  public ConnectionTester getConnectionTester() {
    return connectionTester;
  }

  @Override
  public DebugLogsProvider getDebugLogsProvider() {
    return debugLogsProvider;
  }

  @Override
  public FileProvider getFileProvider() {
    return fileProvider;
  }

  @Override
  public GravityVectorProvider getGravityVectorProvider() {
    return gravityVectorProvider;
  }

  @Override
  public HotspotManager getHotspotManager() {
    return hotspotManager;
  }

  @Override
  public MediaProvider getMediaProvider() {
    return mediaProvider;
  }

  @Override
  public MobileNetworkManager getMobileNetworkManager() {
    return mobileNetworkManager;
  }

  @Override
  public NetworkManager getNetworkManager() {
    return networkManager;
  }

  @Override
  public PairingManager getPairingManager() {
    return pairingManager;
  }

  @Override
  public StorageStatusProvider getStorageStatusProvider() {
    return storageStatusProvider;
  }

  @Override
  public TemperatureProvider getTemperatureProvider() {
    return temperatureProvider;
  }

  @Override
  public UpdateManager getUpdateManager() {
    return updateManager;
  }

  @Override
  public ViewfinderProvider getViewfinderProvider() {
    return viewfinderProvider;
  }

  @Override
  public WakeManager getWakeManager() {
    return wakeManager;
  }
}
