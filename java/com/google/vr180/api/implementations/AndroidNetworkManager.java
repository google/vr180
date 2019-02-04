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
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiNetworkConfiguration;
import com.google.vr180.CameraApi.CameraStatus.HttpServerStatus;
import com.google.vr180.CameraApi.CameraStatus.WifiStatus;
import com.google.vr180.CameraApi.WifiAccessPointInfo;
import com.google.vr180.CameraApi.WifiConnectionErrorType;
import com.google.vr180.CameraApi.WifiNetworkStatus;
import com.google.vr180.CameraApi.WifiScanResult;
import com.google.vr180.api.camerainterfaces.NetworkManager;
import com.google.vr180.api.camerainterfaces.SslManager;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import com.google.vr180.common.logging.Log;
import com.google.vr180.common.wifi.WifiClientManager;
import com.google.vr180.common.wifi.WifiNetworkManager;
import com.google.vr180.common.wifi.WifiNetworkManager.WifiAuthenticationException;
import com.google.vr180.common.wifi.WifiNetworkManager.WifiConfigurationException;
import com.google.vr180.common.wifi.WifiNetworkManager.WifiDisabledException;
import com.google.vr180.common.wifi.WifiUtilities;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Implementation of NetworkManager built on the android wifi apis. */
public class AndroidNetworkManager implements NetworkManager {
  private static final String TAG = "AndroidNetworkManager";
  /** A special value that WifiManager uses when not connected to a network. */
  private static final String UNKNOWN_NETWORK = "<unknown ssid>";

  /**
   * A regex matching network interfaces to exclude from the camera hostname list. This matches: -
   * Loopback interfaces ("lo", "dummy.*") - LTE interfaces (rmnet.*)
   */
  private static final Pattern EXCLUDED_INTERFACE_PATTERN = Pattern.compile("lo|dummy.*|rmnet.*");

  private final Context context;
  private final WifiClientManager wifiClientManager;
  private final int httpServerPort;
  private final SslManager sslManager;
  private final StatusNotifier statusNotifier;

  private volatile WifiConnectionErrorType lastConnectionError =
      WifiConnectionErrorType.DEFAULT_NO_CONNECTION_ERROR;

  public AndroidNetworkManager(
      Context context,
      int httpServerPort,
      SslManager sslManager,
      StatusNotifier statusNotifier) {
    this.context = context;
    this.wifiClientManager = new WifiClientManager(context, false  /* forgetNetworkOnDisconnect */);
    this.httpServerPort = httpServerPort;
    this.sslManager = sslManager;
    this.statusNotifier = statusNotifier;
  }

  @Override
  public HttpServerStatus getHttpServerStatus() {
    HttpServerStatus.Builder result =
        HttpServerStatus.newBuilder()
            .setCameraPort(httpServerPort);
    byte[] certifiateSignature = sslManager.getCameraCertificateSignature();
    if (certifiateSignature != null) {
      result.setCameraCertificateSignature(ByteString.copyFrom(certifiateSignature));
    }

    // Find all ip addresses of the camera.
    try {
      List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
      for (NetworkInterface networkInterface : interfaces) {
        if (shouldExcludeNetworkInterface(networkInterface.getName())) {
          continue;
        }
        for (InetAddress ipAddress : Collections.list(networkInterface.getInetAddresses())) {
          if (!(ipAddress instanceof Inet4Address)) {
            // The VR180 app doesn't really support ipv6 hostnames.
            continue;
          }

          result.addCameraHostname(ipAddress.getHostAddress());
        }
      }
    } catch (SocketException e) {
      Log.e(TAG, "Unable to enumerat ip addresses.", e);
    }

    return result.build();
  }

  @Override
  public String getActiveWifiSsid() {
    WifiManager wifiManager = getWifiManager();
    if (wifiManager == null) {
      return null;
    }
    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
    if (wifiInfo == null) {
      Log.e(TAG, "Error obtaining Wi-Fi info.");
      return null;
    }
    if (UNKNOWN_NETWORK.equals(wifiInfo.getSSID())) {
      return null;
    }

    return wifiInfo.getSSID();
  }

  @Override
  public WifiStatus getWifiStatus() {
    WifiManager wifiManager = getWifiManager();
    if (wifiManager == null) {
      return WifiStatus.getDefaultInstance();
    }

    WifiStatus.Builder result =
        WifiStatus.newBuilder().setConnectionErrorType(lastConnectionError);

    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
    if (wifiInfo != null) {
      result
          .setWpaSupplicantState(
              WifiUtilities.convertSupplicantState(wifiInfo.getSupplicantState()))
          .setSignalLevel(wifiInfo.getRssi());
    }
    String countryCode = getWifiCountryCode();
    if (countryCode != null) {
      result.setWifiCountryCode(countryCode);
    } else {
      Log.e(TAG, "Failed to get country code");
    }

    return result.build();
  }

  @Override
  public WifiNetworkStatus getWifiNetworkStatus() {
    WifiManager wifiManager = getWifiManager();
    if (wifiManager == null) {
      return null;
    }

    List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
    if (configuredNetworks == null) {
      Log.e(TAG, "Failed to get configured networks.");
      return null;
    }

    WifiNetworkStatus.Builder wifiNetworkStatus = WifiNetworkStatus.newBuilder();
    for (WifiConfiguration wifiConfiguration : configuredNetworks) {
      wifiNetworkStatus.addConfiguredNetworkSsids(wifiConfiguration.SSID);
    }

    wifiNetworkStatus.addAllScanResults(getWifiScanResults(wifiManager));

    return wifiNetworkStatus.build();
  }

