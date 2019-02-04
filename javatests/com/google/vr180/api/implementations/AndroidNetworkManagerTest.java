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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import com.google.common.collect.ImmutableList;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiNetworkConfiguration;
import com.google.vr180.CameraApi.WifiNetworkStatus;
import com.google.vr180.api.camerainterfaces.SslManager;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class AndroidNetworkManagerTest {
  private static final int HTTP_PORT = 443;

  @Mock private SslManager sslManager;
  @Mock private Context context;
  @Mock private WifiManager wifiManager;
  @Mock private WifiLock wifiLock;
  @Mock private StatusNotifier mockStatusNotifier;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(wifiManager.createWifiLock(anyInt(), any())).thenReturn(wifiLock);
    when(context.getSystemService(Context.WIFI_SERVICE)).thenReturn(wifiManager);
  }

  @Test
  public void getWifiNetworkStatusShouldReturnNullWhenConfiguredNetworksFails() throws Exception {
    // Given
    WifiInfo wifiInfo = mock(WifiInfo.class);
    when(wifiInfo.getSSID()).thenReturn("mock-network");
    when(wifiInfo.getIpAddress()).thenReturn(0x0100007F);

    when(wifiManager.getConnectionInfo()).thenReturn(wifiInfo);
    when(wifiManager.getConfiguredNetworks()).thenReturn(null);

    // When
    AndroidNetworkManager androidNetworkManager =
        new AndroidNetworkManager(context, HTTP_PORT, sslManager, mockStatusNotifier);
    WifiNetworkStatus status = androidNetworkManager.getWifiNetworkStatus();

    // Then
    assertThat(status).isEqualTo(null);
  }

  @Test
  public void getWifiNetworkStatusShouldReturnEmptyListWhenNoConfiguredNetworks() throws Exception {
    // Given
    when(wifiManager.getConfiguredNetworks()).thenReturn(Collections.emptyList());

    // When
    AndroidNetworkManager androidNetworkManager =
        new AndroidNetworkManager(context, HTTP_PORT, sslManager, mockStatusNotifier);
    WifiNetworkStatus status = androidNetworkManager.getWifiNetworkStatus();

    // Then
    assertThat(status).isEqualTo(WifiNetworkStatus.newBuilder().build());
  }

  @Test
  public void getWifiNetworkStatusShouldReturnListOfConfiguredNetworks() throws Exception {
    // Given
    WifiConfiguration mockNetwork = newWifiConfiguration(42, "mock-network");
    when(wifiManager.getConfiguredNetworks()).thenReturn(ImmutableList.of(mockNetwork));

    // When
    AndroidNetworkManager androidNetworkManager =
        new AndroidNetworkManager(context, HTTP_PORT, sslManager, mockStatusNotifier);
    WifiNetworkStatus status = androidNetworkManager.getWifiNetworkStatus();

    // Then
    assertThat(status)
        .isEqualTo(
            WifiNetworkStatus.newBuilder().addConfiguredNetworkSsids("mock-network").build());
  }

  @Test
  public void updateNetworkConfigurationShouldHandleEmptyNetworksToRemove() throws Exception {
    // When
    WifiNetworkConfiguration wifiNetworkConfiguration =
        WifiNetworkConfiguration.newBuilder().build();
    AndroidNetworkManager androidNetworkManager =
        new AndroidNetworkManager(context, HTTP_PORT, sslManager, mockStatusNotifier);
    androidNetworkManager.updateWifiNetworkConfiguration(wifiNetworkConfiguration);

    // Then
    verify(wifiManager, never()).removeNetwork(anyInt());
  }

  @Test
  public void updateNetworkConfigurationShouldRemoveConfiguredNetworks() throws Exception {
    // Given
    WifiConfiguration mockNetwork = newWifiConfiguration(42, "mock-network");
    ImmutableList<WifiConfiguration> configuredNetworks = ImmutableList.of(mockNetwork);
    when(wifiManager.getConfiguredNetworks()).thenReturn(configuredNetworks);
    when(wifiManager.removeNetwork(42)).thenReturn(true);

    // When
    WifiNetworkConfiguration wifiNetworkConfiguration =
        WifiNetworkConfiguration.newBuilder().addRemoveNetworkSsids("mock-network").build();
    AndroidNetworkManager androidNetworkManager =
        new AndroidNetworkManager(context, HTTP_PORT, sslManager, mockStatusNotifier);
    androidNetworkManager.updateWifiNetworkConfiguration(wifiNetworkConfiguration);

    // Then
    verify(wifiManager).removeNetwork(42);
  }

  @Test
  public void updateNetworkConfigurationShouldIgnoreInvalidNetworksToRemove() throws Exception {
    // Given
    WifiConfiguration mockNetwork = newWifiConfiguration(42, "mock-network");
    ImmutableList<WifiConfiguration> configuredNetworks = ImmutableList.of(mockNetwork);
    when(wifiManager.getConfiguredNetworks()).thenReturn(configuredNetworks);
    when(wifiManager.removeNetwork(42)).thenReturn(true);

    // When
    WifiNetworkConfiguration wifiNetworkConfiguration =
        WifiNetworkConfiguration.newBuilder()
            .addRemoveNetworkSsids("mock-network")
            .addRemoveNetworkSsids("unknown-network")
            .build();
    AndroidNetworkManager androidNetworkManager =
        new AndroidNetworkManager(context, HTTP_PORT, sslManager, mockStatusNotifier);
    androidNetworkManager.updateWifiNetworkConfiguration(wifiNetworkConfiguration);

    // Then
    verify(wifiManager).removeNetwork(42);
  }

  @Test
  public void excludedNetworkInterfaces() throws Exception {
    AndroidNetworkManager androidNetworkManager =
        new AndroidNetworkManager(context, HTTP_PORT, sslManager, mockStatusNotifier);

    assertThat(androidNetworkManager.shouldExcludeNetworkInterface("lo")).isTrue();
    assertThat(androidNetworkManager.shouldExcludeNetworkInterface("dummy0")).isTrue();
    assertThat(androidNetworkManager.shouldExcludeNetworkInterface("rmnet_data6")).isTrue();

    assertThat(androidNetworkManager.shouldExcludeNetworkInterface("p2p0")).isFalse();
    assertThat(androidNetworkManager.shouldExcludeNetworkInterface("wlan0")).isFalse();
  }

  private WifiConfiguration newWifiConfiguration(int networkId, String ssid) {
    WifiConfiguration configuration = new WifiConfiguration();
    configuration.networkId = networkId;
    configuration.SSID = ssid;
    return configuration;
  }
}
