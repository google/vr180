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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import com.google.vr180.CameraApi.CameraStatus.BatteryStatus;
import com.google.vr180.CameraApi.CameraStatus.BatteryStatus.BatteryWarningLevel;
import com.google.vr180.api.camerainterfaces.BatteryStatusProvider;
import com.google.vr180.api.camerainterfaces.StatusNotifier;

/** Implementation of BatteryStatusProvider that uses Android APIs. */
public class AndroidBatteryStatusProvider implements BatteryStatusProvider {
  private final Context context;
  private final StatusNotifier notifier;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private BatteryWarningLevel warningLevel = BatteryWarningLevel.OK;
  private final int shutdownPercentage;

  /**
   * @param shutdownPercentage is the point at which the camera cannot take more photos or videos.
   *     This value must be less than the low battery value.
   */
  public AndroidBatteryStatusProvider(
      Context context, StatusNotifier notifier, int shutdownPercentage) {
    this.context = context;
    this.notifier = notifier;
    this.shutdownPercentage = shutdownPercentage;
    mainHandler.post(() -> registerWarningReceiver());
  }

  @Override
  public BatteryStatus getBatteryStatus() {
    Intent batteryStatus =
        context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    if (batteryStatus == null) {
      // This happens in robolectric tests.
      return BatteryStatus.newBuilder().build();
    }

    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

    int chargingState = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, 1);
    int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
    int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);

    return BatteryStatus.newBuilder()
        .setChargingState(BatteryStatus.ChargingState.forNumber(chargingState))
        .setBatteryPercentage((int) (100 * level / (float) scale))
        .setBatteryVoltage(voltage)
        .setBatteryTemperature(temperature)
        .setWarningLevel(warningLevel)
        .build();
  }

  private void registerWarningReceiver() {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(Intent.ACTION_BATTERY_LOW);
    intentFilter.addAction(Intent.ACTION_BATTERY_OKAY);
    context.registerReceiver(new BatteryLevelReceiver(), intentFilter);
  }

  private class BatteryLevelReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equals(Intent.ACTION_BATTERY_LOW)) {
        if (getBatteryStatus().getBatteryPercentage() <= shutdownPercentage) {
          warningLevel = BatteryWarningLevel.SHUTDOWN;
        } else {
          warningLevel = BatteryWarningLevel.LOW;
        }
        notifier.notifyStatusChanged();
      } else if (intent.getAction().equals(Intent.ACTION_BATTERY_OKAY)) {
        warningLevel = BatteryWarningLevel.OK;
        notifier.notifyStatusChanged();
      }
    }
  }
}
