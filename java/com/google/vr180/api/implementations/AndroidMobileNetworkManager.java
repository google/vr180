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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import com.google.vr180.CameraApi.CameraStatus.MobileNetworkStatus;
import com.google.vr180.CameraApi.CameraStatus.MobileNetworkStatus.SimCardState;
import com.google.vr180.api.camerainterfaces.MobileNetworkManager;
import com.google.vr180.api.camerainterfaces.StatusNotifier;

/**
 * Implementation of MobileNetworkManager to provide information about the LTE / Mobile Data network
 * connectivity of the phone.
 *
 * <p>Managing the network connection is not implemented since Android doesn't provide user-level
 * apis.
 */
public class AndroidMobileNetworkManager implements MobileNetworkManager {
  /** Represents kilobits per second in terms of bytes per second. */
  private static final long KBPS = 125;
  /** Represents megabits per second in terms of bytes per second. */
  private static final long MBPS = 1000 * KBPS;

  private final Context context;
  private final StatusNotifier connectionChangeNotifier;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private SignalStrength cellSignalStrength = null;

  public AndroidMobileNetworkManager(Context context, StatusNotifier connectionChangeNotifier) {
    this.context = context;
    this.connectionChangeNotifier = connectionChangeNotifier;
    mainHandler.post(() -> registerConnectivityListener());
  }

  @Override
  public MobileNetworkStatus getMobileNetworkStatus() {
    if (android.os.Build.VERSION.SDK_INT < 21) {
      // ConnectivityManager is unavailable.
      return null;
    }

    TelephonyManager teleManager =
        (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    if (teleManager == null) {
      return null;
    }

    MobileNetworkStatus.Builder result =
        MobileNetworkStatus.newBuilder()
            .setSimCardState(SimCardState.forNumber(teleManager.getSimState()));

    if (teleManager.getSimOperatorName() != null) {
      result.setCarrierName(teleManager.getSimOperatorName());
    }

    if (cellSignalStrength != null) {
      result.setSignalStrength(cellSignalStrength.getLevel());
    }

    NetworkInfo info = getFirstMobileNetwork();
    if (info != null) {
      result
          .setEnabled(isNetworkEnabled(info))
          .setState(convertNetworkState(info.getState()))
          .setRoaming(info.isRoaming())
          .setApproxBytesPerSecond(getApproxBytesPerSecond(info));

      if (info.getSubtypeName() != null) {
        result.setNetworkType(info.getSubtypeName());
      }
    }

    return result.build();
  }

  @Override
  public void setMobileNetworkEnabled(boolean enabled) {
    // TODO: Not implemented.
    throw new UnsupportedOperationException();
  }

  /** Returns network info about the first mobile network, or null if none is available. */
  private NetworkInfo getFirstMobileNetwork() {
    ConnectivityManager cm =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    Network[] networks = cm.getAllNetworks();
    for (Network network : networks) {
      NetworkInfo info = cm.getNetworkInfo(network);
      if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
        return info;
      }
    }

    return null;
  }

  private void registerConnectivityListener() {
    TelephonyManager teleManager =
        (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    teleManager.listen(
        new PhoneStateListener() {
          @Override
          public void onDataConnectionStateChanged(int state) {
            connectionChangeNotifier.notifyStatusChanged();
          }

          @Override
          public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            cellSignalStrength = signalStrength;
          }
        },
        PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
            | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
  }

  private boolean isNetworkEnabled(NetworkInfo info) {
    if (info.getState() != NetworkInfo.State.DISCONNECTED) {
      return true;
    }

    String reason = info.getReason();
    return (reason == null || reason.equals("specificDisabled"));
  }

  private static MobileNetworkStatus.NetworkState convertNetworkState(NetworkInfo.State state) {
    switch (state) {
      case CONNECTED:
        return MobileNetworkStatus.NetworkState.CONNECTED;
      case CONNECTING:
        return MobileNetworkStatus.NetworkState.CONNECTING;
      case DISCONNECTED:
      case SUSPENDED:
        return MobileNetworkStatus.NetworkState.DISCONNECTED;
      case DISCONNECTING:
        return MobileNetworkStatus.NetworkState.DISCONNECTING;
      case UNKNOWN:
      default:
        return MobileNetworkStatus.NetworkState.UNKNOWN;
    }
  }

  /**
   * Try to convert the network subType into an approximate data rate. Not sure how accurate these
   * values are. Likely all networks except LTE are too slow for live streaming.
   */
  private static long getApproxBytesPerSecond(NetworkInfo info) {
    switch (info.getSubtype()) {
      case TelephonyManager.NETWORK_TYPE_1xRTT:
      case TelephonyManager.NETWORK_TYPE_CDMA:
      case TelephonyManager.NETWORK_TYPE_EDGE:
        return 50 * KBPS; // ~ 50-100 kbps
      case TelephonyManager.NETWORK_TYPE_EVDO_0:
        return 500 * KBPS; // ~ 400-1000 kbps
      case TelephonyManager.NETWORK_TYPE_EVDO_A:
        return 1000 * KBPS; // ~ 600-1400 kbps
      case TelephonyManager.NETWORK_TYPE_GPRS:
        return 100 * KBPS; // ~ 100 kbps
      case TelephonyManager.NETWORK_TYPE_HSDPA:
        return 10 * MBPS; // ~ 2-14 Mbps
      case TelephonyManager.NETWORK_TYPE_HSPA:
        return 1 * MBPS; // ~ 700-1700 kbps
      case TelephonyManager.NETWORK_TYPE_HSUPA:
        return 10 * MBPS; // ~ 1-23 Mbps
      case TelephonyManager.NETWORK_TYPE_UMTS:
        return 5 * MBPS; // ~ 400-7000 kbps
      case TelephonyManager.NETWORK_TYPE_EHRPD:
        return 1 * MBPS; // ~ 1-2 Mbps
      case TelephonyManager.NETWORK_TYPE_EVDO_B:
        return 5 * MBPS; // ~ 5 Mbps
      case TelephonyManager.NETWORK_TYPE_HSPAP:
        return 15 * MBPS; // ~ 10-20 Mbps
      case TelephonyManager.NETWORK_TYPE_IDEN:
        return 25 * KBPS; // ~25 kbps
      case TelephonyManager.NETWORK_TYPE_LTE:
        return 50 * MBPS; // ~ 10+ Mbps
      case TelephonyManager.NETWORK_TYPE_UNKNOWN:
      default:
        return 0;
    }
  }
}
