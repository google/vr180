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
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.provider.Settings.Secure;
import com.google.vr180.CameraApi.WifiScanResult;
import com.google.vr180.CameraApi.WifiSupplicantState;
import com.google.vr180.CameraApi.WpaAuthType;

/**
 * Helper methods for dealing with WiFi networks.
 */
public class WifiUtilities {

  public static final int[] common24GHzOperatingChannels = new int[] {1, 6, 11};
  public static final int[] common5GHzOperatingChannels = new int[] {36, 40, 44, 48};
  public static final int MIN_24_GHZ_CHANNEL = 1;
  public static final int MAX_24_GHZ_CHANNEL = 11;
  private static final int CHANNEL_1_FREQUENCY_24GHZ = 2412;
  private static final int MAX_FREQUENCY_24GHZ = 2472;
  private static final int HZ_PER_CHANNEL = 5;
  private static final int CHANNEL_1_FREQUENCY_5GHZ = 5005;
  private static final int MAX_FREQUENCY_5GHZ = 5825;
  /**
   * Checks if the two specified SSIDs are equivalent after trimming quotes as some APIs use quoted
   * SSIDs and others do not. Note: We do not consider two null SSIDs to be equivalent.
   */
  public static boolean sameNetwork(String ssid1, String ssid2) {
    if (ssid1 == null || ssid2 == null) {
      return false;
    }
    ssid1 = trimQuotes(ssid1);
    ssid2 = trimQuotes(ssid2);
    return ssid1.equals(ssid2);
  }

  /** If the string starts with and ends with a double quote character, trim these from the ends. */
  public static String trimQuotes(String string) {
    if (string != null
        && string.length() >= 2
        && string.startsWith("\"")
        && string.endsWith("\"")) {
      string = string.substring(1, string.length() - 1);
    }
    return string;
  }

  /** Converts an android ScanResult into the CameraApi proto. */
  public static WifiScanResult convertScanResult(ScanResult scanResult) {
    return WifiScanResult.newBuilder()
        .setSsid(scanResult.SSID)
        .setSignalLevel(scanResult.level)
        .setAuthType(parseAuthType(scanResult.capabilities))
        .setTimestamp(scanResult.timestamp)
        .build();
  }

  /** Determines the auth type from Android's ScanResult.capabilities. */
  public static WpaAuthType parseAuthType(String capabilitiesString) {
    if (capabilitiesString == null) {
      return WpaAuthType.NONE_UNKNOWN;
    }

    if (capabilitiesString.contains("WEP")) {
      return WpaAuthType.WEP;
    }
    if (capabilitiesString.contains("WPA-PSK")) {
      return WpaAuthType.WPA_PSK;
    }
    if (capabilitiesString.contains("WPA-EAP")) {
      return WpaAuthType.WPA_EAP;
    }
    if (capabilitiesString.contains("WPA2-PSK")) {
      return WpaAuthType.WPA2_PSK;
    }
    if (capabilitiesString.contains("WPA2-EAP")) {
      return WpaAuthType.WPA2_EAP;
    }
    return WpaAuthType.NONE_UNKNOWN;
  }

  /** Converts from Android's SupplicantState to the WifiSupplicantState proto. */
  public static WifiSupplicantState convertSupplicantState(SupplicantState state) {
    switch (state) {
      case ASSOCIATED:
        return WifiSupplicantState.ASSOCIATED;
      case ASSOCIATING:
        return WifiSupplicantState.ASSOCIATING;
      case AUTHENTICATING:
        return WifiSupplicantState.AUTHENTICATING;
      case COMPLETED:
        return WifiSupplicantState.COMPLETED;
      case DISCONNECTED:
        return WifiSupplicantState.DISCONNECTED;
      case DORMANT:
        return WifiSupplicantState.DORMANT;
      case FOUR_WAY_HANDSHAKE:
        return WifiSupplicantState.FOUR_WAY_HANDSHAKE;
      case GROUP_HANDSHAKE:
        return WifiSupplicantState.GROUP_HANDSHAKE;
      case INACTIVE:
        return WifiSupplicantState.INACTIVE;
      case INTERFACE_DISABLED:
        return WifiSupplicantState.INTERFACE_DISABLED;
      case SCANNING:
        return WifiSupplicantState.SCANNING;
      default:
        return WifiSupplicantState.UNKNOWN_SUPPLICANT_STATE;
    }
  }

  // Deterministically obtains a value from the input {@code list}. Needed because we want to
  // choose the same channel every time we start a direct hotspot. Additionally, we want different
  // phones to choose different channels. Implementation uses the android id, which will remain
  // constant until a device factory reset.
  public static int deterministicallyFromList(int[] list, Context context) {
    int listPos = 0;
    String androidId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);

    if (androidId != null) {
      int hash = Math.abs(androidId.hashCode());
      listPos = hash % list.length;
    }
    return list[listPos];
  }

  public static boolean isValid24GhzChannel(int channel) {
    return MIN_24_GHZ_CHANNEL <= channel && channel <= MAX_24_GHZ_CHANNEL;
  }

  /** Gets the channel number that the wifi is currently connected to. */
  public static int getInfraConnectionChannel(WifiManager wifiManager) {
    int frequency = 0;
    if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      WifiInfo wifiInfo = wifiManager.getConnectionInfo();
      frequency = wifiInfo != null ? wifiInfo.getFrequency() : 0;
    }
    return getChannelFromFrequency(frequency);
  }

  /**
   * Converts a frequency value in MHz into a channel. For more info, see
   * https://en.wikipedia.org/wiki/List_of_WLAN_channels. Returns 0 if the frequency is outside of
   * the standard ranges for 2.4 GHz and 5 GHz wifi.
   */
  private static int getChannelFromFrequency(int frequency) {
    if (CHANNEL_1_FREQUENCY_24GHZ <= frequency && frequency <= MAX_FREQUENCY_24GHZ) {
      return (frequency - CHANNEL_1_FREQUENCY_24GHZ) / HZ_PER_CHANNEL + 1;
    } else if (CHANNEL_1_FREQUENCY_5GHZ <= frequency && frequency <= MAX_FREQUENCY_5GHZ) {
      return (frequency - CHANNEL_1_FREQUENCY_5GHZ) / HZ_PER_CHANNEL + 1;
    }
    return 0;
  }
}
