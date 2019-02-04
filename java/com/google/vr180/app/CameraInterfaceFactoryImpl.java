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
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.CapabilitiesProvider;
import com.google.vr180.api.camerainterfaces.CaptureManager;
import com.google.vr180.api.camerainterfaces.DebugLogsProvider;
import com.google.vr180.api.camerainterfaces.PairingManager;
import com.google.vr180.api.camerainterfaces.SslManager;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.api.camerainterfaces.TemperatureProvider;
import com.google.vr180.api.camerainterfaces.UpdateManager;
import com.google.vr180.api.camerainterfaces.ViewfinderCaptureSource;
import com.google.vr180.api.implementations.AndroidAudioVolumeManager;
import com.google.vr180.api.implementations.AndroidBatteryStatusProvider;
import com.google.vr180.api.implementations.AndroidConnectionTester;
import com.google.vr180.api.implementations.AndroidFileProvider;
import com.google.vr180.api.implementations.AndroidMediaProvider;
import com.google.vr180.api.implementations.AndroidMobileNetworkManager;
import com.google.vr180.api.implementations.AndroidNetworkManager;
import com.google.vr180.api.implementations.AndroidWakeManager;
import com.google.vr180.api.implementations.BaseCameraInterfaceFactory;
import com.google.vr180.api.implementations.CachedFileChecksumProvider;
import com.google.vr180.api.implementations.MemoryDebugLogsProvider;
import com.google.vr180.api.implementations.ViewfinderManager;
import com.google.vr180.api.implementations.WifiDirectHotspotManager;
import com.google.vr180.app.stubs.FakeTemperatureProvider;
import com.google.vr180.app.stubs.FakeUpdateManager;
import com.google.vr180.common.logging.MemoryLogger;
import com.google.vr180.common.wifi.WifiDirectManager;
import java.util.ArrayList;

/**
 * Implementation of CameraInterfaceFactory.
 *
 * <p> Stubs are used for {@link TemperatureProvider} and {@link UpdateManager}.
 *
 * <p> {@link DebugLogsProvider} is disabled by default. Using {@link MemoryDebugLogsProvider}
 * allows the companion app to collect camera logs for debugging purpose.
 */
public class CameraInterfaceFactoryImpl extends BaseCameraInterfaceFactory {

  public CameraInterfaceFactoryImpl(
      Context context,
      CapabilitiesProvider capabilitiesProvider,
      CaptureManager captureManager,
      StorageStatusProvider storageStatusProvider,
      MemoryLogger logger,
      SslManager sslManager,
      ViewfinderCaptureSource viewfinderCaptureSource,
      CameraSettings cameraSettings,
      StatusNotifier statusChangeNotifier,
      PairingManager pairingManager,
      int shutdownPercentage,
      int httpServerPort) {
    super(
        new AndroidAudioVolumeManager(context),
        new AndroidBatteryStatusProvider(context, statusChangeNotifier, shutdownPercentage),
        cameraSettings,
        capabilitiesProvider,
        new AndroidConnectionTester(),
        debugLogsRequest -> new ArrayList<>(),
        new AndroidFileProvider(context, storageStatusProvider),
        () -> null, // GravityVectorProvider is currently not used by VR180 camera app.
        new WifiDirectHotspotManager(WifiDirectManager.getInstance(context), statusChangeNotifier),
        captureManager,
        new AndroidMediaProvider(
            context,
            storageStatusProvider,
            new CachedFileChecksumProvider(
                context, new AndroidFileProvider(context, storageStatusProvider)),
            cameraSettings.getCameraCalibration(),
            statusChangeNotifier),
        new AndroidNetworkManager(context, httpServerPort, sslManager, statusChangeNotifier),
        new AndroidMobileNetworkManager(context, statusChangeNotifier),
        pairingManager,
        storageStatusProvider,
        new FakeTemperatureProvider(),
        new FakeUpdateManager(),
        new ViewfinderManager(context, captureManager, viewfinderCaptureSource),
        new AndroidWakeManager(context, cameraSettings));
  }
}
