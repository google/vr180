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

package com.google.vr180.communication.bluetooth;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiRequest.RequestHeader;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraApi.CameraStatus.BatteryStatus;
import com.google.vr180.CameraApi.CameraStatus.BatteryStatus.BatteryWarningLevel;
import com.google.vr180.CameraApi.CameraStatus.DeviceTemperatureStatus;
import com.google.vr180.CameraApi.CameraStatus.DeviceTemperatureStatus.TemperatureState;
import com.google.vr180.CameraApi.CameraStatus.UpdateStatus;
import com.google.vr180.api.CameraApiHandler;
import com.google.vr180.api.camerainterfaces.AudioVolumeManager;
import com.google.vr180.api.camerainterfaces.BatteryStatusProvider;
import com.google.vr180.api.camerainterfaces.CameraInterfaceFactory;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.CapabilitiesProvider;
import com.google.vr180.api.camerainterfaces.CaptureManager;
import com.google.vr180.api.camerainterfaces.ConnectionTester;
import com.google.vr180.api.camerainterfaces.DebugLogsProvider;
import com.google.vr180.api.camerainterfaces.FileChecksumProvider;
import com.google.vr180.api.camerainterfaces.FileProvider;
import com.google.vr180.api.camerainterfaces.GravityVectorProvider;
import com.google.vr180.api.camerainterfaces.HotspotManager;
import com.google.vr180.api.camerainterfaces.MobileNetworkManager;
import com.google.vr180.api.camerainterfaces.PairingManager;
import com.google.vr180.api.camerainterfaces.SslManager;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.api.camerainterfaces.TemperatureProvider;
import com.google.vr180.api.camerainterfaces.UpdateManager;
import com.google.vr180.api.camerainterfaces.ViewfinderProvider;
import com.google.vr180.api.camerainterfaces.WakeManager;
import com.google.vr180.api.implementations.AndroidMediaProvider;
import com.google.vr180.api.implementations.AndroidNetworkManager;
import com.google.vr180.api.implementations.AndroidStorageStatusProvider;
import com.google.vr180.api.implementations.BaseCameraInterfaceFactory;
import com.google.vr180.common.crypto.CryptoUtilities;
import com.google.vr180.communication.http.HttpSocketServer;
import com.google.vr180.testhelpers.TestCameraCalibration;
import java.util.Date;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class BluetoothApiHandlerTest {

  private static final DeviceTemperatureStatus DEVICE_TEMPERATURE_STATUS =
      DeviceTemperatureStatus.newBuilder()
          .setDeviceTemperatureState(TemperatureState.TEMPERATURE_OK)
          .build();

  private static final UpdateStatus UPDATE_STATUS =
      UpdateStatus.newBuilder()
          .setFirmwareVersion("0")
          .setLastFirmwareCheckTimestamp(new Date().getTime())
          .build();

  private static final BatteryStatus BATTERY_STATUS =
      BatteryStatus.newBuilder()
          .setBatteryPercentage(100)
          .setWarningLevel(BatteryWarningLevel.OK)
          .build();

  private final byte[] sharedKey = CryptoUtilities.generateRandom(32);
  private BluetoothApiHandler handler;
  @Mock private StatusNotifier mockStatusNotifier;
  @Mock private AudioVolumeManager mockAudioVolumeManager;
  @Mock private BatteryStatusProvider mockBatteryStatusProvider;
  @Mock private CameraSettings mockCameraSettings;
  @Mock private CapabilitiesProvider mockCapabilitiesProvider;
  @Mock private ConnectionTester mockConnectionTester;
  @Mock private DebugLogsProvider mockDebugLogsProvider;
  @Mock private FileProvider mockFileProvider;
  @Mock private GravityVectorProvider mockGravityVectorProvider;
  @Mock private HotspotManager mockHotspotManager;
  @Mock private CaptureManager mockCaptureManager;
  @Mock private MobileNetworkManager mockMobileNetworkManager;
  @Mock private PairingManager mockPairingManager;
  @Mock private TemperatureProvider mockTemperatureProvider;
  @Mock private UpdateManager mockUpdateManager;
  @Mock private ViewfinderProvider mockViewfinderProvider;
  @Mock private WakeManager mockWakeManager;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    Context context = RuntimeEnvironment.application;
    StorageStatusProvider storageStatusProvider =
        new AndroidStorageStatusProvider(context, "/DCIM/VR180/", mockStatusNotifier);
    when(mockCameraSettings.getSharedKey()).thenReturn(sharedKey);
    when(mockCameraSettings.getSharedKeyIsPending()).thenReturn(false);
    when(mockCameraSettings.getCameraCalibration())
        .thenReturn(TestCameraCalibration.TOP_BOTTOM_STEREO);
    when(mockTemperatureProvider.getDeviceTemperatureStatus())
        .thenReturn(DEVICE_TEMPERATURE_STATUS);
    when(mockUpdateManager.getUpdateStatus()).thenReturn(UPDATE_STATUS);
    when(mockBatteryStatusProvider.getBatteryStatus()).thenReturn(BATTERY_STATUS);
    CameraInterfaceFactory cameraInterfaceFactory =
        new BaseCameraInterfaceFactory(
            mockAudioVolumeManager,
            mockBatteryStatusProvider,
            mockCameraSettings,
            mockCapabilitiesProvider,
            mockConnectionTester,
            mockDebugLogsProvider,
            mockFileProvider,
            mockGravityVectorProvider,
            mockHotspotManager,
            mockCaptureManager,
            new AndroidMediaProvider(
                RuntimeEnvironment.application,
                storageStatusProvider,
                mock(FileChecksumProvider.class),
                TestCameraCalibration.TOP_BOTTOM_STEREO,
                mockStatusNotifier),
            new AndroidNetworkManager(
                context, HttpSocketServer.PORT, mock(SslManager.class), mockStatusNotifier),
            mockMobileNetworkManager,
            mockPairingManager,
            storageStatusProvider,
            mockTemperatureProvider,
            mockUpdateManager,
            mockViewfinderProvider,
            mockWakeManager);
    CameraApiHandler apiHandler = new TestableCameraApiHandler(cameraInterfaceFactory);
    handler = new BluetoothApiHandler(mockCameraSettings, apiHandler);
  }

  @Test
  public void testStatusRequest() throws Exception {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.STATUS)
            .setHeader(createRequestHeader())
            .build();
    CameraApiResponse response = runRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    assertThat(response.hasKeyExchangeResponse()).isFalse();
    verify(mockWakeManager).wakePing();
  }

  @Test
  public void testInvalidRequest() throws Exception {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.KEY_EXCHANGE_INITIATE)
            .setHeader(createRequestHeader())
            .build();
    CameraApiResponse response = runRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.NOT_SUPPORTED);
  }

  @Test
  public void testExpiredRequest() throws Exception {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.STATUS)
            .setHeader(RequestHeader.newBuilder().setRequestId(10).setExpirationTimestamp(100))
            .build();
    CameraApiResponse response = runRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.INVALID_REQUEST);
  }

  @Test
  public void testMissingExpiration() throws Exception {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.START_CAPTURE)
            .setHeader(RequestHeader.newBuilder().setRequestId(10))
            .build();
    CameraApiResponse response = runRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.INVALID_REQUEST);
  }

  @Test
  public void testMissingRequestId() throws Exception {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.STATUS)
            .setHeader(
                RequestHeader.newBuilder().setExpirationTimestamp(new Date().getTime() + 1000))
            .build();
    CameraApiResponse response = runRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.INVALID_REQUEST);
  }

  @Test
  public void testStatusDoesNotRequireExpiration() throws Exception {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.STATUS)
            .setHeader(RequestHeader.newBuilder().setRequestId(10))
            .build();
    CameraApiResponse response = runRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
  }

  @Test
  public void testRequestId() throws Exception {
    long requestId = 1234;
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.STATUS)
            .setHeader(
                RequestHeader.newBuilder()
                    .setRequestId(requestId)
                    .setExpirationTimestamp(new Date().getTime() + 1000))
            .build();
    CameraApiResponse response = runRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    assertThat(response.getRequestId()).isEqualTo(requestId);
  }


  private CameraApiResponse runRequest(CameraApiRequest request) throws Exception {
    byte[] requestData = CryptoUtilities.encrypt(request.toByteArray(), sharedKey);
    byte[] responseData = handler.handleRequest(requestData);
    return CameraApiResponse.parseFrom(CryptoUtilities.decrypt(responseData, sharedKey));
  }

  private static CameraApiRequest.RequestHeader createRequestHeader() {
    return CameraApiRequest.RequestHeader.newBuilder()
        .setRequestId(100)
        .setExpirationTimestamp(new Date().getTime() + 10000)
        .build();
  }

  /** Overrides the getBackgroundExecutor method to make background tasks happen immediately. */
  private static class TestableCameraApiHandler extends CameraApiHandler {
    public TestableCameraApiHandler(CameraInterfaceFactory interfaceFactory) {
      super(interfaceFactory);
    }

    @Override
    public Executor getBackgroundExecutor() {
      return MoreExecutors.directExecutor();
    }
  }
}
