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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Handler;
import android.os.Looper;
import com.google.vr180.common.wifi.WifiDirectManager.Event;
import com.google.vr180.common.wifi.WifiDirectManager.State;
import com.google.vr180.common.wifi.WifiNetworkManager.Listener;
import com.google.vr180.common.wifi.WifiNetworkManager.WifiDirectDisabledException;
import com.google.vr180.common.wifi.WifiNetworkManager.WifiDirectNotSupportedException;
import com.google.vr180.common.wifi.WifiNetworkManager.WifiDisabledException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class WifiDirectManagerTest {

  private static final String TEST_SSID = "TEST_SSID";
  private static final String TEST_PASSPHRASE = "password";

  private static final String TEST_MAC_ADDRESS = "06-00-00-00-00-00";
  private static final String TEST_IP_ADDRESS = "192.168.1.1";

  private static final int CAMERA_5G_CHANNEL = 44;
  private static final int PHONE_5G_CHANNEL = 48;
  private static final int CAMERA_24G_CHANNEL = 11;
  private static final int PHONE_24G_CHANNEL = 1;
  private static final int DEFAULT_24G_CHANNEL = 6;
  private static final int DEFAULT_5G_CHANNEL = 40;

  private Context mockContext;

  private PackageManager mockPackageManager;
  private WifiManager mockWifiManager;
  private WifiLock mockWifiLock;
  private WifiP2pManager mockWifiP2pManager;

  private ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor;

  private InetAddress groupOwnerAddress;

  private WifiP2pGroup mockWifiP2pGroup;
  private WifiP2pInfo mockWifiP2pInfo;
  private NetworkInfo mockNetworkInfo;
  private Handler mainHandler = new Handler(RuntimeEnvironment.application.getMainLooper());

  // Broadcast intent answer indicating that the GO was created.
  private Answer<Void> groupCreatedAnswer =
      invocationOnMock -> {
        Intent mockIntent = Mockito.mock(Intent.class);
        when(mockIntent.getAction()).thenReturn(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        when(mockNetworkInfo.isConnected()).thenReturn(true);
        when(mockNetworkInfo.getState()).thenReturn(NetworkInfo.State.CONNECTED);
        when(mockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO))
            .thenReturn(mockNetworkInfo);

        mockWifiP2pInfo.groupOwnerAddress = groupOwnerAddress;
        mockWifiP2pInfo.groupFormed = true;
        mockWifiP2pInfo.isGroupOwner = true;
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO))
            .thenReturn(mockWifiP2pInfo);

        when(mockWifiP2pGroup.isGroupOwner()).thenReturn(true);
        when(mockWifiP2pGroup.getNetworkName()).thenReturn(TEST_SSID);
        when(mockWifiP2pGroup.getPassphrase()).thenReturn(TEST_PASSPHRASE);
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP))
            .thenReturn(mockWifiP2pGroup);

        WifiP2pDevice mockOwnerDevice = Mockito.mock(WifiP2pDevice.class);
        mockOwnerDevice.deviceAddress = TEST_MAC_ADDRESS;
        when(mockWifiP2pGroup.getOwner()).thenReturn(mockOwnerDevice);

        if (broadcastReceiverCaptor != null) {
          BroadcastReceiver broadcastReceiver = broadcastReceiverCaptor.getValue();
          mainHandler.post(() -> broadcastReceiver.onReceive(mockContext, mockIntent));
        }
        return null;
      };

  // Broadcast intent answer indicating that the GO was created.
  private Answer<Void> groupRemovedAnswer =
      invocationOnMock -> {
        Intent mockIntent = Mockito.mock(Intent.class);
        when(mockIntent.getAction()).thenReturn(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        when(mockNetworkInfo.isConnected()).thenReturn(false);
        when(mockNetworkInfo.getState()).thenReturn(NetworkInfo.State.DISCONNECTED);
        when(mockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.DISCONNECTED);
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO))
            .thenReturn(mockNetworkInfo);

        mockWifiP2pInfo.groupOwnerAddress = null;
        mockWifiP2pInfo.groupFormed = false;
        mockWifiP2pInfo.isGroupOwner = false;
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO))
            .thenReturn(mockWifiP2pInfo);

        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)).thenReturn(null);

        if (broadcastReceiverCaptor != null) {
          BroadcastReceiver broadcastReceiver = broadcastReceiverCaptor.getValue();
          mainHandler.post(() -> broadcastReceiver.onReceive(mockContext, mockIntent));
        }
        return null;
      };

  // Broadcast intent answer indicating that a client (camera) has connected to the group.
  private Answer<Void> clientConnectedAnswer =
      invocationOnMock -> {
        Intent mockIntent = Mockito.mock(Intent.class);
        when(mockIntent.getAction()).thenReturn(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        Collection<WifiP2pDevice> clientList = new ArrayList<>();
        clientList.add(new WifiP2pDevice());
        when(mockWifiP2pGroup.getClientList()).thenReturn(clientList);
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP))
            .thenReturn(mockWifiP2pGroup);
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO))
            .thenReturn(mockWifiP2pInfo);
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO))
            .thenReturn(mockNetworkInfo);

        if (broadcastReceiverCaptor != null) {
          BroadcastReceiver broadcastReceiver = broadcastReceiverCaptor.getValue();
          mainHandler.post(() -> broadcastReceiver.onReceive(mockContext, mockIntent));
        }
        return null;
      };

  // Broadcast intent answer indicating that a client (camera) is no longer connected to the group.
  private Answer<Void> clientDisconnectAnswer =
      invocationOnMock -> {
        Intent mockIntent = Mockito.mock(Intent.class);
        when(mockIntent.getAction()).thenReturn(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        Collection<WifiP2pDevice> clientList = new ArrayList<>();
        when(mockWifiP2pGroup.getClientList()).thenReturn(clientList);
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP))
            .thenReturn(mockWifiP2pGroup);
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO))
            .thenReturn(mockWifiP2pInfo);
        when(mockIntent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO))
            .thenReturn(mockNetworkInfo);

        if (broadcastReceiverCaptor != null) {
          BroadcastReceiver broadcastReceiver = broadcastReceiverCaptor.getValue();
          mainHandler.post(() -> broadcastReceiver.onReceive(mockContext, mockIntent));
        }
        return null;
      };

  @Before
  public void setup() throws UnknownHostException {
    // Mock required Context behavior.
    mockContext = Mockito.mock(Context.class);

    // Mock extras for WIFI_P2P_CONNECTION_CHANGED_ACTION.
    mockNetworkInfo = Mockito.mock(NetworkInfo.class);
    mockWifiP2pGroup = Mockito.mock(WifiP2pGroup.class);
    mockWifiP2pInfo = Mockito.mock(WifiP2pInfo.class);

    // Mock required Package Manager behavior.
    mockPackageManager = Mockito.mock(PackageManager.class);
    when(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)).thenReturn(true);
    when(mockContext.getPackageManager()).thenReturn(mockPackageManager);

    // Mock required WifiManager behavior.
    mockWifiManager = Mockito.mock(WifiManager.class);
    when(mockWifiManager.isWifiEnabled()).thenReturn(true);
    when(mockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
    mockWifiLock = Mockito.mock(WifiLock.class);
    when(mockWifiManager.createWifiLock(anyInt(), anyString())).thenReturn(mockWifiLock);
    when(mockContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(mockWifiManager);

    // Mock required WifiP2pManager behavior.
    mockWifiP2pManager = Mockito.mock(WifiP2pManager.class);
    Channel mockChannel = Mockito.mock(Channel.class);
    when(mockWifiP2pManager.initialize(any(), any(Looper.class), any())).thenReturn(mockChannel);
    when(mockContext.getSystemService(Context.WIFI_P2P_SERVICE)).thenReturn(mockWifiP2pManager);

    // When WifiClientManager registers a broadcast receiver we must capture it.
    broadcastReceiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver.class);
    when(mockContext.registerReceiver(broadcastReceiverCaptor.capture(), any(), any(), any()))
        .thenReturn(Mockito.mock(Intent.class));

    // Initialize expected InetAddress values.
    groupOwnerAddress = InetAddress.getByName(TEST_IP_ADDRESS);
  }

  /**
   * Verify that a request for a wifi network will fail with WifiDisabledException if Wifi is
   * disabled on the phone.
   */
  @Test
  public void testWifiDisabledError() {
    // Create WifiDirectManager.
    WifiDirectManager manager = createWifiDirectManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // Verify that Wifi is reported as enabled.
    assertThat(manager.isWifiEnabled()).isTrue();

    // Android WifiManager to report that wifi is disabled.
    when(mockWifiManager.isWifiEnabled()).thenReturn(false);
    when(mockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);

    // Verify that Wifi is reported as disabled.
    assertThat(manager.isWifiEnabled()).isFalse();

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.createHotspot(null, mockListener);

    // Request fails upfront
    assertThat(manager.isBusy()).isFalse();

    // Verify that the Wifi lock was never acquired.
    verify(mockWifiLock, never()).acquire();

    // Verify that the receiver was never registered.
    verify(mockContext, never()).registerReceiver(any(BroadcastReceiver.class), any());

    // Verify that we are called back with onError for WifiDisabled.
    verify(mockListener).onError(any(WifiDisabledException.class));

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();

    // Verify that the receiver was never unregistered.
    verify(mockContext, never()).unregisterReceiver(any(BroadcastReceiver.class));
  }

  /**
   * Verify that a request for a wifi network will fail with WifiDirectNotSupportedException if Wifi
   * Direct is not supported on this phone.
   */
  @Test
  public void testWifiDirectNotSupportedError() {
    // When asked if the devices supports Wifi Direct, package manager returns false.
    when(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)).thenReturn(false);

    // Create WifiDirectManager.
    WifiDirectManager manager = createWifiDirectManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // Verify that Wifi Direct is reported as not supported.
    assertThat(manager.isWifiDirectSupported()).isFalse();

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.createHotspot(null, mockListener);

    // The request is failed upfront
    assertThat(manager.isBusy()).isFalse();

    // Verify that the Wifi lock was never acquired.
    verify(mockWifiLock, never()).acquire();

    // Verify that we are called back with onError for WifiDisabled.
    verify(mockListener).onError(any(WifiDirectNotSupportedException.class));

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();
  }

  /**
   * Verify that a request for a wifi network will fail with WifiDirectDisabledException if Wifi
   * Direct is not enabled on this phone.
   */
  @Test
  public void testWifiDirectDisabledError() {
    // Create WifiDirectManager.
    WifiDirectManager manager = createWifiDirectManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // Verify that Wifi Direct is reported as supported.
    assertThat(manager.isWifiDirectSupported()).isTrue();

    // Verify that Wifi is reported as enabled.
    assertThat(manager.isWifiEnabled()).isTrue();

    // Signal P2P disabled.
    sendP2pStateChanged(false);

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.createHotspot(null, mockListener);

    // Request is rejected upfront
    assertThat(manager.isBusy()).isFalse();

    // Verify that we are called back with onError for WifiDirectNotEnabled.
    verify(mockListener).onError(any(WifiDirectDisabledException.class));

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();

    // Verify that the Wifi lock was never acquired.
    verify(mockWifiLock, never()).acquire();
  }

  /**
   * Verify that a request for a wifi network will fail with WifiConfigurationException if we fail
   * to create the wifi direct GO.
   */
  @Test
  public void testWifiCreateGroupError() {
    // Create WifiDirectManager.
    WifiDirectManager manager = createWifiDirectManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // When WifiDirectManager asks to create the group, we respond with an error.
    doAnswer(
            invocationOnMock -> {
              Object[] args = invocationOnMock.getArguments();
              ActionListener listener = (ActionListener) args[1];
              listener.onFailure(WifiP2pManager.ERROR);
              return null;
            })
        .when(mockWifiP2pManager)
        .createGroup(any(), any());

    // Signal P2P enabled.
    sendP2pStateChanged(true);

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.createHotspot(null, mockListener);
    assertThat(manager.isBusy()).isTrue();

    // Expect to see the wifi lock acquired.
    verify(mockWifiLock).acquire();

    // Still busy. We will retry
    assertThat(manager.isBusy()).isTrue();

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();

    // Expect to see the wifi lock released.
    verify(mockWifiLock).release();
  }

  /** Validate the normal, successful, connect and disconnect flow for WifiDirectManager. */
  @Test
  public void testConnectNormalDisconnect() {
    // Create WifiDirectManager.
    WifiDirectManager manager = createWifiDirectManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    Listener mockListener = Mockito.mock(Listener.class);

    // When WifiDirectManager requests to create the GO, answer appropriately.
    doAnswer(groupCreatedAnswer).when(mockWifiP2pManager).createGroup(any(), any());

    // When the listener is called with network info, simulate a connecting client (camera).
    doAnswer(clientConnectedAnswer)
        .when(mockListener)
        .onNetworkInfoAvailable(anyString(), anyString(), anyString(), anyString());

    // Signal P2P enabled.
    sendP2pStateChanged(true);

    // Request network connection.
    manager.createHotspot(null, mockListener);
    assertThat(manager.isBusy()).isTrue();

    // Expect to see the wifi lock acquired.
    verify(mockWifiLock).acquire();

    // Expect a request to create the GO.
    verify(mockWifiP2pManager).createGroup(any(), any());

    // Expect the listener to be called back with network info.
    verify(mockListener)
        .onNetworkInfoAvailable(
            eq(TEST_SSID),
            eq(TEST_PASSPHRASE),
            eq(TEST_IP_ADDRESS),
            eq(TEST_MAC_ADDRESS));

    // Expect the listener to be called back indicating we are connected.
    verify(mockListener).onConnect();

    // Verify that we did not see any errors.
    verify(mockListener, never()).onError(any());

    // Verify that we did not see the session disconnect.
    verify(mockListener, never()).onDisconnect();

    // Still busy until we release the network.
    assertThat(manager.isBusy()).isTrue();

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();

    // Expect to see the wifi lock released.
    verify(mockWifiLock).release();
  }

  /** Validate handling of a disconnect after the wifi session has been successfully connected. */
  @Test
  public void testConnectAndUnexpectedDisconnect() {

    // Create WifiDirectManager.
    WifiDirectManager manager = createWifiDirectManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    Listener mockListener = Mockito.mock(Listener.class);

    // When WifiDirectManager requests to create the GO, answer appropriately.
    doAnswer(groupCreatedAnswer).when(mockWifiP2pManager).createGroup(any(), any());

    // When the listener is called with network info, simulate a connecting client.
    doAnswer(clientConnectedAnswer)
        .when(mockListener)
        .onNetworkInfoAvailable(anyString(), anyString(), anyString(), anyString());

    // When the listener is called for onConnect, simulate a disconnecting client.
    doAnswer(clientDisconnectAnswer).when(mockListener).onConnect();

    // Signal P2P enabled.
    sendP2pStateChanged(true);

    // Request network connection.
    manager.createHotspot(null, mockListener);
    assertThat(manager.isBusy()).isTrue();

    // Expect to see the wifi lock acquired.
    verify(mockWifiLock).acquire();

    // Expect a request to create the GO.
    verify(mockWifiP2pManager).createGroup(any(), any());

    // Expect the listener to be called back with network info.
    verify(mockListener)
        .onNetworkInfoAvailable(
            eq(TEST_SSID),
            eq(TEST_PASSPHRASE),
            eq(TEST_IP_ADDRESS),
            eq(TEST_MAC_ADDRESS));

    // Expect the listener to be called back indicating we are connected.
    verify(mockListener).onConnect();

    // Verify that we did not see any errors.
    verify(mockListener, never()).onError(any());

    // Expect the listener to be called back indicating we are no longer connected.
    verify(mockListener).onDisconnect();

    // Still busy until we release the network.
    assertThat(manager.isBusy()).isTrue();

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();

    // Expect to see the wifi lock released.
    verify(mockWifiLock).release();
  }

  @Test
  public void testGoldenPathWithRealEvents() throws Throwable {
    // Create WifiDirectManager.
    Listener mockListener = Mockito.mock(Listener.class);
    WifiDirectManager manager = createWifiDirectManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    assertThat(manager.getState()).isEqualTo(State.IDLE_NO_GROUP);

    manager.createHotspot(null, mockListener);
    assertThat(manager.getState()).isEqualTo(State.STARTED_CREATING_GROUP);
    verify(mockWifiP2pManager).createGroup(any(), any());

    groupCreatedAnswer.answer(null);
    assertThat(manager.getState()).isEqualTo(State.STARTED_GROUP_ACTIVE);
    verify(mockListener).onNetworkInfoAvailable(any(), any(), any(), any());

    clientConnectedAnswer.answer(null);
    assertThat(manager.getState()).isEqualTo(State.STARTED_PEER_ACTIVE);
    verify(mockListener).onConnect();

    clientDisconnectAnswer.answer(null);
    assertThat(manager.getState()).isEqualTo(State.STARTED_GROUP_ACTIVE);
    verify(mockListener).onDisconnect();

    clientConnectedAnswer.answer(null);
    assertThat(manager.getState()).isEqualTo(State.STARTED_PEER_ACTIVE);
    verify(mockListener, times(2)).onConnect();

    manager.releaseNetwork();
    assertThat(manager.getState()).isEqualTo(State.TEARDOWN);
    verify(mockWifiP2pManager).removeGroup(any(), any());
    verify(mockListener, never()).onError(any());

    groupRemovedAnswer.answer(null);
    assertThat(manager.getState()).isEqualTo(State.IDLE_NO_GROUP);
  }

  @Test
  public void testGoldenPathWithFakeEvents() {
    // Create WifiDirectManager.
    WifiDirectManager manager = createWifiDirectManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    assertThat(manager.getState()).isEqualTo(State.IDLE_NO_GROUP);

    manager.getStateMachine().handleEvent(Event.START);
    assertThat(manager.getState()).isEqualTo(State.STARTED_CREATING_GROUP);
    verify(mockWifiP2pManager).createGroup(any(), any());

    manager.getStateMachine().handleEvent(Event.GROUP_ACTIVE_NO_PEER);
    assertThat(manager.getState()).isEqualTo(State.STARTED_GROUP_ACTIVE);

    manager.getStateMachine().handleEvent(Event.PEER_ACTIVE);
    assertThat(manager.getState()).isEqualTo(State.STARTED_PEER_ACTIVE);

    manager.getStateMachine().handleEvent(Event.GROUP_ACTIVE_NO_PEER);
    assertThat(manager.getState()).isEqualTo(State.STARTED_GROUP_ACTIVE);

    manager.getStateMachine().handleEvent(Event.PEER_ACTIVE);
    assertThat(manager.getState()).isEqualTo(State.STARTED_PEER_ACTIVE);

    manager.getStateMachine().handleEvent(Event.STOP);
    assertThat(manager.getState()).isEqualTo(State.TEARDOWN);

    manager.getStateMachine().handleEvent(Event.NO_GROUP);
    assertThat(manager.getState()).isEqualTo(State.IDLE_NO_GROUP);
  }

  @Test
  public void testChooseOperatingChannel() {

    /* For phones that support 5GHz */
    // Honor client preference
    assertThat(WifiDirectManager.chooseOperatingChannel(
        PHONE_5G_CHANNEL, // preferredChannel
        0, // infraChannel
        true, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(PHONE_5G_CHANNEL);

    // Honor client preference
    assertThat(WifiDirectManager.chooseOperatingChannel(
        PHONE_5G_CHANNEL, // preferredChannel
        CAMERA_24G_CHANNEL, // infraChannel
        true, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(PHONE_5G_CHANNEL);

    // Honor client preference
    assertThat(WifiDirectManager.chooseOperatingChannel(
        PHONE_5G_CHANNEL, // preferredChannel
        CAMERA_5G_CHANNEL, // infraChannel
        true, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(PHONE_5G_CHANNEL);

    // Use camera active 5G channel if client doesn't have preference
    assertThat(WifiDirectManager.chooseOperatingChannel(
        0, // preferredChannel
        CAMERA_5G_CHANNEL, // infraChannel
        true, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(CAMERA_5G_CHANNEL);

    // Use default 5G channel if neither of the devices has 5G wifi connection
    assertThat(WifiDirectManager.chooseOperatingChannel(
        0, // preferredChannel
        CAMERA_24G_CHANNEL, // infraChannel
        true, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(DEFAULT_5G_CHANNEL);

    // Use camera 2.4G channel if neither of the devices has 5G wifi connection and client does not
    // prefer 5G.
    assertThat(WifiDirectManager.chooseOperatingChannel(
        0, // preferredChannel
        CAMERA_24G_CHANNEL, // infraChannel
        true, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        false, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(CAMERA_24G_CHANNEL);

    // Use default 5G channel if neither of the devices has 5G wifi connection
    assertThat(WifiDirectManager.chooseOperatingChannel(
        0, // preferredChannel
        0, // infraChannel
        true, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(DEFAULT_5G_CHANNEL);

    /* For phones that do not support 5GHz */
    // Honor client preference
    assertThat(WifiDirectManager.chooseOperatingChannel(
        PHONE_24G_CHANNEL, // preferredChannel
        CAMERA_24G_CHANNEL, // infraChannel
        false, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(PHONE_24G_CHANNEL);

    // Honor client preference
    assertThat(WifiDirectManager.chooseOperatingChannel(
        PHONE_24G_CHANNEL, // preferredChannel
        CAMERA_5G_CHANNEL, // infraChannel
        false, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(PHONE_24G_CHANNEL);

    // Honor client preference
    assertThat(WifiDirectManager.chooseOperatingChannel(
        PHONE_24G_CHANNEL, // preferredChannel
        0, // infraChannel
        false, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(PHONE_24G_CHANNEL);

    // Use Camera's wifi channel
    assertThat(WifiDirectManager.chooseOperatingChannel(
        0, // preferredChannel
        CAMERA_24G_CHANNEL, // infraChannel
        false, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(CAMERA_24G_CHANNEL);

    // Use default 2.4GHz wifi channel
    assertThat(WifiDirectManager.chooseOperatingChannel(
        0, // preferredChannel
        CAMERA_5G_CHANNEL, // infraChannel
        false, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(DEFAULT_24G_CHANNEL);

    // Use default 2.4GHz wifi channel
    assertThat(WifiDirectManager.chooseOperatingChannel(
        0, // preferredChannel
        0, // infraChannel
        false, // clientSupports5Ghz,
        true, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(DEFAULT_24G_CHANNEL);

    /* For 2.4GHz-only camera */
    // Use default 2.4GHz wifi channel
    assertThat(WifiDirectManager.chooseOperatingChannel(
        0, // preferredChannel
        0, // infraChannel
        true, // clientSupports5Ghz,
        false, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(DEFAULT_24G_CHANNEL);

    // Use client preference
    assertThat(WifiDirectManager.chooseOperatingChannel(
        PHONE_24G_CHANNEL, // preferredChannel
        0, // infraChannel
        true, // clientSupports5Ghz,
        false, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(PHONE_24G_CHANNEL);

    // Use default 2.4GHz wifi channel
    assertThat(WifiDirectManager.chooseOperatingChannel(
        PHONE_5G_CHANNEL, // preferredChannel
        0, // infraChannel
        true, // clientSupports5Ghz,
        false, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(DEFAULT_24G_CHANNEL);

    // Use camera active channel
    assertThat(WifiDirectManager.chooseOperatingChannel(
        0, // preferredChannel
        CAMERA_24G_CHANNEL, // infraChannel
        true, // clientSupports5Ghz,
        false, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(CAMERA_24G_CHANNEL);

    // Use client preference
    assertThat(WifiDirectManager.chooseOperatingChannel(
        PHONE_24G_CHANNEL, // preferredChannel
        CAMERA_24G_CHANNEL, // infraChannel
        true, // clientSupports5Ghz,
        false, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(PHONE_24G_CHANNEL);

    // Use camera active channel
    assertThat(WifiDirectManager.chooseOperatingChannel(
        PHONE_5G_CHANNEL, // preferredChannel
        CAMERA_24G_CHANNEL, // infraChannel
        true, // clientSupports5Ghz,
        false, // selfSupport5Ghz,
        true, // clientPrefers5Ghz,
        DEFAULT_5G_CHANNEL, // default5GhzChannel
        DEFAULT_24G_CHANNEL // default24GhzChannel)
    )).isEqualTo(CAMERA_24G_CHANNEL);

  }

  private WifiDirectManager createWifiDirectManager() {
    return new WifiDirectManager(mockContext, mainHandler, mainHandler);
  }

  private void sendP2pStateChanged(boolean enabled) {
    BroadcastReceiver broadcastReceiver = broadcastReceiverCaptor.getValue();
    Intent mockIntent = Mockito.mock(Intent.class);
    when(mockIntent.getAction()).thenReturn(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    when(mockIntent.getIntExtra(eq(WifiP2pManager.EXTRA_WIFI_STATE), anyInt()))
        .thenReturn(
            enabled
                ? WifiP2pManager.WIFI_P2P_STATE_ENABLED
                : WifiP2pManager.WIFI_P2P_STATE_DISABLED);
    mainHandler.post(() -> broadcastReceiver.onReceive(mockContext, mockIntent));
  }
}
