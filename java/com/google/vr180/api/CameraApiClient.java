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

import com.google.protobuf.ByteString;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiRequest.CameraSv3DBoxRequest;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.AudioConfiguration;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.TimeConfiguration;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.UpdateConfiguration;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiHotspotConfiguration;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiHotspotConfiguration.ChannelPreference;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiNetworkConfiguration;
import com.google.vr180.CameraApi.CameraApiRequest.ConnectionTestRequest;
import com.google.vr180.CameraApi.CameraApiRequest.DebugLogsRequest;
import com.google.vr180.CameraApi.CameraApiRequest.DeleteMediaRequest;
import com.google.vr180.CameraApi.CameraApiRequest.KeyExchangeRequest;
import com.google.vr180.CameraApi.CameraApiRequest.ListMediaRequest;
import com.google.vr180.CameraApi.CameraApiRequest.RequestHeader;
import com.google.vr180.CameraApi.CameraApiRequest.RequestType;
import com.google.vr180.CameraApi.CameraApiRequest.ThumbnailRequest;
import com.google.vr180.CameraApi.CameraApiRequest.WebRtcRequest;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ConnectionTestResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ListMediaResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ResponseStatus.StatusCode;
import com.google.vr180.CameraApi.CameraApiResponse.St3DBoxResponse;
import com.google.vr180.CameraApi.CameraApiResponse.Sv3DBoxResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ThumbnailResponse;
import com.google.vr180.CameraApi.CameraCapabilities;
import com.google.vr180.CameraApi.CameraStatus;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.CaptureMode.CaptureType;
import com.google.vr180.CameraApi.DebugLogMessage;
import com.google.vr180.CameraApi.LiveStreamMode;
import com.google.vr180.CameraApi.Media;
import com.google.vr180.CameraApi.MeteringMode;
import com.google.vr180.CameraApi.PhotoMode;
import com.google.vr180.CameraApi.SleepConfiguration;
import com.google.vr180.CameraApi.VideoMode;
import com.google.vr180.CameraApi.WebRtcSessionDescription;
import com.google.vr180.CameraApi.WhiteBalanceMode;
import com.google.vr180.CameraApi.WifiAccessPointInfo;
import com.google.vr180.CameraApi.WifiNetworkStatus;
import com.google.vr180.api.CameraApiEndpoint.Priority;
import com.google.vr180.common.communication.PaddingCalculator;
import com.google.vr180.common.logging.Log;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.zip.CRC32;
import org.joda.time.Duration;

/** Helper class for running API requests on a Camera Api Endpoint. */
public class CameraApiClient {
  /** Thrown when the response from the Camera is something other than success. */
  public static class CameraApiException extends Exception {
    public final CameraApiResponse.ResponseStatus.StatusCode responseStatus;

    private CameraApiException() {
      this.responseStatus = StatusCode.ERROR;
    }

    private CameraApiException(
        CameraApiResponse.ResponseStatus.StatusCode responseStatus) {
      this.responseStatus = responseStatus;
    }

    @Override
    public String getMessage() {
      return "CameraApiException " + responseStatus;
    }

    @Override
    public String toString() {
      return getMessage();
    }
  }

  /**
   * Interface that returns the current estimate of the camera's clock skew (which is used for
   * setting the expiration time on requests).
   */
  public interface ClockSkewProvider {
    /**
     * Gets the clock skew (the delta between the camera's clock time and the phone's), or null if
     * no estimate is available.
     */
    Long getClockSkew();
  }

  /**
   * Interface that returns the current timestamp (which is used for setting the request ID and
   * expiration time on requests).
   */
  public interface TimestampProvider {
    /**
     * Gets the current Unix timestamp in milliseconds.
     */
    long getTimestamp();
  }

  /** How long requests are valid, in milliseconds. */
  public static final Duration REQUEST_EXPIRATION = Duration.standardMinutes(1);

  private static final String TAG = "CameraApiClient";
  private static final int SV3D_BOX_CHUNK_SIZE = 1000;

  private enum ExpirationType {
    /** Indicates that the request MUST have an Expiration Timestamp to prevent replay attacks. */
    REQUIRED,
    /** Indicates that the request does not require an Expiration Timestamp. */
    NOT_REQUIRED,
  }

