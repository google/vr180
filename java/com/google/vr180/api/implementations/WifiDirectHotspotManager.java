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

import android.os.Handler;
import android.os.Looper;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiHotspotConfiguration.ChannelPreference;
import com.google.vr180.CameraApi.WifiAccessPointInfo;
import com.google.vr180.api.camerainterfaces.HotspotManager;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import com.google.vr180.common.logging.Log;
import com.google.vr180.common.wifi.WifiDirectManager;
import com.google.vr180.common.wifi.WifiNetworkManager;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of HotspotManager that uses the Wifi-Direct android APIs to create an access
 * point.
 */
public class WifiDirectHotspotManager implements HotspotManager {
  private static final String TAG = "WifiDirectHotspotManager";
  private static AtomicBoolean wifiStarted = new AtomicBoolean(false);
  private final WifiDirectManager wifiDirectManager;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private volatile WifiAccessPointInfo accessPointInfo = null;
  private volatile String wifiDirectIpAddress = null;
  private StatusNotifier notifier;

  public WifiDirectHotspotManager(WifiDirectManager wifiDirectManager, StatusNotifier notifier) {
    this.wifiDirectManager = wifiDirectManager;
    this.notifier = notifier;
  }

  @Override
  public WifiAccessPointInfo getHotspotAccessPointInfo() {
    return accessPointInfo;
  }

  @Override
  public String getIpAddress() {
    return wifiDirectManager.isConnected() ? wifiDirectIpAddress : null;
  }

  @Override
  public void startHotspot(ChannelPreference channelPreference) {
    if (wifiStarted.compareAndSet(false, true)) {
      Log.d(TAG, "Starting Wifi Access Point");
      mainHandler.post(() -> startWifiAp(channelPreference));
    } else {
      Log.d(TAG, "Wifi access point already started.");
    }
  }

  @Override
  public void shutdownHotspot() {
    if (wifiStarted.compareAndSet(true, false)) {
      Log.d(TAG, "Stop Wifi Access Point");
      wifiDirectManager.releaseNetwork();
      accessPointInfo = null;
      wifiDirectIpAddress = null;
    } else {
      Log.d(TAG, "Wifi access point already stopped.");
    }
  }

  private void startWifiAp(ChannelPreference channelPreference) {
    wifiDirectManager.createHotspot(
        channelPreference,
        new WifiNetworkManager.Listener() {
          @Override
          public void onNetworkInfoAvailable(
              String ssid,
              String passphrase,
              String groupOwnerIpAddress,
              String groupOwnerMacAddress) {
            accessPointInfo =
                WifiAccessPointInfo.newBuilder()
                    .setSsid(ssid)
                    .setPassword(passphrase)
                    .setOwnerMacAddress(groupOwnerMacAddress)
                    .build();
            wifiDirectIpAddress = groupOwnerIpAddress;
            notifier.notifyStatusChanged();
            Log.d(TAG, "Wifi Direct onNetworkInfoAvailable");
          }

          @Override
          public void onConnect() {
            notifier.notifyStatusChanged();
            Log.d(TAG, "Wifi Direct onConnect");
          }

          @Override
          public void onDisconnect() {
            notifier.notifyStatusChanged();
            Log.d(TAG, "Wifi Direct onDisconnect");
          }

          @Override
          public void onError(Exception e) {
            Log.e(TAG, "Wifi Direct onError", e);
            wifiDirectManager.releaseNetwork();
            accessPointInfo = null;
            wifiStarted.set(false);
          }
        });
  }
}
