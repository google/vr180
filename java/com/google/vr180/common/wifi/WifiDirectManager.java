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
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Handler;
import android.os.Looper;
import com.google.common.util.concurrent.SettableFuture;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiHotspotConfiguration.ChannelPreference;
import com.google.vr180.common.logging.Log;
import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Manages Wifi Direct connectivity with the camera. */
public class WifiDirectManager extends WifiNetworkManager {
  private static final String TAG = "WifiDirectManager";

  private static final long TEARDOWN_WAIT_TIMEOUT_MS = 2_000;
  private static final long GROUP_CREATION_RETRY_DELAY_MS = 500;
  private static final long SET_CHANNEL_TIMEOUT = 1000;

  private static WifiDirectManager instance;

  public static synchronized WifiDirectManager getInstance(Context context) {
    if (instance == null) {
      instance = new WifiDirectManager(context);
    }
    return instance;
  }

  /** Data class to hold wifi direct group info that we are interested in */
  public static class WifiDirectGroupInfo {
    String ssid;
    String passphrase;
    String groupOwnerIpAddress;
    String groupOwnerMacAddress;

    public WifiDirectGroupInfo(
        String ssid,
        String passphrase,
        String groupOwnerIpAddress,
        String groupOwnerMacAddress) {
      this.ssid = ssid;
      this.passphrase = passphrase;
      this.groupOwnerIpAddress = groupOwnerIpAddress;
      this.groupOwnerMacAddress = groupOwnerMacAddress;
    }
  }

  /** Wifi direct states for the state machine */
  enum State {
    IDLE_NO_GROUP,
    IDLE_GROUP_ACTIVE,
    IDLE_PEER_ACTIVE,
    STARTED_NO_GROUP,
    STARTED_CREATING_GROUP,
    STARTED_GROUP_ACTIVE,
    STARTED_PEER_ACTIVE,
    TEARDOWN,
    P2P_DISABLED;

    public boolean isIdle() {
      return this == IDLE_NO_GROUP || this == IDLE_GROUP_ACTIVE || this == IDLE_PEER_ACTIVE;
    }

    public boolean isBusy() {
      return this == STARTED_NO_GROUP || this == STARTED_CREATING_GROUP
          || this == STARTED_GROUP_ACTIVE || this == STARTED_PEER_ACTIVE;
    }

    public boolean isDisabled() {
      return this == P2P_DISABLED;
    }

    public boolean hasGroup() {
      return this == STARTED_GROUP_ACTIVE || this == STARTED_PEER_ACTIVE;
    }
  }

  /** Wifi direct events for the state machine */
  enum Event {
    START,
    GROUP_CREATION_REQUESTED,
    GROUP_CREATION_REQUEST_FAILED,
    STOP,

    NO_GROUP,
    GROUP_ACTIVE_NO_PEER,
    PEER_ACTIVE,
    P2P_DISABLED,
    P2P_ENABLED,
    TIME_OUT,
  }

