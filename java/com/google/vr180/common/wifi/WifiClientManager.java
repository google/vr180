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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import com.google.common.base.Strings;
import com.google.vr180.common.logging.Log;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;

/**
 * Manages wifi client connectivity with the camera.
 *
 * <p>Handles connecting to camera Wifi networks including internal retries via disconnecting and
 * subsequently re-attempting to connect to the target network. Binds the process to the target
 * network to ensure requests will use it even though it is not internet connected.
 */
public class WifiClientManager extends WifiNetworkManager {
  private class NetworkStateChangedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      synchronized (WifiClientManager.this) {
        if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
          onNetworkStateChanged(intent);
        }
        if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(intent.getAction())) {
          onSupplicantStateChanged(intent);
        }
      }
    }

    /** Handles NETWORK_STATE_CHANGED_ACTION notifications. */
    @GuardedBy("WifiClientManager.this")
    private void onNetworkStateChanged(Intent intent) {
      NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
      String activeSsid = getActiveSsid();

      if (!isValidSsid(activeSsid)) {
        // getActiveSsid() could return "<unknown ssid>" for work profile.
        // In this case we fallback to use the extra in the broadcast.
        WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
        if (wifiInfo != null) {
          activeSsid = wifiInfo.getSSID();
        }
      }

      if (networkInfo.getState() == NetworkInfo.State.DISCONNECTED) {
        if (state == STATE_CONNECT_DISCONNECTING) {
          Log.i(TAG, "Network disconnect complete.");
          // When we see the disconnect from the previous network, try to connect to the target
          // network.
          requestConnect();
        } else if (state == STATE_CONNECTED) {
          // We lost the connection to the target network after we connected to it.
          Log.e(TAG, "Lost connection to target network after connection was established.");
          listener.onDisconnect();
          releaseNetwork(forgetNetworkOnDisconnect);
        }
      } else if (networkInfo.getState() == NetworkInfo.State.CONNECTED && isValidSsid(activeSsid)) {
        if (state == STATE_CONNECT_CONNECTING) {
          if (WifiUtilities.sameNetwork(activeSsid, targetNetworkSSID)) {
            Log.i(TAG, "Connected to target network: " + targetNetworkSSID);
            // Bind the process to the now connected network.
            if (!bindProcessToWifiNetwork()) {
              Log.w(TAG, "Unable to bind target network, will retry.");
              requestDisconnect();
            } else {
              listener.onConnect();
            }
          } else {
            Log.w(TAG, "Connected to wrong network, will retry: " + activeSsid);
            requestDisconnect();
          }

          // If we are still connecting, move to connected.
          if (state == STATE_CONNECT_CONNECTING) {
            state = STATE_CONNECTED;
          }
        }
      }
    }

    /** Handles SUPPLICANT_STATE_CHANGED_ACTION notifications. */
    @GuardedBy("WifiClientManager.this")
    private void onSupplicantStateChanged(Intent intent) {
      if (state != STATE_CONNECT_CONNECTING) {
        // Supplicant state change is only relevant when we are in a connecting state.
        return;
      }

      int supplicantError = intent.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, -1);
      if (supplicantError == WifiManager.ERROR_AUTHENTICATING) {
        // Connection attempt failed to authenticate.
        listener.onError(new WifiAuthenticationException());
        releaseNetwork(forgetNetworkOnDisconnect);
      }
    }
  }

  private class TimeoutRunnable implements Runnable {
    @Override
    public void run() {
      synchronized (WifiClientManager.this) {
        if (state != STATE_CONNECT_DISCONNECTING && state != STATE_CONNECT_CONNECTING) {
          // Timeout is only relevant when we are in a connecting state.
          return;
        }
        // Connection attempt has timed out.
        listener.onError(new WifiTimeoutException());
        releaseNetwork(forgetNetworkOnDisconnect);
      }
    }
  }

  private static final String TAG = "WifiClientManager";
  private static final long CONNECTION_TIMEOUT_MILLIS = 30_000;

  private static final int STATE_CONNECT_DISCONNECTING = 1;
  private static final int STATE_CONNECT_CONNECTING = 2;
  private static final int STATE_CONNECTED = 3;
  private static final int STATE_DISCONNECTED = 4;

  private final NetworkStateChangedReceiver networkStateChangedReceiver =
      new NetworkStateChangedReceiver();
  private final Runnable timeoutRunnable = new TimeoutRunnable();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final boolean forgetNetworkOnDisconnect;

  @GuardedBy("this")
  private Listener listener;

  @GuardedBy("this")
  private String targetNetworkSSID;

  @GuardedBy("this")
  private int targetNetworkId;

  @GuardedBy("this")
  private int state = STATE_DISCONNECTED;

  /**
   * The name of the active ssid before requestNetwork was called (or null if no network has been
   * requested).
   */
  @GuardedBy("this")
  private String previouslyActiveSsid;

  public WifiClientManager(Context context, boolean forgetNetworkOnDisconnect) {
    super(context);
    this.forgetNetworkOnDisconnect = forgetNetworkOnDisconnect;
  }

  /** Returns whether the specified network ssid has been requested already. */
  public synchronized boolean isBoundToNetwork(String ssid) {
    return WifiUtilities.sameNetwork(ssid, targetNetworkSSID);
  }

  /**
   * Returns the name of the active ssid if no network has been requested, or the one that was
   * previously active before requestNetwork was called.
   *
   * <p>This is the ssid that user had connected the phone to. This provides a heuristic of the
   * user's preferred wifi network even after we have connected the to the camera's hotspot.
   */
  public synchronized String getPreferredSsid() {
    if (previouslyActiveSsid != null) {
      return previouslyActiveSsid;
    }

    return getActiveSsid();
  }

  /** Returns true if currently fully connected to the target network specified in connect(). */
  public synchronized boolean isConnected() {
    return state == STATE_CONNECTED;
  }

  /** Returns true if currently connected to a network or actively trying to connect to one. */
  public synchronized boolean isConnectedOrConnecting() {
    return state == STATE_CONNECTED || state == STATE_CONNECT_CONNECTING;
  }

  /**
   * Returns true if we are busy (in between connect() and disconnect()) in which case calls to
   * connect() will fail.
   */
  public synchronized boolean isBusy() {
    return state != STATE_DISCONNECTED;
  }

  /**
   * Requests the specified target network and notify the caller of the connection state using the
   * provided listener interface. Only one network may be requested at a time. If requestNetwork()
   * returns success then releaseNetwork() must be called before calling requestNetwork() again.
   */
  public synchronized void requestNetwork(String ssid, String passphrase, Listener listener) {
    Log.i(TAG, "requestNetwork: " + ssid);

    // If already connecting, fail sync.
    if (isBusy()) {
      // Already connecting.
      Log.w(TAG, "Connection already active.");
      postDeferredError(listener, new WifiBusyException());
      return;
    }

    if (!wifiManager.isWifiEnabled()) {
      Log.w(TAG, "Wifi is not enabled.");
      postDeferredError(listener, new WifiDisabledException());
      return;
    }

    // Fail if a VPN is connected.
    if (isVpnConnected()) {
      Log.e(TAG, "VPN detected, connection failed");
      postDeferredError(listener, new VpnConnectedException());
      return;
    }

    previouslyActiveSsid = getActiveSsid();

    // Some android APIs use quotes on SSIDs. We remove any quotes here to normalize.
    ssid = WifiUtilities.trimQuotes(ssid);

    // If the passphrase is specified or we have no preexisting configuration.
    if (!Strings.isNullOrEmpty(passphrase) || findWifiConfiguration(ssid) == null) {
      // Remove any existing wifi configurations for this SSID.
      removeWifiConfigurations(ssid);

      // Create and save a new configuration for this SSID.
      WifiConfiguration newWifiConfiguration = createWifiConfiguration(ssid, passphrase);

      int id = wifiManager.addNetwork(newWifiConfiguration);
      if (id == -1) {
        Log.e(TAG, "Unable to add network");
        postDeferredError(listener, new WifiConfigurationException());
        return;
      }

      if (!wifiManager.saveConfiguration()) {
        Log.e(TAG, "Unable to save configuration");
        postDeferredError(listener, new WifiConfigurationException());
        return;
      }
    }

    // Find the actual saved configuration.
    WifiConfiguration wifiConfiguration = findWifiConfiguration(ssid);
    if (wifiConfiguration == null || wifiConfiguration.networkId == -1) {
      Log.e(TAG, "Unable to find wifi configuration");
      postDeferredError(listener, new WifiConfigurationException());
      return;
    }

    // Take the wake lock.
    wifiLock.acquire();

    // Configured wifi network
    targetNetworkId = wifiConfiguration.networkId;
    targetNetworkSSID = ssid;

    Log.i(TAG, String.format("Target Network: %s (%s)", targetNetworkSSID, targetNetworkId));

    this.listener = listener;

    // Set up receivers.
    IntentFilter networkStateChangedIntentFilter = new IntentFilter();
    networkStateChangedIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    networkStateChangedIntentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
    context.registerReceiver(
        networkStateChangedReceiver, networkStateChangedIntentFilter, null, handler);

    // Disconnect network. Once we are disconnected we will enable the target network and will
    // attempt to connect to it.
    requestDisconnect();

    // Set up connection timeout.
    handler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT_MILLIS);
  }

  /** Releases the previously bound network and removes it's configuration. */
  public void releaseAndForgetNetwork() {
    releaseNetwork(true);
  }

  /**
   * Releases the previously bound network but keeps it in the list of known networks. After this
   * call, the WifiManager is free to connect to a network with internet access.
   */
  public void releaseNetwork() {
    releaseNetwork(false);
  }

  /**
   * Releases the network previously specified in requestNetwork() and allow the device's wifi to
   * switch to the best available network.
   *
   * @param forgetNetwork Whether to permanently remove the network's configuration from
   *     WifiManager.
   */
  private synchronized void releaseNetwork(boolean forgetNetwork) {
    Log.i(TAG, "releaseNetwork");

    if (!isBusy()) {
      return;
    }

    // Stop timeout.
    handler.removeCallbacks(timeoutRunnable);

    // Unregister receivers.
    try {
      context.unregisterReceiver(networkStateChangedReceiver);
    } catch (IllegalArgumentException e) {
      // The receiver was not registered. Ignore.
    }

    unbindProcessFromWifiNetwork();

    this.listener = null;

    // Remove network config, reassociate.
    targetNetworkSSID = null;
    if (targetNetworkId != -1) {
      if (forgetNetwork) {
        wifiManager.removeNetwork(targetNetworkId);
      }

      targetNetworkId = -1;
      wifiManager.saveConfiguration();
      wifiManager.reassociate();
    }

    previouslyActiveSsid = null;

    // Release the wake lock.
    wifiLock.release();

    state = STATE_DISCONNECTED;
  }

  private void removeWifiConfigurations(String ssid) {
    Log.i(TAG, "removeWifiConfigurations");
    List<WifiConfiguration> existingNetworks = wifiManager.getConfiguredNetworks();
    if (existingNetworks != null) {
      // Clear out any existing network configs with our SSID to make sure we don't have conflicts.
      for (WifiConfiguration wifiConfiguration : existingNetworks) {
        if (WifiUtilities.sameNetwork(ssid, wifiConfiguration.SSID)) {
          Log.i(
              TAG,
              String.format(
                  "Remove existing network: %s (%s)",
                  wifiConfiguration.SSID, wifiConfiguration.networkId));
          if (!wifiManager.removeNetwork(wifiConfiguration.networkId)) {
            Log.w(TAG, "Remove failed, this may mean the user manually joined, continuing.");
          }
        }
      }
    }
  }

  private WifiConfiguration findWifiConfiguration(String ssid) {
    Log.i(TAG, "findWifiConfiguration");
    List<WifiConfiguration> existingNetworks = wifiManager.getConfiguredNetworks();
    if (existingNetworks != null) {
      // Clear out any existing network configs with our SSID to make sure we don't have conflicts.
      for (WifiConfiguration wifiConfiguration : existingNetworks) {
        if (WifiUtilities.sameNetwork(ssid, wifiConfiguration.SSID)) {
          Log.i(TAG, "Found configuration for network: " + ssid);
          return wifiConfiguration;
        }
      }
    }
    return null;
  }

  private WifiConfiguration createWifiConfiguration(String ssid, String passphrase) {
    Log.i(TAG, "createWifiConfiguration");
    WifiConfiguration wifiConfiguration = new WifiConfiguration();
    wifiConfiguration.SSID = "\"" + ssid + "\"";
    if (!Strings.isNullOrEmpty(passphrase)) {
      // Configure to connect to a WPA2 PSK secure access point.
      wifiConfiguration.preSharedKey = "\"" + passphrase + "\"";
      wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
      wifiConfiguration.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
    } else {
      // Configure to connect to an open wifi access point.
      wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
      wifiConfiguration.allowedAuthAlgorithms.clear();
    }
    return wifiConfiguration;
  }

  @GuardedBy("this")
  private void requestDisconnect() {
    state = STATE_CONNECT_DISCONNECTING;
    Log.i(TAG, "Requesting network disconnect.");
    wifiManager.disconnect();
  }

  @GuardedBy("this")
  private void requestConnect() {
    state = STATE_CONNECT_CONNECTING;
    Log.i(TAG, "Enabling network and connecting.");
    wifiManager.enableNetwork(targetNetworkId, true);
  }

  private Network getWifiNetwork() {
    for (Network network : connectivityManager.getAllNetworks()) {
      NetworkInfo netInfo = connectivityManager.getNetworkInfo(network);
      if (netInfo != null) {
        if (netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
          return network;
        }
      }
    }
    return null;
  }

  /**
   * Binds our process to the wifi network. If we do not bind our process then android may route
   * network requests over Cellular even when connected to Wifi if it has decided that the connected
   * Wifi network is not usable. Because our Wifi network does not reach the internet the platform
   * will generally consider it unusable unless bound.
   */
  @GuardedBy("this")
  private boolean bindProcessToWifiNetwork() {
    if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
      Log.i(TAG, "No need to bind network");
      return true;
    }
    Log.i(TAG, "bindProcessToNetwork");
    Network network = getWifiNetwork();
    if (network == null) {
      Log.e(TAG, "Unable to find network to bind.");
      return false;
    }

    return bindProcessToNetwork(network);
  }

  /** Unbinds our process from the wifi network. */
  @GuardedBy("this")
  private void unbindProcessFromWifiNetwork() {
    bindProcessToNetwork(null);
  }

  /** Binds our process to the specified network. */
  @GuardedBy("this")
  private boolean bindProcessToNetwork(Network network) {
    if (VERSION.SDK_INT >= VERSION_CODES.M) {
      return connectivityManager.bindProcessToNetwork(network);
    } else {
      return ConnectivityManager.setProcessDefaultNetwork(network);
    }
  }

  private void postDeferredError(final Listener listener, final Exception e) {
    handler.post(() -> listener.onError(e));
  }

  private static boolean isValidSsid(String ssid) {
    return ssid != null && !"".equals(ssid) && !"<unknown ssid>".equals(ssid);
  }
}
