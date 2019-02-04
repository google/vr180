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

package com.google.vr180.common.wifi;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import com.google.vr180.common.logging.Log;

/** Common functionality for wifi network connectivity with the camera. */
public class WifiNetworkManager {
  /** Operation failed because another wifi operation is in progress. */
  public static class WifiBusyException extends Exception {}

  /** Operation failed because wifi is disabled. */
  public static class WifiDisabledException extends Exception {}

  /** Operation failed when attempting to configure wifi network. */
  public static class WifiConfigurationException extends Exception {}

  /** Operation failed because of a timeout connecting to wifi. */
  public static class WifiTimeoutException extends Exception {}

  /** Disconnected from wifi network after we successfully connected. */
  public static class WifiDisconnectException extends Exception {}

  /** VPN connection detected. */
  public static class VpnConnectedException extends Exception {}

  /** Authenticating with the wifi network failed. */
  public static class WifiAuthenticationException extends Exception {}

  /** Wifi Direct not supported. */
  public static class WifiDirectNotSupportedException extends Exception {}

  /** Wifi Direct disabled. */
  public static class WifiDirectDisabledException extends Exception {}

  /**
   * Listener interface to allow consumers of this class to be notified of network connectivity
   * changes.
   */
  public interface Listener {
    /** The network configuration info is now available. */
    void onNetworkInfoAvailable(
        String ssid, String passphrase, String groupOwnerIpAddress, String groupOwnerMacAddress);

    /** The target network has been connected and may now be used. */
    void onConnect();

    /** The target network disconnected unexpectedly while in use. */
    void onDisconnect();

    /** An error occurred while connecting to the target network. */
    void onError(Exception e);
  }

  private static final String TAG = "WifiNetworkManager";
  protected static final long CONNECTION_TIMEOUT_MILLIS = 30_000;

  protected final Context context;
  protected final WifiManager wifiManager;
  protected final ConnectivityManager connectivityManager;
  protected final WifiLock wifiLock;

  public WifiNetworkManager(Context context) {
    this.context = context;
    wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    wifiLock =
        wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF, "VrCamera " + getClass().getSimpleName());
    // We should only need a single lock.
    wifiLock.setReferenceCounted(false);
  }

  /** Returns whether or not wifi is enabled. */
  public boolean isWifiEnabled() {
    return wifiManager.isWifiEnabled();
  }

  /** Return whether or not this device supports 5GHz networks. */
  public boolean is5GHzBandSupported() {
    if (Build.VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
      return false;
    }

    return wifiManager.is5GHzBandSupported();
  }

  /** Returns the currently connected wifi ssid, or null if no network is connected. */
  public String getActiveSsid() {
    WifiInfo activeWifiInfo = wifiManager.getConnectionInfo();
    if (activeWifiInfo != null) {
      return activeWifiInfo.getSSID();
    }

    return null;
  }

  public boolean isVpnConnected() {
    // connectivityManager is null in some tests.
    if (connectivityManager == null) {
      return false;
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      // For Android versions before Lollipop:
      // VpnService.prepare() returns null if VPN is already connected
      // see https://developer.android.com/reference/android/net/VpnService.html
      Intent intent = VpnService.prepare(context);
      if (intent == null) {
        Log.i(TAG, "VPN is connected");
        return true;
      } else {
        return false;
      }
    }

    Network[] networks = connectivityManager.getAllNetworks();
    if (networks == null) {
      return false;
    }
    for (int i = 0; i < networks.length; i++) {
      NetworkCapabilities capabilities =
          connectivityManager.getNetworkCapabilities(networks[i]);
      NetworkInfo info = connectivityManager.getNetworkInfo(networks[i]);
      if (capabilities == null || info == null) {
        // Underlying networks changed since calling getAllNetworks.
        continue;
      }

      if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
          && info.isConnectedOrConnecting()) {
        Log.i(TAG, "Connected Network with TRANSPORT_VPN: " + networks[i] + " " + info);
        return true;
      }
    }
    return false;
  }
}
