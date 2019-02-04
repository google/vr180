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

package com.google.vr180.api;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.TimeConfiguration;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.UpdateConfiguration;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiNetworkConfiguration;
import com.google.vr180.CameraApi.CameraApiRequest.DeleteMediaRequest;
import com.google.vr180.CameraApi.CameraApiRequest.RequestHeader;
import com.google.vr180.CameraApi.CameraApiRequest.RequestType;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ResponseStatus.StatusCode;
import com.google.vr180.CameraApi.CameraStatus.DeviceTemperatureStatus;
import com.google.vr180.CameraApi.CameraStatus.DeviceTemperatureStatus.TemperatureState;
import com.google.vr180.CameraApi.CameraStatus.UpdateStatus;
import com.google.vr180.CameraApi.DebugLogMessage;
import com.google.vr180.api.camerainterfaces.CameraInterfaceFactory;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.CapabilitiesProvider;
import com.google.vr180.api.camerainterfaces.CaptureManager;
import com.google.vr180.api.camerainterfaces.Exceptions.CriticallyLowBatteryException;
import com.google.vr180.api.camerainterfaces.Exceptions.InsufficientStorageException;
import com.google.vr180.api.camerainterfaces.Exceptions.ThermalException;
import com.google.vr180.api.camerainterfaces.FileProvider;
import com.google.vr180.api.camerainterfaces.MediaProvider;
import com.google.vr180.api.camerainterfaces.PairingManager;
import com.google.vr180.api.camerainterfaces.SslManager;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.api.camerainterfaces.TemperatureProvider;
import com.google.vr180.api.camerainterfaces.UpdateManager;
import com.google.vr180.api.camerainterfaces.ViewfinderCaptureSource;
import com.google.vr180.api.implementations.AndroidAudioVolumeManager;
import com.google.vr180.api.implementations.AndroidBatteryStatusProvider;
import com.google.vr180.api.implementations.AndroidConnectionTester;
import com.google.vr180.api.implementations.AndroidFileProvider;
import com.google.vr180.api.implementations.AndroidMediaProvider;
import com.google.vr180.api.implementations.AndroidMobileNetworkManager;
import com.google.vr180.api.implementations.AndroidNetworkManager;
import com.google.vr180.api.implementations.AndroidStorageStatusProvider;
import com.google.vr180.api.implementations.AndroidWakeManager;
import com.google.vr180.api.implementations.BaseCameraInterfaceFactory;
import com.google.vr180.api.implementations.CachedFileChecksumProvider;
import com.google.vr180.api.implementations.MemoryDebugLogsProvider;
import com.google.vr180.api.implementations.ViewfinderManager;
import com.google.vr180.api.implementations.WifiDirectHotspotManager;
import com.google.vr180.common.logging.MemoryLogger;
import com.google.vr180.common.media.BitmapIO;
import com.google.vr180.common.wifi.WifiDirectManager;
import com.google.vr180.communication.http.HttpSocketServer;
import com.google.vr180.testhelpers.TestCameraCalibration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.zip.CRC32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class CameraApiHandlerTest {
  private static final int SHUTDOWN_PERCENTAGE = 5;

  private static final DeviceTemperatureStatus DEVICE_TEMPERATURE_STATUS =
      DeviceTemperatureStatus.newBuilder()
          .setDeviceTemperatureState(TemperatureState.TEMPERATURE_OK)
          .build();

  private static final UpdateStatus UPDATE_STATUS =
      UpdateStatus.newBuilder()
          .setFirmwareVersion("0")
          .setLastFirmwareCheckTimestamp(new Date().getTime())
          .build();

  private static final DeleteMediaRequest MOCK_DELETE_MEDIA_REQUEST =
      DeleteMediaRequest.newBuilder().setFilename("filename1").build();

  private final MemoryLogger logger = new MemoryLogger();
  @Mock private CameraSettings mockCameraSettings;
  @Mock private CaptureManager mockCaptureManager;
  @Mock private TemperatureProvider mockTemperatureProvider;
  @Mock private FileProvider mockFileProvider;
  @Mock private UpdateManager mockUpdateManager;
  @Mock private PairingManager mockPairingManager;
  @Mock private CapabilitiesProvider mockCapabilitiesProvider;
  @Mock private StatusNotifier mockStatusNotifier;
  private long requestId = 1;
  private CameraApiHandler apiHandler;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(mockCameraSettings.getCameraCalibration())
        .thenReturn(TestCameraCalibration.TOP_BOTTOM_STEREO);
    Context context = RuntimeEnvironment.application;
    StorageStatusProvider storageStatusProvider =
        new AndroidStorageStatusProvider(context, "/DCIM/VR180/", mockStatusNotifier);
    MediaProvider mockMediaProvider =
        spy(
            new AndroidMediaProvider(
                context,
                storageStatusProvider,
                new CachedFileChecksumProvider(
                    context, new AndroidFileProvider(context, storageStatusProvider)),
                mockCameraSettings.getCameraCalibration(),
                mockStatusNotifier));
    CameraInterfaceFactory mockCameraInterfaceFactory =
        new BaseCameraInterfaceFactory(
            new AndroidAudioVolumeManager(context),
            new AndroidBatteryStatusProvider(context, mockStatusNotifier, SHUTDOWN_PERCENTAGE),
            mockCameraSettings,
            mockCapabilitiesProvider,
            new AndroidConnectionTester(),
            new MemoryDebugLogsProvider(logger),
            mockFileProvider,
            () -> null,
            new WifiDirectHotspotManager(
                WifiDirectManager.getInstance(context), mockStatusNotifier),
            mockCaptureManager,
            mockMediaProvider,
            new AndroidNetworkManager(
                context, HttpSocketServer.PORT, mock(SslManager.class), mockStatusNotifier),
            new AndroidMobileNetworkManager(context, mockStatusNotifier),
            mockPairingManager,
            storageStatusProvider,
            mockTemperatureProvider,
            mockUpdateManager,
            new ViewfinderManager(context, mockCaptureManager, mock(ViewfinderCaptureSource.class)),
            new AndroidWakeManager(context, mockCameraSettings));
    when(mockTemperatureProvider.getDeviceTemperatureStatus())
        .thenReturn(DEVICE_TEMPERATURE_STATUS);
    when(mockUpdateManager.getUpdateStatus()).thenReturn(UPDATE_STATUS);
    doReturn(createTestThumbnail()).when(mockMediaProvider).getThumbnail(any());
    apiHandler = new TestableCameraApiHandler(mockCameraInterfaceFactory);
  }

  @Test
  public void testSt3DBoxRequest() throws Exception {
    CameraApiRequest.Builder request = newValidRequest();
    request.setType(RequestType.GET_CAMERA_ST3D_BOX);
    CameraApiResponse response = apiHandler.handleRequest(request.build());
    assertThat(response.getResponseStatus().getStatusCode()).isEqualTo(StatusCode.OK);
    assertThat(response.getSt3DBoxResponse().getData())
        .isEqualTo(TestCameraCalibration.TOP_BOTTOM_STEREO.getSt3DBox());
  }

  @Test
  public void testSv3DBoxRequestChunk() throws Exception {
    CameraApiRequest.Builder request = newValidRequest();
    request.setType(RequestType.GET_CAMERA_SV3D_BOX);
    request.setCameraSv3DBoxRequest(
        CameraApiRequest.CameraSv3DBoxRequest.newBuilder()
            .setLength(2)
            .setStartIndex(3)
            .build());
    CameraApiResponse response = apiHandler.handleRequest(request.build());
    assertThat(response.getResponseStatus().getStatusCode()).isEqualTo(StatusCode.OK);

    // Verify the chunk we asked for.
    assertThat(response.getSv3DBoxResponse().getData().size()).isEqualTo(2);
    assertThat(response.getSv3DBoxResponse().getData().byteAt(0))
        .isEqualTo(TestCameraCalibration.TOP_BOTTOM_STEREO.getSv3DBox().byteAt(3));
    assertThat(response.getSv3DBoxResponse().getData().byteAt(1))
        .isEqualTo(TestCameraCalibration.TOP_BOTTOM_STEREO.getSv3DBox().byteAt(4));

    // Verify the checksum.
    CRC32 checksum = new CRC32();
    checksum.update(TestCameraCalibration.TOP_BOTTOM_STEREO.getSv3DBox().toByteArray());
    assertThat(response.getSv3DBoxResponse().getChecksum()).isEqualTo(checksum.getValue());

    // Verify the total length.
    assertThat(response.getSv3DBoxResponse().getTotalSize())
        .isEqualTo(TestCameraCalibration.TOP_BOTTOM_STEREO.getSv3DBox().size());
  }

  @Test
  public void testSv3DBoxRequestWholeBox() throws Exception {
    CameraApiRequest.Builder request = newValidRequest();
    request.setType(RequestType.GET_CAMERA_SV3D_BOX);
    request.setCameraSv3DBoxRequest(
        CameraApiRequest.CameraSv3DBoxRequest.newBuilder()
            .setLength(0)
            .setStartIndex(0)
            .build());
    CameraApiResponse response = apiHandler.handleRequest(request.build());
    assertThat(response.getResponseStatus().getStatusCode()).isEqualTo(StatusCode.OK);

    // Verify the entire box is in the response.
    assertThat(response.getSv3DBoxResponse().getData().size())
        .isEqualTo(TestCameraCalibration.TOP_BOTTOM_STEREO.getSv3DBox().size());
    assertThat(response.getSv3DBoxResponse().getData())
        .isEqualTo(TestCameraCalibration.TOP_BOTTOM_STEREO.getSv3DBox());

    // Verify the checksum.
    CRC32 checksum = new CRC32();
    checksum.update(TestCameraCalibration.TOP_BOTTOM_STEREO.getSv3DBox().toByteArray());
    assertThat(response.getSv3DBoxResponse().getChecksum()).isEqualTo(checksum.getValue());

    // Verify the total length.
    assertThat(response.getSv3DBoxResponse().getTotalSize())
        .isEqualTo(TestCameraCalibration.TOP_BOTTOM_STEREO.getSv3DBox().size());
  }

  @Test
  public void testStartCapture_criticallyLowBattery()
      throws InsufficientStorageException, ThermalException, CriticallyLowBatteryException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.START_CAPTURE)
            .setHeader(
                RequestHeader.newBuilder()
                    .setRequestId(1)
                    .setExpirationTimestamp(new Date().getTime() + 1000))
            .build();
    doThrow(new CriticallyLowBatteryException()).when(mockCaptureManager).startCapture();
    assertThat(apiHandler.handleRequest(request).getResponseStatus())
        .isEqualTo(CameraApiHandler.criticallyLowBatteryResponse().build().getResponseStatus());
  }

  @Test
  public void testStartCapture_insufficientStorage()
      throws InsufficientStorageException, ThermalException, CriticallyLowBatteryException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.START_CAPTURE)
            .setHeader(
                RequestHeader.newBuilder()
                    .setRequestId(1)
                    .setExpirationTimestamp(new Date().getTime() + 1000))
            .build();
    doThrow(new InsufficientStorageException()).when(mockCaptureManager).startCapture();
    assertThat(apiHandler.handleRequest(request).getResponseStatus())
        .isEqualTo(CameraApiHandler.insufficientStorageResponse().build().getResponseStatus());
  }

  @Test
  public void testStartCapture_thermalError()
      throws InsufficientStorageException, ThermalException, CriticallyLowBatteryException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.START_CAPTURE)
            .setHeader(
                RequestHeader.newBuilder()
                    .setRequestId(1)
                    .setExpirationTimestamp(new Date().getTime() + 1000))
            .build();
    doThrow(new ThermalException()).when(mockCaptureManager).startCapture();
    assertThat(apiHandler.handleRequest(request).getResponseStatus())
        .isEqualTo(CameraApiHandler.thermalErrorResponse().build().getResponseStatus());
  }


  @Test
  public void testDeviceTemperatureStatus_ok() throws Exception {
    when(mockTemperatureProvider.getDeviceTemperatureAboveErrorThreshold()).thenReturn(false);
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.STATUS)
            .setHeader(
                RequestHeader.newBuilder()
                    .setRequestId(1)
                    .setExpirationTimestamp(new Date().getTime() + 1000))
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus())
        .isEqualTo(CameraApiHandler.okResponse().getResponseStatus());
    assertThat(response.getCameraStatus().getDeviceTemperatureStatus().getDeviceTemperatureState())
        .isEqualTo(TemperatureState.TEMPERATURE_OK);
    assertThat(response.getCameraStatus().getDeviceTemperatureAboveErrorThreshold())
        .isEqualTo(false);
  }

  @Test
  public void testDeviceTemperatureStatus_overheated() throws Exception {
    when(mockTemperatureProvider.getDeviceTemperatureStatus())
        .thenReturn(
            DeviceTemperatureStatus.newBuilder()
                .setDeviceTemperatureState(TemperatureState.TEMPERATURE_OVERHEATED)
                .build());
    when(mockTemperatureProvider.getDeviceTemperatureAboveErrorThreshold()).thenReturn(true);
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.STATUS)
            .setHeader(
                RequestHeader.newBuilder()
                    .setRequestId(1)
                    .setExpirationTimestamp(new Date().getTime() + 1000))
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus())
        .isEqualTo(CameraApiHandler.okResponse().getResponseStatus());
    assertThat(response.getCameraStatus().getDeviceTemperatureStatus().getDeviceTemperatureState())
        .isEqualTo(TemperatureState.TEMPERATURE_OVERHEATED);
    assertThat(response.getCameraStatus().getDeviceTemperatureAboveErrorThreshold())
        .isEqualTo(true);
  }

  @Test
  public void testDelete() throws Exception {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.DELETE_MEDIA)
            .setHeader(
                RequestHeader.newBuilder()
                    .setRequestId(1)
                    .setExpirationTimestamp(new Date().getTime() + 1000))
            .addDeleteMediaRequest(MOCK_DELETE_MEDIA_REQUEST)
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus())
        .isEqualTo(CameraApiHandler.okResponse().getResponseStatus());
    assertThat(response.getDeleteMediaStatusList().size()).isEqualTo(1);
    verify(mockFileProvider).deleteFile("filename1");
  }

  /** Tests that WIFI_NETWORK_STATUS request returns configured WiFi network list. */
  @Test
  public void wifiNetworkStatusShouldReturnConfiguredNetworks() throws Exception {
    // Given
    WifiManager wifiManager =
        (WifiManager) RuntimeEnvironment.application.getSystemService(Context.WIFI_SERVICE);
    WifiConfiguration mockNetwork = new WifiConfiguration();
    mockNetwork.SSID = "mock-network-0";
    wifiManager.addNetwork(mockNetwork);

    // When
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.WIFI_NETWORK_STATUS)
            .setHeader(createRequestHeader())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);

    // Then
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    assertThat(response.getWifiNetworkStatus().getConfiguredNetworkSsidsList())
        .containsExactly("mock-network-0");
  }

  /** Tests that CONFIGURE request removes specified WiFi networks. */
  @Test
  public void configureShouldRemoveConfiguredNetworks() throws Exception {
    // Given
    WifiManager wifiManager =
        (WifiManager) RuntimeEnvironment.application.getSystemService(Context.WIFI_SERVICE);
    for (int i = 0; i < 3; i++) {
      WifiConfiguration mockNetwork = new WifiConfiguration();
      mockNetwork.SSID = "mock-network-" + i;
      wifiManager.addNetwork(mockNetwork);
    }

    // When
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.CONFIGURE)
            .setHeader(createRequestHeader())
            .setConfigurationRequest(
                ConfigurationRequest.newBuilder()
                    .setWifiNetworkConfiguration(
                        WifiNetworkConfiguration.newBuilder()
                            .addRemoveNetworkSsids("mock-network-1")
                            .addRemoveNetworkSsids("mock-network-2")
                            .build())
                    .build())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);

    // Then
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
    assertThat(configuredNetworks).hasSize(1);
    assertThat(configuredNetworks.get(0).SSID).isEqualTo("mock-network-0");
  }

  @Test
  public void testGetDebugLogs() throws Exception {
    // Add a log message.
    logger.w("test", "test error");
    assertThat(logger.getMessages()).hasSize(1);
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.GET_DEBUG_LOGS)
            .setHeader(createRequestHeader())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    assertThat(response.getDebugLogsCount()).isEqualTo(1);
    assertThat(response.getDebugLogs(0).getTag()).isEqualTo("test");
    assertThat(response.getDebugLogs(0).getMessage()).isEqualTo("test error");
    assertThat(response.getDebugLogs(0).getTimestamp() > new Date().getTime() - 1000).isTrue();
    assertThat(response.getDebugLogs(0).getTimestamp() <= new Date().getTime()).isTrue();
  }

  @Test
  public void testGetDebugLogsFilterLevel() throws Exception {
    // Add a log message.
    logger.w("test", "test error");
    assertThat(logger.getMessages()).hasSize(1);
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.GET_DEBUG_LOGS)
            .setHeader(createRequestHeader())
            .setDebugLogsRequest(
                CameraApiRequest.DebugLogsRequest.newBuilder()
                    .setMinLevel(DebugLogMessage.Level.ERROR)
                    .build())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    assertThat(response.getDebugLogsCount()).isEqualTo(0);
  }

  @Test
  public void testGetDebugLogsFilterTagNoMatch() throws Exception {
    // Add a log message.
    logger.w("test", "test error");
    assertThat(logger.getMessages()).hasSize(1);
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.GET_DEBUG_LOGS)
            .setHeader(createRequestHeader())
            .setDebugLogsRequest(
                CameraApiRequest.DebugLogsRequest.newBuilder()
                    .setTagFilter("wrong_filter")
                    .build())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    assertThat(response.getDebugLogsCount()).isEqualTo(0);
  }

  @Test
  public void testGetDebugLogsFilterTagMatch() throws Exception {
    // Add a log message.
    logger.w("test", "test error");
    assertThat(logger.getMessages()).hasSize(1);
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.GET_DEBUG_LOGS)
            .setHeader(createRequestHeader())
            .setDebugLogsRequest(
                CameraApiRequest.DebugLogsRequest.newBuilder().setTagFilter("test").build())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    assertThat(response.getDebugLogsCount()).isEqualTo(1);
  }

  @Test
  public void testGetFullThumbnail() throws Exception {
    BitmapIO.saveBitmap(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), 80, "testfile.jpg");
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.GET_THUMBNAIL)
            .setHeader(createRequestHeader())
            .addThumbnailRequest(
                CameraApiRequest.ThumbnailRequest.newBuilder()
                    .setFilename("testfile.jpg")
                    .setWidth(100)
                    .setHeight(100)
                    .setQuality(50)
                    .build())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    assertThat(response.getThumbnailCount()).isEqualTo(1);
    CRC32 crc = new CRC32();
    crc.update(response.getThumbnail(0).getData().toByteArray());
    assertThat(response.getThumbnail(0).getChecksum()).isEqualTo(crc.getValue());
    assertThat(response.getThumbnail(0).getTotalSize())
        .isEqualTo(response.getThumbnail(0).getData().size());
  }

  @Test
  public void testGetPartialThumbnail() throws Exception {
    BitmapIO.saveBitmap(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), 80, "testfile.jpg");
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.GET_THUMBNAIL)
            .setHeader(createRequestHeader())
            .addThumbnailRequest(
                CameraApiRequest.ThumbnailRequest.newBuilder()
                    .setFilename("testfile.jpg")
                    .setWidth(100)
                    .setHeight(100)
                    .setQuality(50)
                    .setStartIndex(2)
                    .setLength(10)
                    .build())
            .build();

    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    assertThat(response.getThumbnailCount()).isEqualTo(1);
    Log.e("BluetoothApiHandlerTest", "Thumbnail size " + response.getThumbnail(0).getData().size());

    // Fetch the full file to compare.
    CameraApiRequest fullFileRequest =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.GET_THUMBNAIL)
            .setHeader(createRequestHeader())
            .addThumbnailRequest(
                CameraApiRequest.ThumbnailRequest.newBuilder()
                    .setFilename("testfile.jpg")
                    .setWidth(100)
                    .setHeight(100)
                    .setQuality(50)
                    .build())
            .build();
    CameraApiResponse fullResponse = apiHandler.handleRequest(fullFileRequest);

    byte[] expectedBytes = fullResponse.getThumbnail(0).getData().substring(2, 12).toByteArray();
    assertThat(response.getThumbnail(0).getData().toByteArray()).isEqualTo(expectedBytes);
    assertThat(response.getThumbnail(0).getChecksum())
        .isEqualTo(fullResponse.getThumbnail(0).getChecksum());
    assertThat(response.getThumbnail(0).getTotalSize())
        .isEqualTo(fullResponse.getThumbnail(0).getData().size());
  }

  @Test
  public void testApplyUpdate() throws Exception {
    UpdateConfiguration updateConfig =
        UpdateConfiguration.newBuilder().setDesiredUpdateVersion("12").build();

    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.CONFIGURE)
            .setHeader(createRequestHeader())
            .setConfigurationRequest(
                CameraApiRequest.ConfigurationRequest.newBuilder()
                    .setUpdateConfiguration(updateConfig)
                    .build())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);

    Mockito.verify(mockUpdateManager).applyUpdate(updateConfig);
  }

  @Test
  public void testSetTime() throws Exception {
    TimeConfiguration timeConfig =
        TimeConfiguration.newBuilder()
            .setTimestamp(new Date().getTime())
            .setTimezone(java.util.TimeZone.getDefault().getID())
            .build();

    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.CONFIGURE)
            .setHeader(createRequestHeader())
            .setConfigurationRequest(
                CameraApiRequest.ConfigurationRequest.newBuilder()
                    .setTimeConfiguration(timeConfig)
                    .build())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);

    Mockito.verify(mockCameraSettings).setTime(timeConfig);
  }

  @Test
  public void testDeleteMedia() throws Exception {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.DELETE_MEDIA)
            .setHeader(createRequestHeader())
            .addDeleteMediaRequest(
                CameraApiRequest.DeleteMediaRequest.newBuilder()
                    .setFilename("test.jpg")
                    .build())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    assertThat(response.getDeleteMediaStatusCount()).isEqualTo(1);
    assertThat(response.getDeleteMediaStatus(0).getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
    Mockito.verify(mockFileProvider).deleteFile("test.jpg");
  }

  @Test
  public void testFormatSdCard() throws Exception {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.FORMAT_SD_CARD)
            .setHeader(createRequestHeader())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);

    verify(mockCameraSettings).formatStorage();
  }

  @Test
  public void testFactoryReset() throws Exception {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.FACTORY_RESET)
            .setHeader(createRequestHeader())
            .build();
    CameraApiResponse response = apiHandler.handleRequest(request);
    assertThat(response.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);

    verify(mockCameraSettings).factoryReset();
  }

  /** @return The CameraApiResponse.Builder with a valid header. */
  private CameraApiRequest.Builder newValidRequest() {
    return CameraApiRequest.newBuilder()
        .setHeader(
            CameraApiRequest.RequestHeader.newBuilder()
                .setExpirationTimestamp(new Date().getTime() + 3000)
                .setRequestId(requestId++)
                .build());
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

  private static CameraApiRequest.RequestHeader createRequestHeader() {
    return CameraApiRequest.RequestHeader.newBuilder()
        .setRequestId(100)
        .setExpirationTimestamp(new Date().getTime() + 10000)
        .build();
  }

  private static byte[] createTestThumbnail() {
    byte[] result = new byte[100];
    for (int i = 0; i < 100; ++i) {
      result[i] = (byte) i;
    }
    return result;
  }
}