  private final CameraApiEndpoint endpoint;
  private final TimestampProvider timestampProvider;
  private final ClockSkewProvider clockSkewProvider;

  /**
   * The default CameraApiClient that assumes no clock skew and uses system clock.
   *
   * @param endpoint The camera api endpoint to perform requests.
   */
  public CameraApiClient(CameraApiEndpoint endpoint) {
    this(endpoint, () -> 0L, () -> System.currentTimeMillis());
  }

  public CameraApiClient(
      CameraApiEndpoint endpoint,
      TimestampProvider timestampProvider,
      ClockSkewProvider clockSkewProvider) {
    this.endpoint = endpoint;
    this.timestampProvider = timestampProvider;
    this.clockSkewProvider = clockSkewProvider;
  }

  /**
   * Requests the status of the camera.
   *
   * @return The camera's status response.
   */
  public CameraStatus getStatus() throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.STATUS)
            .setHeader(createRequestHeader(ExpirationType.NOT_REQUIRED))
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
    return response.getCameraStatus();
  }

  /**
   * Initiates a key exchange with the camera.
   *
   * @param publicKey The public key of the app
   * @param salt The salt of the request
   * @return The key exchange response.
   */
  public CameraApiResponse.KeyExchangeResponse initiateKeyExchange(
      byte[] publicKey, byte[] salt) throws CameraApiException, IOException {
    KeyExchangeRequest keyExchangeRequest =
        KeyExchangeRequest.newBuilder()
            .setPublicKey(ByteString.copyFrom(publicKey))
            .setSalt(ByteString.copyFrom(salt))
            .build();
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.KEY_EXCHANGE_INITIATE)
            .setKeyExchangeRequest(keyExchangeRequest)
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
    return response.getKeyExchangeResponse();
  }

  /**
   * Finalizes the key exchange data from the camera.
   *
   * @param publicKey The same public key as the one passed to initiateKeyExchange
   * @param salt The same salt as the one passed to initiateKeyExchange
   * @return true if the key exchange was finalized.
   */
  public boolean finalizeKeyExchange(byte[] publicKey, byte[] salt)
      throws CameraApiException, IOException {
    KeyExchangeRequest keyExchangeRequest =
        KeyExchangeRequest.newBuilder()
            .setPublicKey(ByteString.copyFrom(publicKey))
            .setSalt(ByteString.copyFrom(salt))
            .build();
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.KEY_EXCHANGE_FINALIZE)
            .setKeyExchangeRequest(keyExchangeRequest)
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseIsValid(request, response);
    return responseHasStatus(response, CameraApiResponse.ResponseStatus.StatusCode.OK);
  }

  public void doWifiConnectRequest(WifiAccessPointInfo accessPointInfo)
      throws CameraApiException, IOException {
    // Add padding to avoid leaking passphrase length.
    WifiAccessPointInfo paddedAccessPointInfo =
        accessPointInfo
            .toBuilder()
            .setPadding(
                ByteString.copyFrom(
                    PaddingCalculator.computePadding(accessPointInfo.getPassword())))
            .build();
    doConfigurationRequest(
        ConfigurationRequest.newBuilder().setLocalWifiInfo(paddedAccessPointInfo).build());
  }

  /** Sets whether the camera should enable it's Wifi Hotspot. */
  public void setWifiHotspotEnabled(boolean enabled) throws CameraApiException, IOException {
    WifiHotspotConfiguration hotspotConfig =
        WifiHotspotConfiguration.newBuilder().setEnableHotspot(enabled).build();
    doConfigurationRequest(
        ConfigurationRequest.newBuilder().setWifiHotspotConfiguration(hotspotConfig).build());
  }

  /** Sets whether the camera should enable it's Wifi Hotspot. */
  public void setWifiHotspotEnabled(
      boolean enabled, boolean clientSupports5Ghz, boolean clientPrefers5Ghz, int preferredChannel)
      throws CameraApiException, IOException {
    WifiHotspotConfiguration.Builder hotspotConfig =
        WifiHotspotConfiguration.newBuilder()
            .setEnableHotspot(enabled)
            .setChannelPreference(
                ChannelPreference.newBuilder()
                    .setClientSupports5Ghz(clientSupports5Ghz)
                    .setClientPrefers5Ghz(clientPrefers5Ghz)
                    .setClientPreferredChannel(preferredChannel));
    doConfigurationRequest(
        ConfigurationRequest.newBuilder().setWifiHotspotConfiguration(hotspotConfig).build());
  }

  public CameraCapabilities getCapabilities() throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.GET_CAPABILITIES)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
    return response.getCapabilities();
  }

  public List<DebugLogMessage> getDebugLogs(DebugLogsRequest logsRequest)
      throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.GET_DEBUG_LOGS)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .setDebugLogsRequest(logsRequest)
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
    return response.getDebugLogsList();
  }

  public St3DBoxResponse getCameraSt3DBox() throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.GET_CAMERA_ST3D_BOX)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
    return response.getSt3DBoxResponse();
  }

  public Sv3DBoxResponse getCameraSv3DBox() throws CameraApiException, IOException {
    ByteString data = ByteString.EMPTY;
    int offset = 0;
    Integer crc = null;

    while (true) {
      Log.d(TAG, "Fetching Sv3D chunk offset=" + offset);
      Sv3DBoxResponse chunkResponse = getCameraSv3DBoxChunk(offset, SV3D_BOX_CHUNK_SIZE);
      data = data.concat(chunkResponse.getData());
      offset = data.size();
      if (data.size() >= chunkResponse.getTotalSize()) {
        break;
      }
      if (crc != null && crc != chunkResponse.getChecksum()) {
        Log.e(TAG, "Sv3DBox changed during fetch.");
        throw new CameraApiException(StatusCode.ERROR);
      }
      crc = chunkResponse.getChecksum();
    }

    if (crc == null) {
      Log.e(TAG, "Sv3DBox checksum missing.");
      throw new CameraApiException(StatusCode.ERROR);
    } else if (getChecksum(data.toByteArray()) != crc) {
      Log.e(TAG, "Sv3DBox data does not match checksum.");
      throw new CameraApiException(StatusCode.ERROR);
    }

    return Sv3DBoxResponse.newBuilder()
        .setData(data)
        .setChecksum(crc)
        .setTotalSize(data.size())
        .build();
  }

  public int getCameraSv3DBoxChecksum() throws CameraApiException, IOException {
    return getCameraSv3DBoxChunk(0, 1).getChecksum();
  }

  /**
   * @param startIndex The start index of the chunk of the Sv3DBox to request
   * @param length The length of the chunk of the Sv3DBox to request
   * @return The Sv3DBoxResponse object representing the chunk requested.
   * @throws CameraApiException
   * @throws IOException
   */
  private Sv3DBoxResponse getCameraSv3DBoxChunk(int startIndex, int length)
      throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.GET_CAMERA_SV3D_BOX)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .setCameraSv3DBoxRequest(
                CameraSv3DBoxRequest.newBuilder()
                    .setStartIndex(startIndex)
                    .setLength(length)
                    .build())
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
    return response.getSv3DBoxResponse();
  }

  public WifiNetworkStatus getWifiNetworkStatus() throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.WIFI_NETWORK_STATUS)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
    return response.getWifiNetworkStatus();
  }

  public void setCaptureMode(CaptureMode captureMode) throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.CONFIGURE)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .setConfigurationRequest(
                ConfigurationRequest.newBuilder().setCaptureMode(captureMode).build())
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_HIGH);
    checkResponseStatus(request, response);
  }

  public ListMediaResponse listMedia(long startIndex, long count)
      throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.LIST_MEDIA)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .setListMediaRequest(
                ListMediaRequest.newBuilder()
                    .setStartIndex(startIndex)
                    .setMediaCount(count)
                    .build())
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
    return response.getMedia();
  }

  /** Asks the camera to delete a media item. */
  public void deleteMedia(Media media) throws CameraApiException, IOException {
    DeleteMediaRequest.Builder deleteMediaRequest =
        DeleteMediaRequest.newBuilder().setFilename(media.getFilename());
    if (media.getChecksumCount() > 0) {
      deleteMediaRequest.setChecksum(media.getChecksum(0));
    }

    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.DELETE_MEDIA)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .addDeleteMediaRequest(deleteMediaRequest.build())
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
    for (CameraApiResponse.ResponseStatus status : response.getDeleteMediaStatusList()) {
      if (status.getStatusCode() != CameraApiResponse.ResponseStatus.StatusCode.OK) {
        throw new CameraApiException(status.getStatusCode());
      }
    }
  }

  /**
   * Fetch an entire thumbnail.
   *
   * @param thumbnailRequest The thumbnail to fetch. The offset property is modified to fetch all
   *     chunks of the file. The length property must be filled in with the maximum response size.
   * @return The fully assembled thumbnail.
   */
  public ByteString getThumbnail(ThumbnailRequest thumbnailRequest)
      throws CameraApiException, IOException {
    ByteString thumbnailData = ByteString.EMPTY;
    int offset = 0;
    Integer crc = null;

    while (true) {
      Log.d(TAG, "Fetching thumbnail " + thumbnailRequest.getFilename() + " offset=" + offset);
      ThumbnailResponse thumbnailResponse =
          getThumbnailChunk(thumbnailRequest.toBuilder().setStartIndex(offset).build());
      thumbnailData = thumbnailData.concat(thumbnailResponse.getData());
      offset = thumbnailData.size();
      if (thumbnailData.size() >= thumbnailResponse.getTotalSize()) {
        break;
      }
      if (crc != null && crc != thumbnailResponse.getChecksum()) {
        Log.e(TAG, "Thumbnail changed during fetch.");
        throw new CameraApiException(StatusCode.ERROR);
      }
      crc = thumbnailResponse.getChecksum();
    }

    return thumbnailData;
  }

  public WebRtcSessionDescription startViewfinderWebrtc(WebRtcRequest webRtcRequest)
      throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.START_VIEWFINDER_WEBRTC)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .setWebrtcRequest(webRtcRequest)
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
    return response.getWebrtcAnswer();
  }

  public void stopViewfinderWebrtc(WebRtcRequest webRtcRequest)
      throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.STOP_VIEWFINDER_WEBRTC)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .setWebrtcRequest(webRtcRequest)
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_DEFAULT);
    checkResponseStatus(request, response);
  }

  public void startCapture() throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.START_CAPTURE)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_HIGH);
    checkResponseStatus(request, response);
  }

  public void stopCapture() throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.STOP_CAPTURE)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_HIGH);
    checkResponseStatus(request, response);
  }

  public void approveOtaUpdate(String version) throws CameraApiException, IOException {
    UpdateConfiguration updateConfiguration =
        UpdateConfiguration.newBuilder().setDesiredUpdateVersion(version).build();
    ConfigurationRequest configurationRequest =
        ConfigurationRequest.newBuilder().setUpdateConfiguration(updateConfiguration).build();
    doConfigurationRequest(configurationRequest);
  }

  /**
   * Configures the current time and timezone on the camera.
   *
   * @param utcNow The time to set on the camera (in UTC).
   * @param timezone The Olson id of the timezone.
   */
  public void setCurrentTime(Date utcNow, String timezone)
      throws CameraApiException, IOException {
    TimeConfiguration timeConfiguration =
        TimeConfiguration.newBuilder().setTimestamp(utcNow.getTime()).setTimezone(timezone).build();
    ConfigurationRequest configurationRequest =
        ConfigurationRequest.newBuilder().setTimeConfiguration(timeConfiguration).build();
    doConfigurationRequest(configurationRequest);
  }

  /**
   * Sets the current volume level on the camera.
   *
   * @param volume The desired volume on a range from 0 to the max_volume in the camera status
   *     (inclusive).
   */
  public void setAudioVolume(int volume) throws CameraApiException, IOException {
    AudioConfiguration audioConfiguration =
        AudioConfiguration.newBuilder().setVolume(volume).build();
    doConfigurationRequest(
        ConfigurationRequest.newBuilder().setAudioConfiguration(audioConfiguration).build());
  }

  /** Set video mode */
  public void setVideoMode(VideoMode videoMode) throws CameraApiException, IOException {
    setCaptureMode(CaptureMode.newBuilder().setConfiguredVideoMode(videoMode).build());
  }

  /** Set photo mode */
  public void setPhotoMode(PhotoMode photoMode) throws CameraApiException, IOException {
    setCaptureMode(CaptureMode.newBuilder().setConfiguredPhotoMode(photoMode).build());
  }

  /** Set live stream mode */
  public void setLiveStreamMode(LiveStreamMode liveStreamMode)
      throws CameraApiException, IOException {
    setCaptureMode(CaptureMode.newBuilder().setConfiguredLiveMode(liveStreamMode).build());
  }

  /** Set capture type */
  public void setCaptureType(CaptureType captureType) throws CameraApiException, IOException {
    setCaptureMode(CaptureMode.newBuilder().setActiveCaptureType(captureType).build());
  }

  /** Set white blance mode */
  public void setWhiteBalanceMode(WhiteBalanceMode whiteBalanceMode)
      throws CameraApiException, IOException {
    setCaptureMode(CaptureMode.newBuilder().setWhiteBalanceMode(whiteBalanceMode).build());
  }

  /** Set metering mode */
  public void setMeteringMode(MeteringMode meteringMode)
      throws CameraApiException, IOException {
    setCaptureMode(CaptureMode.newBuilder().setMeteringMode(meteringMode).build());
  }

  /** Set camera ISO level */
  public void setIsoLevel(int isoLevel) throws CameraApiException, IOException {
    setCaptureMode(CaptureMode.newBuilder().setIsoLevel(isoLevel).build());
  }

  /** Set camera flat color mode */
  public void setFlatColor(boolean flatColor) throws CameraApiException, IOException {
    setCaptureMode(CaptureMode.newBuilder().setFlatColor(flatColor).build());
  }

  /** Set exposure value adjustment */
  public void setExposureValueAdjustment(float exposureValueAdjustment)
      throws CameraApiException, IOException {
    setCaptureMode(
        CaptureMode.newBuilder().setExposureValueAdjustment(exposureValueAdjustment).build());
  }

  /** Fetch a piece of a thumbnail. */
  public ThumbnailResponse getThumbnailChunk(ThumbnailRequest thumbnailRequest)
      throws CameraApiException, IOException {
    // TODO: Support batch fetching thumbnails.
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.GET_THUMBNAIL)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .addThumbnailRequest(thumbnailRequest)
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_LOW);
    checkResponseStatus(request, response);
    if (response.getThumbnailCount() != 1) {
      // If the camera doesn't return a thumbnail, fail.
      throw new CameraApiException(StatusCode.ERROR);
    }

    return response.getThumbnail(0);
  }

  /** Helper to run a configuration request. */
  private void doConfigurationRequest(ConfigurationRequest configurationRequest)
      throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.CONFIGURE)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .setConfigurationRequest(configurationRequest)
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_HIGH);
    checkResponseStatus(request, response);
  }

  /**
   * Tells the camera to format the SD card.
   *
   * @throws CameraApiException if the camera reports an error formatting the card
   * @throws IOException if there is a failure transmitting the request or response to the camera
   */
  public void formatSdCard() throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.FORMAT_SD_CARD)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_HIGH);
    checkResponseStatus(request, response);
  }

  /**
   * Tells the camera to factory reset.
   *
   * @throws CameraApiException if the camera reports an error performing the reset
   * @throws IOException if there is a failure transmitting the request or response to the camera
   */
  public void factoryReset() throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.FACTORY_RESET)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_HIGH);
    checkResponseStatus(request, response);
  }

  /**
   * Configure the camera sleep time
   *
   * @param wakeTimeSeconds The time to remain awake before going to sleep in seconds.
   * @param sleepTimeSeconds The time to remain in sleep mode before powering off in seconds.
   */
  public void configureSleepTime(int wakeTimeSeconds, int sleepTimeSeconds)
      throws CameraApiException, IOException {
    SleepConfiguration sleepConfiguration =
        SleepConfiguration.newBuilder()
            .setWakeTimeSeconds(wakeTimeSeconds)
            .setSleepTimeSeconds(sleepTimeSeconds)
            .build();
    doConfigurationRequest(
        ConfigurationRequest.newBuilder().setSleepConfiguration(sleepConfiguration).build());
  }

  /**
   * Remove the specified wifi from camera wifi configuration
   *
   * @param ssid The SSID of the wifi network to remove
   * @throws CameraApiException if the camera reports an error performing the reset
   * @throws IOException if there is a failure transmitting the request or response to the camera
   */
  public void removeWifi(String ssid) throws CameraApiException, IOException {
    WifiNetworkConfiguration wifiNetworkConfiguration =
        WifiNetworkConfiguration.newBuilder().addRemoveNetworkSsids(ssid).build();
    doConfigurationRequest(
        ConfigurationRequest.newBuilder()
            .setWifiNetworkConfiguration(wifiNetworkConfiguration)
            .build());
  }

  public ConnectionTestResponse doConnectionTest(ConnectionTestRequest connectionTestRequest)
      throws CameraApiException, IOException {
    CameraApiRequest request =
        CameraApiRequest.newBuilder()
            .setType(RequestType.CONNECTION_TEST)
            .setHeader(createRequestHeader(ExpirationType.REQUIRED))
            .setConnectionTestRequest(connectionTestRequest)
            .build();
    CameraApiResponse response = endpoint.doRequest(request, Priority.PRIORITY_HIGH);
    checkResponseStatus(request, response);
    return response.getConnectionTestResponse();
  }

  public void setWifiCountryCode(String countryCode) throws CameraApiException, IOException {
    WifiNetworkConfiguration wifiNetworkConfiguration =
        WifiNetworkConfiguration.newBuilder().setWifiCountryCode(countryCode).build();
    doConfigurationRequest(
        ConfigurationRequest.newBuilder()
            .setWifiNetworkConfiguration(wifiNetworkConfiguration)
            .build());
  }

  private RequestHeader createRequestHeader(ExpirationType expirationType)
      throws CameraApiException {
    long timestamp = timestampProvider.getTimestamp();
    CameraApiRequest.RequestHeader.Builder header =
        CameraApiRequest.RequestHeader.newBuilder().setRequestId(timestamp);
    Log.d(TAG, "request_id = " + timestamp);

    Long clockSkew = clockSkewProvider.getClockSkew();
    if (clockSkew != null) {
      header.setExpirationTimestamp(timestamp + REQUEST_EXPIRATION.getMillis() + clockSkew);
      Log.d(TAG, "expiration_timestamp = " + header.getExpirationTimestamp());
    } else if (expirationType == ExpirationType.REQUIRED) {
      // If the clock skew is unavailable and we have to have an expiration header on this request,
      // just fail.
      Log.e(TAG, "Cannot create expiration timestamp without clock skew estimate");
      throw new CameraApiException(StatusCode.INVALID_REQUEST);
    }

    return header.build();
  }

  private static void checkResponseStatus(
      CameraApiRequest request, CameraApiResponse response)
      throws CameraApiException {
    checkResponseIsValid(request, response);

    if (!responseHasStatus(response, CameraApiResponse.ResponseStatus.StatusCode.OK)) {
      CameraApiResponse.ResponseStatus status = response.getResponseStatus();
      throw new CameraApiException(status.getStatusCode());
    }
  }

  private static void checkResponseIsValid(
      CameraApiRequest request, CameraApiResponse response)
      throws CameraApiException {
    if (!response.hasResponseStatus()) {
      Log.e(TAG, "Response is missing a ResponseStatus");
      throw new CameraApiException(StatusCode.ERROR);
    }

    if (request.hasHeader() && response.getRequestId() != request.getHeader().getRequestId()) {
      // Don't accept a mismatched response (could be an attacker trying a replay).
      Log.e(TAG, "Response's RequestId does not match request.");
      throw new CameraApiException(StatusCode.INVALID_REQUEST);
    }
  }

  private static boolean responseHasStatus(
      CameraApiResponse response,
      CameraApiResponse.ResponseStatus.StatusCode statusCode) {
    CameraApiResponse.ResponseStatus status = response.getResponseStatus();
    return status.getStatusCode() == statusCode;
  }

  private static int getChecksum(byte[] data) {
    CRC32 crc = new CRC32();
    crc.update(data);
    return (int) crc.getValue();
  }
}
