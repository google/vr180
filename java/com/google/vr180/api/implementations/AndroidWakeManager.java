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

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import com.google.vr180.CameraApi.SleepConfiguration;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.WakeManager;
import com.google.vr180.common.logging.Log;

/** Implementation of WakeManager that uses android WakeLock. */
public class AndroidWakeManager implements WakeManager {
  private static final String TAG = "AndroidWakeManager";
  private static final int SECONDS_TO_MILLISECONDS = 1000;
  private static final int DEFAULT_SLEEP_TIME_SECONDS = 30;
  private final PowerManager powerManager;

  private final WakeLock wakeLock;
  private final CameraSettings cameraSettings;

  private volatile boolean enabled = true;

  public AndroidWakeManager(Context context, CameraSettings cameraSettings) {
    this.cameraSettings = cameraSettings;
    powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    wakeLock =
        powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
  }

  /** Handles a ping requesting that the camera wake up. */
  @Override
  public void wakePing() {
    if (!enabled) {
      Log.d(TAG, "AndroidWakeManager is disabled.");
      return;
    }
    int wakeTimeSeconds = DEFAULT_SLEEP_TIME_SECONDS;
    SleepConfiguration sleepConfig = cameraSettings.getSleepConfiguration();
    if (sleepConfig != null) {
      wakeTimeSeconds = sleepConfig.getWakeTimeSeconds();
    }
    Log.d(TAG, "wakePing " + wakeTimeSeconds);
    wakeLock.acquire(wakeTimeSeconds * SECONDS_TO_MILLISECONDS);
  }

  /**
   * Set whether the AndroidWakeManager is enabled. Default is enabled. When disabled, wakePing() is
   * ignored.
   */
  @Override
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
