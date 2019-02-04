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
import static junit.framework.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.res.Resources;
import com.google.protobuf.ByteString;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest;
import com.google.vr180.CameraApi.CameraApiRequest.ConnectionTestRequest;
import com.google.vr180.CameraApi.CameraApiRequest.DebugLogsRequest;
import com.google.vr180.CameraApi.CameraApiRequest.RequestType;
import com.google.vr180.CameraApi.CameraApiRequest.WebRtcRequest;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ConnectionTestResponse;
import com.google.vr180.CameraApi.CameraApiResponse.KeyExchangeResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ListMediaResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ResponseStatus;
import com.google.vr180.CameraApi.CameraApiResponse.ResponseStatus.StatusCode;
import com.google.vr180.CameraApi.CameraApiResponse.St3DBoxResponse;
import com.google.vr180.CameraApi.CameraApiResponse.Sv3DBoxResponse;
import com.google.vr180.CameraApi.CameraCalibration;
import com.google.vr180.CameraApi.CameraCapabilities;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.CaptureMode.CaptureType;
import com.google.vr180.CameraApi.DebugLogMessage;
import com.google.vr180.CameraApi.FileChecksum;
import com.google.vr180.CameraApi.FrameSize;
import com.google.vr180.CameraApi.LiveStreamMode;
import com.google.vr180.CameraApi.Media;
import com.google.vr180.CameraApi.MeteringMode;
import com.google.vr180.CameraApi.PhotoMode;
import com.google.vr180.CameraApi.VideoMode;
import com.google.vr180.CameraApi.WhiteBalanceMode;
import com.google.vr180.CameraApi.WifiAccessPointInfo;
import com.google.vr180.CameraApi.WifiNetworkStatus;
import com.google.vr180.api.CameraApiClient.CameraApiException;
import com.google.vr180.api.CameraApiClient.ClockSkewProvider;
import com.google.vr180.api.CameraApiClient.TimestampProvider;
import com.google.vr180.api.CameraApiEndpoint.Priority;
import java.util.List;
import java.util.zip.CRC32;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE)
public final class CameraApiClientTest {

  private static final long DEFAULT_SKEW = 0L;
  private static final Instant DEFAULT_TIME = new Instant(1000000000L);

  // A generic OK response, which can be expanded with additional data.
  // The request id is set to the default time because the client sets the current timestamp as the
  // request id on its requests.
  private static final CameraApiResponse OK_RESPONSE =
      CameraApiResponse.newBuilder()
          .setResponseStatus(ResponseStatus.newBuilder().setStatusCode(StatusCode.OK))
          .setRequestId(DEFAULT_TIME.getMillis())
          .build();
  private static final CaptureMode TEST_CAPTURE_MODE =
      CaptureMode.newBuilder().setActiveCaptureType(CaptureType.PHOTO).build();
  private static final ConnectionTestRequest CONNECTION_TEST_REQUEST =
      ConnectionTestRequest.newBuilder()
          .setUrl("http://connectivitycheck.gstatic.com/generate_204")
          .build();
  private static final ConnectionTestResponse CONNECTION_TEST_RESPONSE =
      ConnectionTestResponse.newBuilder().setHttpResponseCode(204).build();
  private static final String TEST_FILENAME = "a.jpg";
  private static final FileChecksum TEST_CHECKSUM =
      FileChecksum.newBuilder().setChecksumType(FileChecksum.ChecksumType.SHA1).build();
  private static final ListMediaResponse LIST_MEDIA_TEST_RESPONSE =
      ListMediaResponse.newBuilder().setLastModifiedTime(10000).setTotalCount(0).build();
  private static final WebRtcRequest TEST_WEBRTC_REQUEST =
      WebRtcRequest.newBuilder().setSessionName("test_session").build();
  private static final String TEST_UPDATE_VERSION = "test_version";
  private static final long TEST_TIME = 10000000L;
  private static final String TEST_TIMEZONE = "America/Los_Angeles";
  private static final FrameSize TEST_FRAME_SIZE =
      FrameSize.newBuilder().setFrameWidth(480).setFrameHeight(320).build();
  private static final VideoMode TEST_VIDEO_MODE =
      VideoMode.newBuilder().setFrameSize(TEST_FRAME_SIZE).build();
  private static final PhotoMode TEST_PHOTO_MODE =
      PhotoMode.newBuilder().setFrameSize(TEST_FRAME_SIZE).build();
  private static final LiveStreamMode TEST_LIVE_STREAM_MODE =
      LiveStreamMode.newBuilder().setVideoMode(TEST_VIDEO_MODE).build();
  private static final byte[] TEST_PUBLIC_KEY = new byte[] {1, 2, 3};
  private static final byte[] TEST_SALT = new byte[] {4, 3, 2};
  private static final KeyExchangeResponse TEST_KEY_EXCHANGE_RESPONSE =
      KeyExchangeResponse.newBuilder().setPublicKey(ByteString.copyFrom(TEST_PUBLIC_KEY)).build();

