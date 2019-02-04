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

package com.google.vr180.capture;

import android.os.Handler;
import android.os.Looper;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.common.logging.Log;

/** Helper class to periodically check if the storage is low */
public class FreeSpaceChecker {

  private static final String TAG = "FreeSpaceChecker";
  private static final long LOW_SPACE_THRESHOLD = 1024L * 1024 * 1024; // 1 GB
  private static final long LOW_SPACE_CHECK_PERIOD = 10_000; // 10 seconds
  private final StorageStatusProvider storageStatusProvider;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private LowSpaceCallback lowSpaceCallback;

  private final Runnable lowSpaceCheckRunnable =
      () -> {
        if (isLowSpace()) {
          lowSpaceCallback.onLowSpace();
        } else {
          scheduleLowSpaceCheck();
        }
      };

  /** Callback for low space. */
  public interface LowSpaceCallback {
    /** Called when space is low. */
    void onLowSpace();
  }

  public FreeSpaceChecker(StorageStatusProvider storageStatusProvider) {
    this.storageStatusProvider = storageStatusProvider;
  }

  public boolean isLowSpace() {
    long freeSpace = storageStatusProvider.getStorageStatus().getFreeSpace();
    Log.d(TAG, "Free space: " + freeSpace / (1024.0 * 1024 * 1024) + "GB");
    return freeSpace < LOW_SPACE_THRESHOLD;
  }

  /**
   * Start periodically check remaining space and call back when space is less than the
   * LOW_SPACE_THRESHOLD.
   *
   * Note: It does NOT properly handle the case when SD card insertion during recording.
   *
   * @param lowSpaceCallback The callback for low space.
   */
  public void scheduleRepeatingFreeSpaceCheck(LowSpaceCallback lowSpaceCallback) {
    this.lowSpaceCallback = lowSpaceCallback;
    scheduleLowSpaceCheck();
  }

  public void cancelRepeatingFreeSpaceCheck() {
    handler.removeCallbacks(lowSpaceCheckRunnable);
    this.lowSpaceCallback = null;
  }

  private void scheduleLowSpaceCheck() {
    Log.d(TAG, "scheduleLowSpaceCheck");
    handler.postDelayed(lowSpaceCheckRunnable, LOW_SPACE_CHECK_PERIOD);
  }
}
