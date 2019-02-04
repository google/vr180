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

import android.app.Application;
import com.google.vr180.api.camerainterfaces.CapabilitiesProvider;
import com.google.vr180.app.stubs.Emulator;
import com.google.vr180.app.stubs.EmulatorCapabilitiesProvider;
import com.google.vr180.app.stubs.EmulatorDeviceInfo;
import com.google.vr180.app.stubs.EmulatorProjectionMetadataProvider;
import com.google.vr180.capture.camera.CameraConfigurator;
import com.google.vr180.capture.camera.DefaultPreviewConfigProvider;
import com.google.vr180.capture.camera.PreviewConfigProvider;
import com.google.vr180.common.InstanceMap;
import com.google.vr180.common.logging.Log;
import com.google.vr180.device.DeviceInfo;
import com.google.vr180.device.Hardware;
import com.google.vr180.media.metadata.ProjectionMetadataProvider;

/** Performs application-level initialization. */
public class CameraApplication extends Application {

  private static final String LOG_TAG = "VR180";

  @Override
  public void onCreate() {
    super.onCreate();
    Log.setTag(LOG_TAG);
    populateInstanceMap();
  }

  private void populateInstanceMap() {
    // Device-specific configurations.
    InstanceMap.put(DeviceInfo.class, new EmulatorDeviceInfo());
    InstanceMap.put(CameraConfigurator.class, new CameraConfigurator());
    InstanceMap.put(PreviewConfigProvider.class, new DefaultPreviewConfigProvider());
    InstanceMap.put(CapabilitiesProvider.class, new EmulatorCapabilitiesProvider());
    InstanceMap.put(ProjectionMetadataProvider.class, new EmulatorProjectionMetadataProvider(this));
    InstanceMap.put(Hardware.class, new Emulator());

    // Global camera object.
    InstanceMap.put(Camera.class, new Camera(this));
  }
}