  private class WifiP2pReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
        int p2pState =
            intent.getIntExtra(
                WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED);
        boolean enabled = p2pState == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
        stateMachine.handleEvent(enabled ? Event.P2P_ENABLED : Event.P2P_DISABLED);
        Log.d(TAG, "WIFI_P2P_STATE_CHANGED_ACTION enabled " + enabled);
      }

      if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        WifiP2pInfo wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
        WifiP2pGroup wifiP2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);
        Log.d(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION");
        Log.d(TAG, "NetworkInfo: " + networkInfo);
        Log.d(TAG, "WifiP2pInfo: " + wifiP2pInfo);
        Log.d(TAG, "WifiP2pGroup: " + wifiP2pGroup);

        if (!isLocalGroupActive(networkInfo, wifiP2pInfo, wifiP2pGroup)) {
          stateMachine.handleEventWithData(Event.NO_GROUP, null);
        } else if (!isConnected(networkInfo, wifiP2pInfo, wifiP2pGroup)) {
          if (networkInfo.getState() == NetworkInfo.State.CONNECTED) {
            stateMachine.handleEventWithData(
                Event.GROUP_ACTIVE_NO_PEER,
                new WifiDirectGroupInfo(
                    wifiP2pGroup.getNetworkName(),
                    wifiP2pGroup.getPassphrase(),
                    wifiP2pInfo.groupOwnerAddress.getHostAddress(),
                    wifiP2pGroup.getOwner().deviceAddress));
          } else {
            stateMachine.handleEvent(Event.GROUP_ACTIVE_NO_PEER);
          }
        } else {
          stateMachine.handleEventWithData(
              Event.PEER_ACTIVE,
              new WifiDirectGroupInfo(
                  wifiP2pGroup.getNetworkName(),
                  wifiP2pGroup.getPassphrase(),
                  null,
                  wifiP2pGroup.getOwner().deviceAddress));
        }
      }
    }

    private boolean isLocalGroupActive(
        NetworkInfo networkInfo, WifiP2pInfo wifiP2pInfo, WifiP2pGroup wifiP2pGroup) {
      return wifiP2pGroup != null
          && wifiP2pInfo != null
          && networkInfo != null
          && wifiP2pGroup.isGroupOwner();
    }

    private boolean isConnected(
        NetworkInfo networkInfo, WifiP2pInfo wifiP2pInfo, WifiP2pGroup wifiP2pGroup) {
      return wifiP2pGroup != null
          && wifiP2pInfo != null
          && networkInfo != null
          && wifiP2pInfo.groupFormed
          && wifiP2pInfo.isGroupOwner
          && wifiP2pInfo.groupOwnerAddress != null
          && wifiP2pGroup.getClientList().size() >= 1
          && networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED;
    }
  }

  private final boolean supported;
  private final Channel wifiP2pChannel;
  private final Handler handler;
  private final WifiP2pManager wifiP2pManager;
  private final WifiP2pReceiver wifiP2pReceiver = new WifiP2pReceiver();
  private final StateMachine<State, Event, WifiDirectGroupInfo> stateMachine;

  // The listener waiting for results of the operation.
  private Listener listener;
  private ChannelPreference channelPreference;

  private WifiDirectManager(Context context) {
    this(context, new Handler(Looper.getMainLooper()),
        StateMachine.createStateMachineHandler());
  }

  WifiDirectManager(Context context, Handler mainHandler, Handler stateMachineHandler) {
    super(context);
    handler = mainHandler;
    PackageManager packageManager = context.getPackageManager();
    supported = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
    Log.d(TAG, "Wi-Fi Direct supported: " + supported);

    // Initialize the state machine
    stateMachine =
        new StateMachine<>(
            stateMachineHandler,
            this::handleStateChange,
            State.IDLE_NO_GROUP,
            Event.TIME_OUT);
    initStateMachine();

    if (supported) {
      wifiP2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
      // Initialize the Wifi P2P Channel.
      Log.i(TAG, "Initializing Wifi P2P Manager.");
      wifiP2pChannel =
          wifiP2pManager.initialize(context, context.getMainLooper(), null /*listener*/);

      // Listen for Wifi P2P Intents. The ones we're interested in are sticky so registering
      // the receiver here will update us with current system state.
      IntentFilter intentFilter = new IntentFilter();
      intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
      intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
      context.registerReceiver(wifiP2pReceiver, intentFilter, null, handler);
    } else {
      wifiP2pManager = null;
      wifiP2pChannel = null;
    }
  }

  /** Returns true if Wi-Fi Direct support is declared. */
  public boolean isWifiDirectSupported() {
    return supported;
  }

  /** Returns true if currently fully connected to the target network specified in connect(). */
  public boolean isConnected() {
    return stateMachine.getState().hasGroup();
  }

  /** Returns true if we are busy (in between createHotspot() and releaseNetwork()) */
  public boolean isBusy() {
    return stateMachine.getState().isBusy();
  }

  /**
   * Creates the Wifi Direct network and notifies the caller of the connection state using the
   * provided listener interface. Only one network may be requested at a time. If createHotspot()
   * returns success then releaseNetwork() must be called before calling createHotspot() again.
   *
   * @param channelPreference The preferred WiFi channel settings of the client. Optional.
   * @param listener The listener that receives notifications of the hotspot creation status.
   */
  public void createHotspot(ChannelPreference channelPreference, Listener listener) {
    Log.i(TAG, "createHotspot");

    if (!supported) {
      Log.w(TAG, "Wifi Direct not supported");
      onError(new WifiDirectNotSupportedException(), listener);
      return;
    }

    if (!isWifiEnabled()) {
      Log.w(TAG, "Wifi is not enabled");
      onError(new WifiDisabledException(), listener);
      return;
    }

    if (isVpnConnected()) {
      Log.e(TAG, "VPN detected, connection failed");
      onError(new VpnConnectedException(), listener);
      return;
    }

    stateMachine.handleEventWithCallbacks(Event.START, (exception) -> {
      if (exception == null) {
        this.listener = listener;
        this.channelPreference = channelPreference;
      } else if (listener != null) {
        onError(exception, listener);
      }
    }, null);
  }

  /** Releases the previously requested Wi-Fi network. */
  public void releaseNetwork() {
    Log.i(TAG, "releaseNetwork");
    listener = null;
    channelPreference = null;
    stateMachine.handleEvent(Event.STOP);
  }

  /** Helper function for testing purpose */
  StateMachine<State, Event, WifiDirectGroupInfo> getStateMachine() {
    return stateMachine;
  }

  /** Helper function for testing purpose */
  State getState() {
    return stateMachine.getState();
  }

  private void onError(Exception exception, Listener listener) {
    if (listener != null) {
      handler.post(() -> listener.onError(exception));
    }
  }

  private void handleStateChange(Object oldOne, Object newOne) {
    State oldState = (State) oldOne;
    State newState = (State) newOne;

    // Callbacks
    if (listener != null) {

      // onNetworkInfoAvailable() and onConnect() may both be called in one state transition, if the
      // state jumps from IDLE_PEER_ACTIVE to STARTED_PEER_ACTIVE
      if (newState.hasGroup() && !oldState.hasGroup()) {
        WifiDirectGroupInfo wifiDirectGroupInfo = stateMachine.getData();
        listener.onNetworkInfoAvailable(
            wifiDirectGroupInfo.ssid,
            wifiDirectGroupInfo.passphrase,
            wifiDirectGroupInfo.groupOwnerIpAddress,
            wifiDirectGroupInfo.groupOwnerMacAddress);
      }

      if (newState == State.STARTED_PEER_ACTIVE) {
        listener.onConnect();
      } else if (oldState == State.STARTED_PEER_ACTIVE && newState.isBusy()) {
        listener.onDisconnect();
      } else if (newState == State.P2P_DISABLED) {
        onError(new WifiDirectDisabledException(), listener);
      } else if (newState == State.TEARDOWN) {
        onError(new WifiConfigurationException(), listener);
      }
    }

    // Actions
    if (oldState.isBusy() && !newState.isBusy()) {
      wifiLock.release();
    } else if (!oldState.isBusy() && newState.isBusy()) {
      wifiLock.acquire();
    }

    if (newState == State.STARTED_NO_GROUP) {
      if (oldState == State.STARTED_CREATING_GROUP) {
        handler.postDelayed(() -> {
          resetChannelsConfigurationIfNecessary();
          createGroup();
          stateMachine.handleEvent(Event.GROUP_CREATION_REQUESTED);
        }, GROUP_CREATION_RETRY_DELAY_MS);
      } else {
        configureChannelsIfNecessary();
        createGroup();
        stateMachine.handleEvent(Event.GROUP_CREATION_REQUESTED);
      }
    }

    if (newState == State.TEARDOWN) {
      removeGroup();
    }
  }

  private void createGroup() {
    wifiP2pManager.createGroup(
        wifiP2pChannel,
        new ActionListener() {
          @Override
          public void onSuccess() {
            Log.i(TAG, "createGroup success");
          }

          @Override
          public void onFailure(int reason) {
            stateMachine.handleEvent(Event.GROUP_CREATION_REQUEST_FAILED);
            Log.w(TAG, "createGroup failure: " + reason);
          }
        });
  }

  private void removeGroup() {
    wifiP2pManager.removeGroup(
        wifiP2pChannel,
        new ActionListener() {
          @Override
          public void onSuccess() {
            Log.i(TAG, "removeGroup success");
          }

          @Override
          public void onFailure(int reason) {
            Log.i(TAG, "removeGroup failure: " + reason);
          }
        });
  }

  private void configureChannelsIfNecessary() {
    if (channelPreference == null) {
      Log.d(TAG, "Skipping channel configuration for unknown client preference");
      return;
    }
    int infraChannel = WifiUtilities.getInfraConnectionChannel(wifiManager);
    // The listening channel must be 2.4 GHz
    int listeningChannel =
        WifiUtilities.isValid24GhzChannel(infraChannel)
            ? infraChannel
            : WifiUtilities.deterministicallyFromList(
                WifiUtilities.common24GHzOperatingChannels, context);
    int operatingChannel =
        chooseOperatingChannel(
            channelPreference.getClientPreferredChannel(),
            infraChannel,
            channelPreference.getClientSupports5Ghz(),
            is5GHzBandSupported(),
            channelPreference.getClientPrefers5Ghz(),
            WifiUtilities.deterministicallyFromList(
                WifiUtilities.common5GHzOperatingChannels, context),
            WifiUtilities.deterministicallyFromList(
                WifiUtilities.common24GHzOperatingChannels, context));
    Log.i(
        TAG, "ic = " + infraChannel + " lc = " + listeningChannel + " oc = " + operatingChannel);
    try {
      setChannels(listeningChannel, operatingChannel)
          .get(SET_CHANNEL_TIMEOUT, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      Log.e(TAG, "Setting channel failed");
    }
  }

  private void resetChannelsConfigurationIfNecessary() {
    if (channelPreference == null) {
      Log.d(TAG, "Skipping channel configuration reset for unknown client preference");
      return;
    }
    int infraChannel = WifiUtilities.getInfraConnectionChannel(wifiManager);
    int listeningChannel =
        WifiUtilities.isValid24GhzChannel(infraChannel)
            ? infraChannel
            : WifiUtilities.deterministicallyFromList(
                WifiUtilities.common24GHzOperatingChannels, context);
    try {
      setChannels(listeningChannel, listeningChannel)
          .get(SET_CHANNEL_TIMEOUT, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      Log.e(TAG, "Clearing channel configuration failed");
    }
  }

  static int chooseOperatingChannel(
      int preferredChannel,
      int infraChannel,
      boolean clientSupports5Ghz,
      boolean selfSupports5Ghz,
      boolean clientPrefer5Ghz,
      int default5GhzChannel,
      int default24GhzChannel) {
    if (clientSupports5Ghz && selfSupports5Ghz) {
      if (preferredChannel != 0) {
        return preferredChannel;
      } else if (infraChannel != 0
          && (!WifiUtilities.isValid24GhzChannel(infraChannel) || !clientPrefer5Ghz)) {
        // Use intraChannel if (1) we have a 5G infraChannel or (2) we have a 2.4 infraChannel and
        // client doesn't have preference on 5G.
        return infraChannel;
      } else {
        return default5GhzChannel;
      }
    } else {
      if (WifiUtilities.isValid24GhzChannel(preferredChannel)) {
        return preferredChannel;
      } else if (WifiUtilities.isValid24GhzChannel(infraChannel)) {
        return infraChannel;
      } else {
        return default24GhzChannel;
      }
    }
  }

  /**
   * Sets channels used by Wi-Fi Direct.
   *
   * @param lc listening channel, used for discovery
   * @param oc operating channel, used by the hotspot
   */
  private Future<Void> setChannels(int lc, int oc) {
    SettableFuture<Void> setChannelCompleteFuture = SettableFuture.create();
    try {
      Method setWifiP2pChannels =
          WifiP2pManager.class.getMethod(
              "setWifiP2pChannels",
              WifiP2pManager.Channel.class,
              int.class, int.class,
              WifiP2pManager.ActionListener.class);
      setWifiP2pChannels.invoke(
          wifiP2pManager, wifiP2pChannel, lc, oc, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
              Log.d(TAG, "Change channel succeeded");
              setChannelCompleteFuture.set(null);
            }

            @Override
            public void onFailure(int reason) {
              Log.e(TAG, "Change channel failed");
              setChannelCompleteFuture.setException(new WifiConfigurationException());
            }
          });
    } catch (Exception e) {
      Log.e(TAG, "Change channel failed", e);
      setChannelCompleteFuture.setException(e);
    }
    return setChannelCompleteFuture;
  }

  private void initStateMachine() {
    stateMachine.on(Event.START)
        .states(State.IDLE_NO_GROUP, State.TEARDOWN).transitionTo(State.STARTED_NO_GROUP)
        .states(State.IDLE_GROUP_ACTIVE).transitionTo(State.STARTED_GROUP_ACTIVE)
        .states(State.IDLE_PEER_ACTIVE).transitionTo(State.STARTED_PEER_ACTIVE)
        .states(
            State.STARTED_NO_GROUP,
            State.STARTED_CREATING_GROUP,
            State.STARTED_GROUP_ACTIVE,
            State.STARTED_PEER_ACTIVE).reject(new WifiBusyException())
        .states(State.P2P_DISABLED).reject(new WifiDirectDisabledException());

    stateMachine.on(Event.STOP)
        .states(State.STARTED_NO_GROUP).transitionTo(State.IDLE_NO_GROUP)
        .states(
            State.STARTED_CREATING_GROUP,
            State.STARTED_GROUP_ACTIVE,
            State.STARTED_PEER_ACTIVE).transitionTo(State.TEARDOWN)
        .states(
            State.IDLE_NO_GROUP,
            State.IDLE_GROUP_ACTIVE,
            State.IDLE_PEER_ACTIVE,
            State.TEARDOWN,
            State.P2P_DISABLED).ignore();

    stateMachine.on(Event.P2P_DISABLED)
        .states(
            State.IDLE_NO_GROUP,
            State.IDLE_GROUP_ACTIVE,
            State.IDLE_PEER_ACTIVE,
            State.STARTED_NO_GROUP,
            State.STARTED_CREATING_GROUP,
            State.STARTED_GROUP_ACTIVE,
            State.STARTED_PEER_ACTIVE,
            State.TEARDOWN).transitionTo(State.P2P_DISABLED)
        .states(State.P2P_DISABLED).ignore();

    stateMachine.on(Event.P2P_ENABLED)
        .states(
            State.IDLE_NO_GROUP,
            State.IDLE_GROUP_ACTIVE,
            State.IDLE_PEER_ACTIVE,
            State.STARTED_NO_GROUP,
            State.STARTED_CREATING_GROUP,
            State.STARTED_GROUP_ACTIVE,
            State.STARTED_PEER_ACTIVE,
            State.TEARDOWN).ignore()
        .states(State.P2P_DISABLED).transitionTo(State.IDLE_NO_GROUP);

    stateMachine.on(Event.NO_GROUP)
        .states(
            State.IDLE_GROUP_ACTIVE,
            State.IDLE_PEER_ACTIVE,
            State.TEARDOWN).transitionTo(State.IDLE_NO_GROUP)
        .states(
            State.STARTED_GROUP_ACTIVE,
            State.STARTED_PEER_ACTIVE).transitionTo(State.STARTED_NO_GROUP)
        .states(
            State.IDLE_NO_GROUP,
            State.STARTED_NO_GROUP,
            State.STARTED_CREATING_GROUP,
            State.P2P_DISABLED).ignore();

    stateMachine.on(Event.GROUP_CREATION_REQUESTED)
        .states(State.STARTED_NO_GROUP).transitionTo(State.STARTED_CREATING_GROUP)
        .states(
            State.IDLE_NO_GROUP,
            State.IDLE_GROUP_ACTIVE,
            State.IDLE_PEER_ACTIVE,
            State.STARTED_CREATING_GROUP,
            State.STARTED_GROUP_ACTIVE,
            State.STARTED_PEER_ACTIVE,
            State.TEARDOWN,
            State.P2P_DISABLED).reject(new IllegalStateException());

    stateMachine.on(Event.GROUP_CREATION_REQUEST_FAILED)
        .states(State.STARTED_CREATING_GROUP).transitionWithWarning(State.STARTED_NO_GROUP)
        .states(
            State.IDLE_NO_GROUP,
            State.IDLE_GROUP_ACTIVE,
            State.IDLE_PEER_ACTIVE,
            State.STARTED_NO_GROUP,
            State.STARTED_GROUP_ACTIVE,
            State.STARTED_PEER_ACTIVE,
            State.TEARDOWN,
            State.P2P_DISABLED).reject(new IllegalStateException());

    stateMachine.on(Event.GROUP_ACTIVE_NO_PEER)
        .states(
            State.IDLE_NO_GROUP,
            State.IDLE_PEER_ACTIVE,
            State.P2P_DISABLED).transitionTo(State.IDLE_GROUP_ACTIVE)
        .states(
            State.STARTED_CREATING_GROUP,
            State.STARTED_PEER_ACTIVE).transitionTo(State.STARTED_GROUP_ACTIVE)
        .states(State.STARTED_NO_GROUP).transitionWithWarning(State.STARTED_GROUP_ACTIVE)
        .states(
            State.IDLE_GROUP_ACTIVE,
            State.STARTED_GROUP_ACTIVE,
            State.TEARDOWN).ignore();

    stateMachine.on(Event.PEER_ACTIVE)
        .states(
            State.IDLE_NO_GROUP,
            State.IDLE_GROUP_ACTIVE,
            State.P2P_DISABLED).transitionTo(State.IDLE_PEER_ACTIVE)
        .states(State.STARTED_GROUP_ACTIVE).transitionTo(State.STARTED_PEER_ACTIVE)
        .states(
            State.STARTED_NO_GROUP,
            State.STARTED_CREATING_GROUP).transitionWithWarning(State.STARTED_PEER_ACTIVE)
        .states(State.IDLE_PEER_ACTIVE, State.STARTED_PEER_ACTIVE, State.TEARDOWN).ignore();

    stateMachine.on(Event.TIME_OUT)
        .states(
            State.STARTED_NO_GROUP,
            State.STARTED_CREATING_GROUP).transitionWithWarning(State.TEARDOWN)
        .states(State.TEARDOWN).transitionWithWarning(State.IDLE_NO_GROUP)
        .states(
            State.IDLE_NO_GROUP,
            State.IDLE_GROUP_ACTIVE,
            State.IDLE_PEER_ACTIVE,
            State.STARTED_GROUP_ACTIVE,
            State.STARTED_PEER_ACTIVE,
            State.P2P_DISABLED).reject(new IllegalStateException());

    stateMachine.setTimeout(
        CONNECTION_TIMEOUT_MILLIS, State.STARTED_NO_GROUP, State.STARTED_CREATING_GROUP);
    stateMachine.setTimeout(TEARDOWN_WAIT_TIMEOUT_MS, State.TEARDOWN);

    stateMachine.checkRuleCompleteness(State.values(), Event.values());
  }
}
