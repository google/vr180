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

import com.google.common.base.Optional;
import com.google.vr180.CameraApi.CameraStatus.StorageStatus;

/** Provides information about the storage for the camera. */
public interface StorageStatusProvider {

  /** Gets the storage status for the camera. */
  StorageStatus getStorageStatus();

  /** Gets the base path to internal storage. Returns empty if no internal storage. */
  Optional<String> getInternalStoragePath();

  /** Gets the base path to external SD card. Returns empty if no external SD card. */
  Optional<String> getExternalStoragePath();

  /**
   * Gets the path to write media files to.
   *
   * Based on the implementation, the path could be different for with and without SD card.
   */
  Optional<String> getWriteBasePath();

  /** Returns whether the path is a valid path in the media folder. */
  boolean isValidPath(String path);
}