  /** Starts trying to connect to the provided wifi access point. */
  @Override
  public void startWifiConnect(WifiAccessPointInfo accessPointInfo) {
    if (wifiClientManager.isConnectedOrConnecting()
        && wifiClientManager.isBoundToNetwork(accessPointInfo.getSsid())) {
      Log.d(TAG, "Already connecting to requested network. Ignoring new request.");
      return;
    }

    wifiClientManager.releaseNetwork();
    // Reset the error type state.
    lastConnectionError = WifiConnectionErrorType.DEFAULT_NO_CONNECTION_ERROR;

    if (!accessPointInfo.hasSsid()) {
      Log.d(TAG, "Releasing network and allowing WifiManager to pick.");
      return;
    }

    wifiClientManager.requestNetwork(
        accessPointInfo.getSsid(),
        accessPointInfo.getPassword(),
        new WifiNetworkManager.Listener() {
          @Override
          public void onNetworkInfoAvailable(
              String ssid,
              String passphrase,
              String groupOwnerIpAddress,
              String groupOwnerMacAddress) {
            Log.d(TAG, "onNetworkInfoAvailable " + ssid);
          }

          @Override
          public void onConnect() {
            Log.d(TAG, "onConnect");
            notifyStatusChanged();
          }

          @Override
          public void onDisconnect() {
            Log.d(TAG, "onDisconnect");
            notifyStatusChanged();
          }

          @Override
          public void onError(Exception e) {
            Log.d(TAG, "onError", e);
            if (e instanceof WifiAuthenticationException) {
              lastConnectionError = WifiConnectionErrorType.ERROR_AUTHENTICATING;
              return;
            }
            if (e instanceof WifiDisabledException) {
              lastConnectionError = WifiConnectionErrorType.WIFI_DISABLED;
              return;
            }
            if (e instanceof WifiConfigurationException) {
              lastConnectionError = WifiConnectionErrorType.INVALID_CONFIGURATION;
              return;
            }
            lastConnectionError = WifiConnectionErrorType.CONNECT_FAILED;
            notifyStatusChanged();
          }
        });
  }

  @Override
  public void updateWifiNetworkConfiguration(WifiNetworkConfiguration wifiNetworkConfiguration) {
    if (wifiNetworkConfiguration.hasWifiCountryCode()) {
      setWifiCountryCode(wifiNetworkConfiguration.getWifiCountryCode());
    }

    if (wifiNetworkConfiguration.getRemoveNetworkSsidsCount() == 0) {
      return;
    }

    WifiManager wifiManager = getWifiManager();
    if (wifiManager == null) {
      return;
    }

    // https://developer.android.com/reference/android/net/wifi/WifiConfiguration.html#SSID
    Set<String> removeNetworkSsids = new HashSet<>();
    for (String ssid : wifiNetworkConfiguration.getRemoveNetworkSsidsList()) {
      removeNetworkSsids.add(WifiUtilities.trimQuotes(ssid));
    }
    for (WifiConfiguration wifiConfiguration : wifiManager.getConfiguredNetworks()) {
      if (removeNetworkSsids.contains(WifiUtilities.trimQuotes(wifiConfiguration.SSID))) {
        if (!wifiManager.removeNetwork(wifiConfiguration.networkId)) {
          Log.e(TAG, String.format("Failed to remove network %s.", wifiConfiguration.SSID));
        }
      }
    }
    wifiManager.saveConfiguration();
  }

  private WifiManager getWifiManager() {
    WifiManager result = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    if (result == null) {
      Log.e(TAG, "Wi-Fi manager not found.");
    }
    return result;
  }

  private void notifyStatusChanged() {
    statusNotifier.notifyStatusChanged();
  }

  /**
   * Returns whether to exclude the network interface from reporting ip addresses.
   *
   * @param interfaceName The name of the NetworkInterface.
   * @return Whether the interface should be excluded.
   */
  @VisibleForTesting
  boolean shouldExcludeNetworkInterface(String interfaceName) {
    return EXCLUDED_INTERFACE_PATTERN.matcher(interfaceName).matches();
  }

  private static Collection<WifiScanResult> getWifiScanResults(WifiManager wifiManager) {
    List<ScanResult> scanResults = wifiManager.getScanResults();
    if (scanResults == null) {
      return Collections.EMPTY_LIST;
    }

    // Get the results of the most recent scan, keeping only the newest result for each ssid.
    HashMap<String, WifiScanResult> scannedNetworks = new HashMap<String, WifiScanResult>();
    for (ScanResult scanResult : scanResults) {
      WifiScanResult wifiScanResult = WifiUtilities.convertScanResult(scanResult);
      WifiScanResult existingResult = scannedNetworks.get(scanResult.SSID);
      if (existingResult == null
          || wifiScanResult.getTimestamp() > existingResult.getTimestamp()) {
        scannedNetworks.put(scanResult.SSID, wifiScanResult);
      }
    }
    return scannedNetworks.values();
  }

  private void setWifiCountryCode(String countryCode) {
    try {
      Method setCountryCode =
          WifiManager.class.getMethod("setCountryCode", String.class, boolean.class);
      setCountryCode.invoke(getWifiManager(), countryCode, true);
    } catch (Exception e) {
      Log.w(TAG, "Setting country code failed");
    }
  }

  private String getWifiCountryCode() {
    try {
      Method getCountryCodeMethod = WifiManager.class.getMethod("getCountryCode");
      String result =  (String) getCountryCodeMethod.invoke(getWifiManager());
      // Replace null value with an empty string.
      return result == null ? "" : result;
    } catch (Exception e) {
      Log.w(TAG, "Getting country code failed");
      // Return null to indicate that country code configuration is not supported.
      return null;
    }
  }
}
