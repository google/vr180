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

package com.google.vr180.app.stubs;

import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.UpdateConfiguration;
import com.google.vr180.CameraApi.CameraCapabilities;
import com.google.vr180.CameraApi.CameraStatus.UpdateStatus;
import com.google.vr180.api.camerainterfaces.UpdateManager;
import java.io.File;

/**
 * Stub implementation of the UpdateManager.
 *
 * Customize this class if OEM-specific OTA update is supported and update
 * {@link CameraCapabilities.UpdateCapability} accordingly.
 *
 * Leave it as is if OTA is not supported.
 */
public class FakeUpdateManager implements UpdateManager {
  private static final String VERSION = "1";

  public FakeUpdateManager() {}

  @Override
  public UpdateStatus getUpdateStatus() {
    return UpdateStatus.newBuilder().setFirmwareVersion(VERSION).build();
  }

  @Override
  public void applyUpdate(UpdateConfiguration updateConfiguration) {}

  @Override
  public void handleUpdate(String updateName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public File getUpdateDirectory() {
    throw new UnsupportedOperationException();
  }
}
