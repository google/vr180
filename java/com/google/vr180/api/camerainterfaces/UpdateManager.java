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

import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.UpdateConfiguration;
import com.google.vr180.CameraApi.CameraStatus.UpdateStatus;
import java.io.File;
import java.io.IOException;

/** Handles applying firmware updates to the camera. */
public interface UpdateManager {

  /** Fetches the update status of the camera. */
  UpdateStatus getUpdateStatus();

  /** Instructs the camera to start applying firmware update to the specified version. */
  void applyUpdate(UpdateConfiguration updateConfiguration);

  /** Returns the directory to use for receiving push updates. */
  File getUpdateDirectory();

  /**
   * Receives a completed push update. The update will be stored in a file named as specified in
   * updateName in the folder specified by {@link UpdateManager#getUpdateDirectory}.
   *
   * <p>This method should NOT block for an extended length of time, since it may be called from the
   * request handler for the update push.
   *
   * @param updateName The identifier of the new update file, which is stored in the folder returned
   *     from getUpdateDirectory()
   * @throws UnsupportedOperationException if the camera doesn't support push updates.
   */
  void handleUpdate(String updateName) throws IOException;
}
