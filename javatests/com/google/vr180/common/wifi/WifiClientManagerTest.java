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
import static org.mockito.Matchers.anyBoolean;
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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Handler;
import android.os.SystemClock;
import com.google.vr180.common.wifi.WifiNetworkManager.Listener;
import com.google.vr180.common.wifi.WifiNetworkManager.WifiBusyException;
import com.google.vr180.common.wifi.WifiNetworkManager.WifiConfigurationException;
import com.google.vr180.common.wifi.WifiNetworkManager.WifiDisabledException;
import com.google.vr180.common.wifi.WifiNetworkManager.WifiTimeoutException;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class WifiClientManagerTest {
  private static final String TEST_SSID = "TEST_SSID";
  private static final String TEST_PASSPHRASE = "password";

  private Context mockContext;

  private WifiManager mockWifiManager;
  private WifiLock mockWifiLock;
  private ConnectivityManager mockConnectivityManager;

  private ArgumentCaptor<BroadcastReceiver> broadcastReceiverCaptor;

  // Broadcast intent answer for request to disconnect network.
  private final Answer<Boolean> disconnectAnswer =
      invocationOnMock -> {
        Intent mockIntent = Mockito.mock(Intent.class);
        NetworkInfo mockNetworkInfo = Mockito.mock(NetworkInfo.class);
        when(mockNetworkInfo.getState()).thenReturn(NetworkInfo.State.DISCONNECTED);
        when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);
        when(mockIntent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO))
            .thenReturn(mockNetworkInfo);
        when(mockIntent.getAction()).thenReturn(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        if (broadcastReceiverCaptor != null) {
          BroadcastReceiver broadcastReceiver = broadcastReceiverCaptor.getValue();
          Handler handler = new Handler(RuntimeEnvironment.application.getMainLooper());
          handler.post(() -> broadcastReceiver.onReceive(mockContext, mockIntent));
        }
        return true;
      };

  // Broadcast intent answer for request to enable network.
  private final Answer<Boolean> enableNetworkAnswer =
      invocationOnMock -> {
        Intent mockIntent = Mockito.mock(Intent.class);
        NetworkInfo mockNetworkInfo = Mockito.mock(NetworkInfo.class);
        when(mockNetworkInfo.getState()).thenReturn(NetworkInfo.State.CONNECTED);
        when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);
        WifiInfo mockWifiInfo = Mockito.mock(WifiInfo.class);
        when(mockWifiInfo.getSSID()).thenReturn(TEST_SSID);
        when(mockIntent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO))
            .thenReturn(mockNetworkInfo);
        when(mockWifiManager.getConnectionInfo()).thenReturn(mockWifiInfo);
        when(mockIntent.getAction()).thenReturn(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        if (broadcastReceiverCaptor != null) {
          BroadcastReceiver broadcastReceiver = broadcastReceiverCaptor.getValue();
          Handler handler = new Handler(RuntimeEnvironment.application.getMainLooper());
          handler.post(() -> broadcastReceiver.onReceive(mockContext, mockIntent));
        }
        return true;
      };

  @Before
  public void setup() throws IOException {
    // Mock required Context behavior.
    mockContext = Mockito.mock(Context.class);

    // Mock required WifiManager behavior.
    mockWifiManager = Mockito.mock(WifiManager.class);
    when(mockWifiManager.isWifiEnabled()).thenReturn(true);
    mockWifiLock = Mockito.mock(WifiLock.class);
    when(mockWifiManager.createWifiLock(anyInt(), anyString())).thenReturn(mockWifiLock);

    when(mockWifiManager.saveConfiguration()).thenReturn(true);
    when(mockWifiManager.enableNetwork(anyInt(), eq(true))).thenReturn(true);
    when(mockWifiManager.removeNetwork(anyInt())).thenReturn(true);

    ArrayList<WifiConfiguration> configuredNetworks = new ArrayList<WifiConfiguration>();
    WifiConfiguration configuredNetwork = new WifiConfiguration();
    configuredNetwork.SSID = TEST_SSID;
    configuredNetwork.networkId = 1;
    configuredNetworks.add(configuredNetwork);
    when(mockWifiManager.getConfiguredNetworks()).thenReturn(configuredNetworks);

    when(mockContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(mockWifiManager);

    // Mock required ConnectivityManager behavior.
    mockConnectivityManager = Mockito.mock(ConnectivityManager.class);
    when(mockConnectivityManager.getAllNetworks())
        .thenReturn(new Network[] {Mockito.mock(Network.class)});
    NetworkInfo mockNetworkInfo = Mockito.mock(NetworkInfo.class);
    when(mockNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);
    when(mockConnectivityManager.getNetworkInfo(any(Network.class))).thenReturn(mockNetworkInfo);
    when(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
        .thenReturn(mockConnectivityManager);

    // When WifiClientManager registers a broadcast receiver we must capture it.
    broadcastReceiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver.class);
    when(mockContext.registerReceiver(broadcastReceiverCaptor.capture(), any(), any(), any()))
        .thenReturn(Mockito.mock(Intent.class));

    // When WifiClientManager unregisters the broadcast receiver, we must clear it.
    doAnswer(
            invocationOnMock -> {
              // No longer sending broadcast messages.
              broadcastReceiverCaptor = null;
              return null;
            })
        .when(mockContext)
        .unregisterReceiver(any());
  }

  /**
   * Verify that a request for a wifi network will fail with WifiBusyException if WifiClientManager
   * is already in use.
   */
  @Test
  public void testWifiBusyError() {
    // Create WifiClientManager.
    WifiClientManager manager = createWifiClientManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // Request network connection.
    Listener mockListener1 = Mockito.mock(Listener.class);
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener1);
    assertThat(manager.isBusy()).isTrue();

    // Expect to see the wifi lock acquired.
    verify(mockWifiLock).acquire();

    // Request network connection again, expecting it to fail with WifiBusyException.
    Listener mockListener2 = Mockito.mock(Listener.class);
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener2);
    assertThat(manager.isBusy()).isTrue();

    // Verify that no error is issued to the first listener.
    verify(mockListener1, never()).onError(any());

    // Verify that the second listener is called with WifiBusyException.
    verify(mockListener2).onError(any(WifiBusyException.class));

    // Still busy until we release the network.
    assertThat(manager.isBusy()).isTrue();

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();

    // Expect to see the wifi lock released.
    verify(mockWifiLock).release();
  }

  /**
   * Verify that a request for a wifi network will fail with WifiDisabledException if Wifi is
   * disabled on the phone.
   */
  @Test
  public void testWifiDisabledError() {
    // Create WifiClientManager.
    WifiClientManager manager = createWifiClientManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // Verify that Wifi is reported as enabled.
    assertThat(manager.isWifiEnabled()).isTrue();

    // Android WifiManager to report that wifi is disabled.
    when(mockWifiManager.isWifiEnabled()).thenReturn(false);

    // Verify that Wifi is reported as disabled.
    assertThat(manager.isWifiEnabled()).isFalse();

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener);

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

  @Test
  public void testWifiConfigurationAddNetworkError() {
    // Create WifiClientManager.
    WifiClientManager manager = createWifiClientManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // Unable to add network.
    when(mockWifiManager.addNetwork(any())).thenReturn(-1);

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener);

    // Verify that the Wifi lock was never acquired.
    verify(mockWifiLock, never()).acquire();

    // Verify that we are called back with onError for WifiConfigurationException.
    verify(mockListener).onError(any(WifiConfigurationException.class));

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();
  }

  @Test
  public void testWifiConfigurationSaveConfigurationError() {
    // Create WifiClientManager.
    WifiClientManager manager = createWifiClientManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // Unable to save configuration.
    when(mockWifiManager.saveConfiguration()).thenReturn(false);

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener);

    // Verify that the Wifi lock was never acquired.
    verify(mockWifiLock, never()).acquire();

    // Verify that we are called back with onError for WifiConfigurationException.
    verify(mockListener).onError(any(WifiConfigurationException.class));

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();
  }

  @Test
  public void testWifiConfigurationNullConfiguredNetworksError() {
    // Create WifiClientManager.
    WifiClientManager manager = createWifiClientManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // Null list of configurations.
    when(mockWifiManager.getConfiguredNetworks()).thenReturn(null);

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener);

    // Verify that the Wifi lock was never acquired.
    verify(mockWifiLock, never()).acquire();

    // Verify that we are called back with onError for WifiConfigurationException.
    verify(mockListener).onError(any(WifiConfigurationException.class));

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();
  }

  @Test
  public void testWifiConfigurationEmptyConfiguredNetworksError() {
    // Create WifiClientManager.
    WifiClientManager manager = createWifiClientManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // Empty list of configurations.
    when(mockWifiManager.getConfiguredNetworks()).thenReturn(new ArrayList<>());

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener);

    // Verify that the Wifi lock was never acquired.
    verify(mockWifiLock, never()).acquire();

    // Verify that we are called back with onError for WifiConfigurationException.
    verify(mockListener).onError(any(WifiConfigurationException.class));

    // Release network connection.
    manager.releaseNetwork();
    assertThat(manager.isBusy()).isFalse();
  }

  /** Validate the normal, successful, connect and disconnect flow for WifiClientManager. */
  @Test
  public void testConnectNormalDisconnect() throws IOException {
    // Create WifiClientManager.
    WifiClientManager manager = createWifiClientManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // When WifiClientManager requests to disconnect, answer appropriately.
    doAnswer(disconnectAnswer).when(mockWifiManager).disconnect();

    // When WifiClientManager requests to enable the target network, answer appropriately.
    doAnswer(enableNetworkAnswer).when(mockWifiManager).enableNetwork(anyInt(), anyBoolean());

    // When we receive a bind request, succeed.
    when(mockConnectivityManager.bindProcessToNetwork(any(Network.class))).thenReturn(true);

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener);
    assertThat(manager.isBusy()).isTrue();

    // Expect to see the wifi lock acquired.
    verify(mockWifiLock).acquire();

    // Expect to see a request to disconnect.
    verify(mockWifiManager).disconnect();

    // Expect to see a request to enable the target network.
    verify(mockWifiManager).enableNetwork(anyInt(), anyBoolean());

    // Expect to see the network bound using the platform appropriate API.
    verify(mockConnectivityManager).bindProcessToNetwork(any(Network.class));

    // Verify that we did not see any errors.
    verify(mockListener, never()).onError(any());

    // Expect the listener to be called back indicating we are connected.
    verify(mockListener).onConnect();

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
  public void testConnectAndUnexpectedDisconnect() throws IOException {
    // Create WifiClientManager.
    WifiClientManager manager = createWifiClientManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    Listener mockListener = Mockito.mock(Listener.class);

    // When WifiClientManager requests to disconnect, answer appropriately.
    doAnswer(disconnectAnswer).when(mockWifiManager).disconnect();

    // When WifiClientManager requests to enable the target network, answer appropriately.
    doAnswer(enableNetworkAnswer).when(mockWifiManager).enableNetwork(anyInt(), anyBoolean());

    // When we receive a bind request, succeed.
    when(mockConnectivityManager.bindProcessToNetwork(any(Network.class))).thenReturn(true);

    // When the listener is called for onConnect, simulate network disconnect.
    doAnswer(disconnectAnswer).when(mockListener).onConnect();

    // Request network connection.
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener);

    // Expect to see the wifi lock acquired.
    verify(mockWifiLock).acquire();

    // Expect to see a request to disconnect.
    verify(mockWifiManager).disconnect();

    // Expect to see a request to enable the target network.
    verify(mockWifiManager).enableNetwork(anyInt(), anyBoolean());

    // Expect to see the network bounded and then unbounded
    verify(mockConnectivityManager, times(2)).bindProcessToNetwork(any(Network.class));

    // Expect the listener to be called back indicating we are connected.
    verify(mockListener).onConnect();

    // Verify that we did not see any errors.
    verify(mockListener, never()).onError(any());

    // Expect the listener to be called back indicating we are no longer connected.
    verify(mockListener).onDisconnect();

    // Still we release the network upon disconnection.
    assertThat(manager.isBusy()).isFalse();

    // Expect to see the wifi lock released.
    verify(mockWifiLock).release();
  }

  /** Verifies timeout behavior when we fail to disconnect from the existing wifi network. */
  @Test
  public void wifiOnDisconnectNetworkTimeoutTest() throws Exception {
    // Create WifiClientManager.
    WifiClientManager manager = createWifiClientManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener);
    assertThat(manager.isBusy()).isTrue();

    // Expect to see the wifi lock acquired.
    verify(mockWifiLock).acquire();

    // Expect to see a request to disconnect.
    verify(mockWifiManager).disconnect();

    // NOTE: We will not send any response to the disconnect, expect timeout error.

    // Verify that we don't see a connection or an error immediately.
    verify(mockListener, never()).onConnect();
    verify(mockListener, never()).onError(any());

    // Verify that we time out in less than 60 seconds.
    advanceTime(60000);
    verify(mockListener).onError(any(WifiTimeoutException.class));

    // Still we release the network upon disconnection.
    assertThat(manager.isBusy()).isFalse();

    // Expect to see the wifi lock released.
    verify(mockWifiLock).release();
  }

  /** Verifies timeout behavior when we fail to connect to the target network. */
  @Test
  public void wifiOnConnectNetworkTimeoutTest() throws Exception {
    // Create WifiClientManager.
    WifiClientManager manager = createWifiClientManager();
    assertThat(manager.isBusy()).isFalse();
    assertThat(manager.isConnected()).isFalse();

    // When WifiClientManager requests to disconnect, answer appropriately.
    doAnswer(disconnectAnswer).when(mockWifiManager).disconnect();

    // Request network connection.
    Listener mockListener = Mockito.mock(Listener.class);
    manager.requestNetwork(TEST_SSID, TEST_PASSPHRASE, mockListener);
    assertThat(manager.isBusy()).isTrue();

    // Expect to see the wifi lock acquired.
    verify(mockWifiLock).acquire();

    // Expect to see a request to disconnect.
    verify(mockWifiManager).disconnect();

    // Expect to see a request to enable the target network.
    verify(mockWifiManager).enableNetwork(anyInt(), anyBoolean());

    // NOTE: We will not send any response to the enableNetwork, expect timeout error.

    // Verify that we don't see a connection or an error immediately.
    verify(mockListener, never()).onConnect();
    verify(mockListener, never()).onError(any());

    // Verify that we time out in less than 60 seconds.
    advanceTime(60000);
    verify(mockListener).onError(any(WifiTimeoutException.class));

    // Still we release the network upon disconnection.
    assertThat(manager.isBusy()).isFalse();

    // Expect to see the wifi lock released.
    verify(mockWifiLock).release();
  }

  private WifiClientManager createWifiClientManager() {
    return new WifiClientManager(mockContext, false);
  }

  /** Runs all threads and tasks to completion, including delayed tasks. */
  private static void runAllTasksAndThreads() {
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    ShadowApplication.runBackgroundTasks();
    Thread.yield();
  }

  /** Advances time the given amount and runs all tasks and threads to drain any pending timers. */
  private static void advanceTime(long millis) {
    runAllTasksAndThreads();
    SystemClock.sleep(millis);
    runAllTasksAndThreads();
  }
}