  @Mock private CameraApiEndpoint mockEndpoint;
  @Mock private ClockSkewProvider mockClockSkewProvider;
  @Mock private TimestampProvider mockTimestampProvider;
  private CameraApiClient apiClient;
  private ArgumentCaptor<CameraApiRequest> requestCaptor;

  private CameraCalibration testCalibration;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(mockTimestampProvider.getTimestamp()).thenReturn(DEFAULT_TIME.getMillis());
    when(mockClockSkewProvider.getClockSkew()).thenReturn(DEFAULT_SKEW);
    apiClient = new CameraApiClient(mockEndpoint, mockTimestampProvider, mockClockSkewProvider);
    requestCaptor = ArgumentCaptor.forClass(CameraApiRequest.class);
    when(mockEndpoint.doRequest(any(), any())).thenReturn(OK_RESPONSE);

    // Initialize the test calibration data.
    Resources resources = RuntimeEnvironment.application.getResources();
    ByteString sv3dBox =
        ByteString.readFrom(resources.openRawResource(
            com.google.vr180.testhelpers.R.raw.sv3d_box));
    ByteString st3dBox =
        ByteString.readFrom(resources.openRawResource(
            com.google.vr180.testhelpers.R.raw.st3d_box));
    testCalibration =
        CameraCalibration.newBuilder().setSt3DBox(st3dBox).setSv3DBox(sv3dBox).build();
  }

  @Test
  public void testWifiConnect() throws Exception {
    WifiAccessPointInfo info =
        WifiAccessPointInfo.newBuilder().setSsid("test-ssid").setPassword("password").build();

    apiClient.doWifiConnectRequest(info);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    ConfigurationRequest configurationRequest = checkConfigurationRequest(requestCaptor.getValue());
    assertThat(configurationRequest.getLocalWifiInfo().getSsid()).isEqualTo(info.getSsid());
    assertThat(configurationRequest.getLocalWifiInfo().getPassword()).isEqualTo(info.getPassword());
  }

  @Test
  public void testGetCapabilities() throws Exception {
    CameraCapabilities capabilities = CameraCapabilities.newBuilder().setModelName("test").build();
    when(mockEndpoint.doRequest(any(), any()))
        .thenReturn(OK_RESPONSE.toBuilder().setCapabilities(capabilities).build());

    CameraCapabilities returnedCapabilities = apiClient.getCapabilities();

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.GET_CAPABILITIES);
    assertThat(returnedCapabilities).isEqualTo(capabilities);
  }

  @Test
  public void testDebugLogs() throws Exception {
    DebugLogMessage logMessage = DebugLogMessage.newBuilder().setMessage("test").build();
    DebugLogsRequest request = DebugLogsRequest.newBuilder().setTagFilter("filter").build();
    when(mockEndpoint.doRequest(any(), any()))
        .thenReturn(OK_RESPONSE.toBuilder().addDebugLogs(logMessage).build());

    List<DebugLogMessage> messages = apiClient.getDebugLogs(request);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.GET_DEBUG_LOGS);
    assertThat(requestCaptor.getValue().getDebugLogsRequest()).isEqualTo(request);
    assertThat(messages).containsExactly(logMessage);
  }

  @Test
  public void testStartCapture() throws Exception {
    apiClient.startCapture();

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.START_CAPTURE);
  }

  @Test
  public void testStopCapture() throws Exception {
    apiClient.stopCapture();

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.STOP_CAPTURE);
  }

  @Test
  public void testGetWifiNetworkStatus() throws Exception {
    WifiNetworkStatus testStatus =
        WifiNetworkStatus.newBuilder().addConfiguredNetworkSsids("ssid").build();
    when(mockEndpoint.doRequest(any(), any()))
        .thenReturn(OK_RESPONSE.toBuilder().setWifiNetworkStatus(testStatus).build());

    WifiNetworkStatus returnedStatus = apiClient.getWifiNetworkStatus();

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.WIFI_NETWORK_STATUS);
    assertThat(returnedStatus).isEqualTo(testStatus);
  }

  @Test
  public void testFormatSdCard() throws Exception {
    apiClient.formatSdCard();

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.FORMAT_SD_CARD);
  }

  @Test
  public void testFactoryReset() throws Exception {
    apiClient.factoryReset();

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.FACTORY_RESET);
  }

  @Test
  public void testSetAudioVolume() throws Exception {
    apiClient.setAudioVolume(5);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    ConfigurationRequest configurationRequest = checkConfigurationRequest(requestCaptor.getValue());
    assertThat(configurationRequest.getAudioConfiguration().getVolume()).isEqualTo(5);
  }

  @Test
  public void testGetCameraSt3DBox() throws Exception {
    when(mockEndpoint.doRequest(any(), any()))
        .thenReturn(
            OK_RESPONSE
                .toBuilder()
                .setSt3DBoxResponse(
                    St3DBoxResponse.newBuilder().setData(testCalibration.getSt3DBox()).build())
                .build());
    St3DBoxResponse response = apiClient.getCameraSt3DBox();
    assertThat(response.getData()).isEqualTo(testCalibration.getSt3DBox());
    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType()).isEqualTo(RequestType.GET_CAMERA_ST3D_BOX);
  }

  @Test
  public void testGetCameraSv3DBox() throws Exception {
    CRC32 crc = new CRC32();
    crc.update(testCalibration.getSv3DBox().toByteArray());
    when(mockEndpoint.doRequest(any(), any()))
        .thenAnswer(
            invocation -> {
              CameraApiRequest request = (CameraApiRequest) invocation.getArguments()[0];
              int startIndex =
                  Math.min(
                      testCalibration.getSv3DBox().size(),
                      request.getCameraSv3DBoxRequest().getStartIndex());
              int endIndex;
              if (request.getCameraSv3DBoxRequest().getLength() == 0) {
                endIndex = testCalibration.getSv3DBox().size();
              } else {
                endIndex =
                    Math.min(
                        testCalibration.getSv3DBox().size(),
                        startIndex + request.getCameraSv3DBoxRequest().getLength());
              }
              int length = endIndex - startIndex;
              ByteString chunk =
                  ByteString.copyFrom(
                      testCalibration.getSv3DBox().toByteArray(), startIndex, length);
              return OK_RESPONSE
                  .toBuilder()
                  .setSv3DBoxResponse(
                      Sv3DBoxResponse.newBuilder()
                          .setData(chunk)
                          .setChecksum((int) crc.getValue())
                          .setTotalSize(testCalibration.getSv3DBox().size())
                          .build())
                  .build();
            });
    Sv3DBoxResponse response = apiClient.getCameraSv3DBox();
    assertThat(response.getData()).isEqualTo(testCalibration.getSv3DBox());
    verify(mockEndpoint, atLeast(1))
        .doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType()).isEqualTo(RequestType.GET_CAMERA_SV3D_BOX);
  }

  @Test
  public void testGetCameraSv3DBoxNoChecksum() throws Exception {
    when(mockEndpoint.doRequest(any(), any()))
        .thenAnswer(
            invocation -> {
              CameraApiRequest request = (CameraApiRequest) invocation.getArguments()[0];
              int startIndex =
                  Math.min(
                      testCalibration.getSv3DBox().size(),
                      request.getCameraSv3DBoxRequest().getStartIndex());
              int endIndex;
              if (request.getCameraSv3DBoxRequest().getLength() == 0) {
                endIndex = testCalibration.getSv3DBox().size();
              } else {
                endIndex =
                    Math.min(
                        testCalibration.getSv3DBox().size(),
                        startIndex + request.getCameraSv3DBoxRequest().getLength());
              }
              int length = endIndex - startIndex;
              ByteString chunk =
                  ByteString.copyFrom(
                      testCalibration.getSv3DBox().toByteArray(), startIndex, length);
              return OK_RESPONSE
                  .toBuilder()
                  .setSv3DBoxResponse(
                      Sv3DBoxResponse.newBuilder()
                          .setData(chunk)
                          .setTotalSize(testCalibration.getSv3DBox().size())
                          .build())
                  .build();
            });

    try {
      apiClient.getCameraSv3DBox();
      fail("Did not get expected CameraApiException");
    } catch (CameraApiException e) {
      verify(mockEndpoint, atLeast(1))
          .doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    }
  }

  @Test
  public void setCaptureMode() throws Exception {
    apiClient.setCaptureMode(TEST_CAPTURE_MODE);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    checkCaptureModeRequest(requestCaptor.getValue());
  }

  @Test
  public void doConnectionTest() throws Exception {
    when(mockEndpoint.doRequest(any(), any()))
        .thenReturn(
            OK_RESPONSE.toBuilder().setConnectionTestResponse(CONNECTION_TEST_RESPONSE).build());

    ConnectionTestResponse response = apiClient.doConnectionTest(CONNECTION_TEST_REQUEST);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.CONNECTION_TEST);
    assertThat(requestCaptor.getValue().hasConnectionTestRequest()).isTrue();
    assertThat(requestCaptor.getValue().getConnectionTestRequest())
        .isEqualTo(CONNECTION_TEST_REQUEST);
    assertThat(response).isEqualTo(CONNECTION_TEST_RESPONSE);
  }

  @Test
  public void initiateKeyExchange() throws Exception {
    when(mockEndpoint.doRequest(any(), any()))
        .thenReturn(
            OK_RESPONSE.toBuilder().setKeyExchangeResponse(TEST_KEY_EXCHANGE_RESPONSE).build());

    KeyExchangeResponse response = apiClient.initiateKeyExchange(TEST_PUBLIC_KEY, TEST_SALT);
    assertThat(response).isEqualTo(TEST_KEY_EXCHANGE_RESPONSE);
    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.KEY_EXCHANGE_INITIATE);
    checkKeyExchangeRequest(requestCaptor.getValue());
  }

  @Test
  public void finalizeKeyExchange() throws Exception {
    apiClient.finalizeKeyExchange(TEST_PUBLIC_KEY, TEST_SALT);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.KEY_EXCHANGE_FINALIZE);
    checkKeyExchangeRequest(requestCaptor.getValue());
  }

  @Test
  public void listMedia() throws Exception {
    when(mockEndpoint.doRequest(any(), any()))
        .thenReturn(OK_RESPONSE.toBuilder().setMedia(LIST_MEDIA_TEST_RESPONSE).build());

    ListMediaResponse response = apiClient.listMedia(20, 10);

    assertThat(response).isEqualTo(LIST_MEDIA_TEST_RESPONSE);
    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.LIST_MEDIA);
    assertThat(requestCaptor.getValue().hasListMediaRequest()).isTrue();
    assertThat(requestCaptor.getValue().getListMediaRequest().getStartIndex()).isEqualTo(20);
    assertThat(requestCaptor.getValue().getListMediaRequest().getMediaCount()).isEqualTo(10);
  }

  @Test
  public void deleteMedia() throws Exception {
    Media testMedia =
        Media.newBuilder().setFilename(TEST_FILENAME).addChecksum(TEST_CHECKSUM).build();
    apiClient.deleteMedia(testMedia);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.DELETE_MEDIA);
    assertThat(requestCaptor.getValue().getDeleteMediaRequestCount()).isEqualTo(1);
    assertThat(requestCaptor.getValue().getDeleteMediaRequest(0).getFilename())
        .isEqualTo(TEST_FILENAME);
    assertThat(requestCaptor.getValue().getDeleteMediaRequest(0).getChecksum())
        .isEqualTo(TEST_CHECKSUM);
  }

  @Test
  public void startViewfinderWebrtc() throws Exception {
    apiClient.startViewfinderWebrtc(TEST_WEBRTC_REQUEST);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.START_VIEWFINDER_WEBRTC);
    assertThat(requestCaptor.getValue().getWebrtcRequest()).isEqualTo(TEST_WEBRTC_REQUEST);
  }

  @Test
  public void stopViewfinderWebrtc() throws Exception {
    apiClient.stopViewfinderWebrtc(TEST_WEBRTC_REQUEST);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_DEFAULT));
    checkRequestHeader(requestCaptor.getValue());
    assertThat(requestCaptor.getValue().getType())
        .isEqualTo(CameraApiRequest.RequestType.STOP_VIEWFINDER_WEBRTC);
    assertThat(requestCaptor.getValue().getWebrtcRequest()).isEqualTo(TEST_WEBRTC_REQUEST);
  }

  @Test
  public void approveOtaUpdate() throws Exception {
    apiClient.approveOtaUpdate(TEST_UPDATE_VERSION);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    ConfigurationRequest configurationRequest = checkConfigurationRequest(requestCaptor.getValue());
    assertThat(configurationRequest.getUpdateConfiguration().getDesiredUpdateVersion())
        .isEqualTo(TEST_UPDATE_VERSION);
  }

  @Test
  public void setCurrentTime() throws Exception {
    apiClient.setCurrentTime(new java.util.Date(TEST_TIME), TEST_TIMEZONE);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    ConfigurationRequest configurationRequest = checkConfigurationRequest(requestCaptor.getValue());
    assertThat(configurationRequest.getTimeConfiguration().getTimestamp()).isEqualTo(TEST_TIME);
    assertThat(configurationRequest.getTimeConfiguration().getTimezone()).isEqualTo(TEST_TIMEZONE);
  }

  @Test
  public void removeWifi() throws Exception {
    apiClient.removeWifi("ssid");

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    ConfigurationRequest configurationRequest = checkConfigurationRequest(requestCaptor.getValue());
    assertThat(configurationRequest.hasWifiNetworkConfiguration()).isTrue();
    assertThat(configurationRequest.getWifiNetworkConfiguration().getRemoveNetworkSsidsList())
        .containsExactly("ssid");
  }

  @Test
  public void setVideoMode() throws Exception {
    apiClient.setVideoMode(TEST_VIDEO_MODE);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    CaptureMode captureMode = checkCaptureModeRequest(requestCaptor.getValue());
    assertThat(captureMode.getConfiguredVideoMode()).isEqualTo(TEST_VIDEO_MODE);
  }

  @Test
  public void setPhotoMode() throws Exception {
    apiClient.setPhotoMode(TEST_PHOTO_MODE);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    CaptureMode captureMode = checkCaptureModeRequest(requestCaptor.getValue());
    assertThat(captureMode.getConfiguredPhotoMode()).isEqualTo(TEST_PHOTO_MODE);
  }

  @Test
  public void setLiveStreamMode() throws Exception {
    apiClient.setLiveStreamMode(TEST_LIVE_STREAM_MODE);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    CaptureMode captureMode = checkCaptureModeRequest(requestCaptor.getValue());
    assertThat(captureMode.getConfiguredLiveMode()).isEqualTo(TEST_LIVE_STREAM_MODE);
  }

  @Test
  public void setCaptureType() throws Exception {
    apiClient.setCaptureType(CaptureType.PHOTO);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    CaptureMode captureMode = checkCaptureModeRequest(requestCaptor.getValue());
    assertThat(captureMode.getActiveCaptureType()).isEqualTo(CaptureType.PHOTO);
  }

  @Test
  public void setWhiteBalanceMode() throws Exception {
    apiClient.setWhiteBalanceMode(WhiteBalanceMode.DAYLIGHT);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    CaptureMode captureMode = checkCaptureModeRequest(requestCaptor.getValue());
    assertThat(captureMode.getWhiteBalanceMode()).isEqualTo(WhiteBalanceMode.DAYLIGHT);
  }

  @Test
  public void setMeteringMode() throws Exception {
    apiClient.setMeteringMode(MeteringMode.CENTER);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    CaptureMode captureMode = checkCaptureModeRequest(requestCaptor.getValue());
    assertThat(captureMode.getMeteringMode()).isEqualTo(MeteringMode.CENTER);
  }

  @Test
  public void setIsoLevel() throws Exception {
    apiClient.setIsoLevel(400);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    CaptureMode captureMode = checkCaptureModeRequest(requestCaptor.getValue());
    assertThat(captureMode.getIsoLevel()).isEqualTo(400);
  }

  @Test
  public void setFlatColor() throws Exception {
    apiClient.setFlatColor(true);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    CaptureMode captureMode = checkCaptureModeRequest(requestCaptor.getValue());
    assertThat(captureMode.getFlatColor()).isTrue();
  }

  @Test
  public void setExposureValueAdjustment() throws Exception {
    apiClient.setExposureValueAdjustment(0.1f);

    verify(mockEndpoint).doRequest(requestCaptor.capture(), eq(Priority.PRIORITY_HIGH));
    CaptureMode captureMode = checkCaptureModeRequest(requestCaptor.getValue());
    assertThat(captureMode.getExposureValueAdjustment()).isEqualTo(0.1f);
  }

  private CaptureMode checkCaptureModeRequest(CameraApiRequest request) {
    ConfigurationRequest configurationRequest = checkConfigurationRequest(request);
    assertThat(configurationRequest.hasCaptureMode()).isTrue();
    return configurationRequest.getCaptureMode();
  }

  /** Checks that the request is a configuration request and returns the configuration request. */
  private ConfigurationRequest checkConfigurationRequest(CameraApiRequest request) {
    checkRequestHeader(request);
    assertThat(request.getType()).isEqualTo(CameraApiRequest.RequestType.CONFIGURE);
    assertThat(request.hasConfigurationRequest()).isTrue();
    return request.getConfigurationRequest();
  }

  private void checkRequestHeader(CameraApiRequest request) {
    assertThat(request.hasHeader()).isTrue();
    assertThat(request.getHeader().hasRequestId()).isTrue();

    // Expect the request to expire within the expiration limit.
    assertThat(request.getHeader().getExpirationTimestamp())
        .isEqualTo(
            mockTimestampProvider.getTimestamp() + CameraApiClient.REQUEST_EXPIRATION.getMillis());
  }

  private void checkKeyExchangeRequest(CameraApiRequest request) {
    assertThat(request.hasKeyExchangeRequest()).isTrue();
    assertThat(request.getKeyExchangeRequest().getPublicKey())
        .isEqualTo(ByteString.copyFrom(TEST_PUBLIC_KEY));
    assertThat(request.getKeyExchangeRequest().getSalt()).isEqualTo(ByteString.copyFrom(TEST_SALT));
  }
}
