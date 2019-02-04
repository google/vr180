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

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiRequest.CameraSv3DBoxRequest;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.WifiHotspotConfiguration.ChannelPreference;
import com.google.vr180.CameraApi.CameraApiRequest.ConnectionTestRequest;
import com.google.vr180.CameraApi.CameraApiRequest.DebugLogsRequest;
import com.google.vr180.CameraApi.CameraApiRequest.DeleteMediaRequest;
import com.google.vr180.CameraApi.CameraApiRequest.ListMediaRequest;
import com.google.vr180.CameraApi.CameraApiRequest.RequestType;
import com.google.vr180.CameraApi.CameraApiRequest.ThumbnailRequest;
import com.google.vr180.CameraApi.CameraApiRequest.WebRtcRequest;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ListMediaResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ResponseStatus;
import com.google.vr180.CameraApi.CameraApiResponse.St3DBoxResponse;
import com.google.vr180.CameraApi.CameraApiResponse.Sv3DBoxResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ThumbnailResponse;
import com.google.vr180.CameraApi.CameraStatus;
import com.google.vr180.CameraApi.CameraStatus.AudioVolumeStatus;
import com.google.vr180.CameraApi.CameraStatus.MobileNetworkStatus;
import com.google.vr180.CameraApi.CameraStatus.RecordingStatus;
import com.google.vr180.CameraApi.CameraStatus.RecordingStatus.LiveStreamStatus;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.IndicatorBrightnessConfiguration;
import com.google.vr180.CameraApi.Media;
import com.google.vr180.CameraApi.SleepConfiguration;
import com.google.vr180.CameraApi.Vector3;
import com.google.vr180.CameraApi.WebRtcSessionDescription;
import com.google.vr180.CameraApi.WifiAccessPointInfo;
import com.google.vr180.CameraApi.WifiNetworkStatus;
import com.google.vr180.api.camerainterfaces.CameraInterfaceFactory;
import com.google.vr180.api.camerainterfaces.CaptureManager;
import com.google.vr180.api.camerainterfaces.Exceptions;
import com.google.vr180.api.camerainterfaces.Exceptions.CriticallyLowBatteryException;
import com.google.vr180.api.camerainterfaces.Exceptions.InsufficientStorageException;
import com.google.vr180.api.camerainterfaces.Exceptions.InvalidRequestException;
import com.google.vr180.api.camerainterfaces.Exceptions.ThermalException;
import com.google.vr180.api.camerainterfaces.MediaProvider;
import com.google.vr180.common.communication.PaddingCalculator;
import com.google.vr180.common.logging.Log;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.zip.CRC32;

/**
 * Generic handler for CameraApi api requests. This supports api requests over both the
 * Bluetooth LE and HTTPS interfaces.
 */
public class CameraApiHandler {
  private static final String TAG = "CameraApiHandler";

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final CameraInterfaceFactory interfaceFactory;
  private final ApiHandlerExtension[] apiHandlerExtensions;

  /**
   * Extension to the camera API handler. Currently it supports extending the CameraStatus,
   * configuration requests, and EXTENSIONS requests.
   */
  public interface ApiHandlerExtension {

    /** Attach more info to the {@link CameraStatus} in a {@link RequestType#STATUS} request. */
    void addStatus(CameraStatus.Builder status);

    /**
     * Handles the {@link RequestType#EXTENSIONS} requests.
     *
     * @param request The incoming request.
     * @return The response to the request. null if the request is not handled by this extension.
     */
    CameraApiResponse.Builder handleRequest(CameraApiRequest request);

    /** Handles the {@link RequestType#CONFIGURE} request. */
    void handleConfigurationRequest(CameraApiRequest.ConfigurationRequest request)
        throws Exceptions.InvalidRequestException;
  }

  public CameraApiHandler(CameraInterfaceFactory interfaceFactory) {
    this(interfaceFactory, new ApiHandlerExtension[0]);
  }

  public CameraApiHandler(
      CameraInterfaceFactory interfaceFactory, ApiHandlerExtension[] apiHandlerExtensions) {
    this.interfaceFactory = interfaceFactory;
    this.apiHandlerExtensions = apiHandlerExtensions;
  }

  /**
   * Handles a request.
   *
   * @param request The deserialized request proto.
   * @return The CameraApiResponse to serialize and send to the client.
   */
  public CameraApiResponse handleRequest(CameraApiRequest request) {
    if (request.hasHeader()) {
      if (!request.getHeader().hasRequestId()) {
        Log.w(TAG, "Request is missing request id");
        return createResponse(ResponseStatus.StatusCode.INVALID_REQUEST).build();
      }

      if (request.getHeader().hasExpirationTimestamp()) {
        if (new Date().getTime() > request.getHeader().getExpirationTimestamp()) {
          Log.w(
              TAG,
              "Request was expired: "
                  + new Date(request.getHeader().getExpirationTimestamp()).toString());
          return createResponse(ResponseStatus.StatusCode.INVALID_REQUEST)
              .setRequestId(request.getHeader().getRequestId())
              .build();
        }
      } else {
        // Any request other than a STATUS request must provide an expiration timestamp.
        if (request.getType() != CameraApiRequest.RequestType.STATUS) {
          Log.w(TAG, "Request missing expiration timestamp.");
          return createResponse(ResponseStatus.StatusCode.INVALID_REQUEST)
              .setRequestId(request.getHeader().getRequestId())
              .build();
        }
      }

      if (interfaceFactory.getPairingManager() != null
          && interfaceFactory.getPairingManager().isPairingActive()) {
        Log.w(TAG, "Request during pairing mode.");
        return createResponse(ResponseStatus.StatusCode.ERROR_PAIRING_MODE_ACTIVE)
            .setRequestId(request.getHeader().getRequestId())
            .build();
      }
    } else {
      Log.w(TAG, "Request is missing header.");
      return createResponse(ResponseStatus.StatusCode.INVALID_REQUEST).build();
    }

    CameraApiResponse.Builder response;
    Log.i(TAG, "Handling request (type=" + request.getType() + ")");
    interfaceFactory.getWakeManager().wakePing();
    switch (request.getType()) {
      case STATUS:
        response = handleStatusRequest();
        break;
      case CONFIGURE:
        response = handleConfigurationRequest(request.getConfigurationRequest());
        break;
      case GET_DEBUG_LOGS:
        response = handleGetDebugLogsRequest(request.getDebugLogsRequest());
        break;
      case GET_CAPABILITIES:
        response = handleGetCapabilitiesRequest();
        break;
      case GET_CAMERA_ST3D_BOX:
        response = handleGetCameraSt3DBoxRequest();
        break;
      case GET_CAMERA_SV3D_BOX:
        response = handleGetCameraSv3DBoxRequest(request.getCameraSv3DBoxRequest());
        break;
      case LIST_MEDIA:
        response = handleListMediaRequest(request.getListMediaRequest());
        break;
      case GET_THUMBNAIL:
        response = handleGetThumbnailRequest(request.getThumbnailRequestList());
        break;
      case START_CAPTURE:
        response = handleStartCaptureRequest();
        break;
      case STOP_CAPTURE:
        response = handleStopCaptureRequest();
        break;
      case START_VIEWFINDER_WEBRTC:
        response = handleStartViewfinderWebrtcRequest(request.getWebrtcRequest());
        break;
      case STOP_VIEWFINDER_WEBRTC:
        response = handleStopViewfinderWebrtcRequest(request.getWebrtcRequest());
        break;
      case DELETE_MEDIA:
        response = handleDeleteMediaRequest(request.getDeleteMediaRequestList());
        break;
      case WIFI_NETWORK_STATUS:
        response = handleWifiNetworkStatusRequest();
        break;
      case FORMAT_SD_CARD:
        response = handleFormatSdCardRequest();
        break;
      case FACTORY_RESET:
        response = handleFactoryResetRequest();
        break;
      case CONNECTION_TEST:
        response = handleConnectionTestRequest(request.getConnectionTestRequest());
        break;
      case EXTENSIONS:
        response = handleExtensionsRequest(request);
        break;
      default:
        response = notSupportedResponse();
        break;
    }
    response.setRequestId(request.getHeader().getRequestId());
    return response.build();
  }

  /**
   * Helper for constructing a CameraApiResponse representing success.
   *
   * @return A builder with the ResponseStatus already specified.
   */
  public static CameraApiResponse.Builder okResponse() {
    return createResponse(ResponseStatus.StatusCode.OK);
  }

  /**
   * Helper for constructing a CameraApiResponse for an unsupported request.
   *
   * @return A NOT_SUPPORTED response builder.
   */
  public static CameraApiResponse.Builder notSupportedResponse() {
    return createResponse(ResponseStatus.StatusCode.NOT_SUPPORTED);
  }

  /**
   * Helper for constructing a CameraApiResponse for an invalid request.
   *
   * @return an INVALID_REQUEST response builder.
   */
  public static CameraApiResponse.Builder invalidRequestResponse() {
    return createResponse(ResponseStatus.StatusCode.INVALID_REQUEST);
  }

  /**
   * Helper for constructing a CameraApiResponse for an unauthorized request.
   *
   * @return an UNAUTHORIZED_REQUEST response builder.
   */
  public static CameraApiResponse.Builder unauthorizedRequestResponse() {
    return createResponse(ResponseStatus.StatusCode.UNAUTHORIZED_REQUEST);
  }

  /**
   * Helper for constructing a CameraApiResponse for a request that fails due to critically low
   * battery.
   *
   * @return a CRITICALLY_LOW_BATTERY response builder.
   */
  public static CameraApiResponse.Builder criticallyLowBatteryResponse() {
    return createResponse(ResponseStatus.StatusCode.CRITICALLY_LOW_BATTERY);
  }

  /**
   * Helper for constructing a CameraApiResponse for a request that fails due to insufficient
   * storage.
   *
   * @return an INSUFFICIENT_STORAGE response builder.
   */
  public static CameraApiResponse.Builder insufficientStorageResponse() {
    return createResponse(ResponseStatus.StatusCode.INSUFFICIENT_STORAGE);
  }

  /**
   * Helper for constructing a CameraApiResponse for a request that fails due to the camera
   * overheating.
   *
   * @return a THERMAL_ERROR response builder.
   */
  public static CameraApiResponse.Builder thermalErrorResponse() {
    return createResponse(ResponseStatus.StatusCode.THERMAL_ERROR);
  }

  public static CameraApiResponse.Builder createResponse(
      ResponseStatus.StatusCode statusCode) {
    return CameraApiResponse.newBuilder().setResponseStatus(createResponseStatus(statusCode));
  }

  /**
   * Helper for creating a ResponseStatus for the given statusCode.
   *
   * @param statusCode The status code to add to the response status
   * @return a ResponseStatus with the specified status code
   */
  public static ResponseStatus createResponseStatus(ResponseStatus.StatusCode statusCode) {
    return ResponseStatus.newBuilder().setStatusCode(statusCode).build();
  }

  private CameraApiResponse.Builder handleStatusRequest() {
    CameraStatus.Builder statusBuilder =
        CameraStatus.newBuilder()
            .setDeviceTimestamp(new Date().getTime())
            .setDeviceTimezone(TimeZone.getDefault().getID())
            .setUpdateStatus(interfaceFactory.getUpdateManager().getUpdateStatus())
            .setBatteryStatus(interfaceFactory.getBatteryStatusProvider().getBatteryStatus())
            .setStorageStatus(interfaceFactory.getStorageStatusProvider().getStorageStatus())
            .setDeviceTemperature(interfaceFactory.getTemperatureProvider().getTemperature())
            .setHttpServerStatus(interfaceFactory.getNetworkManager().getHttpServerStatus())
            .setMediaLastModifiedTime(
                interfaceFactory.getMediaProvider().getLastModified().getTime())
            .setDeviceTemperatureStatus(
                interfaceFactory.getTemperatureProvider().getDeviceTemperatureStatus())
            .setDeviceTemperatureAboveErrorThreshold(
                interfaceFactory.getTemperatureProvider().getDeviceTemperatureAboveErrorThreshold());
    String activeWifiSsid = interfaceFactory.getNetworkManager().getActiveWifiSsid();
    if (activeWifiSsid != null) {
      statusBuilder.setActiveWifiSsid(activeWifiSsid);
    }
    statusBuilder.setWifiStatus(interfaceFactory.getNetworkManager().getWifiStatus());

    CaptureManager captureManager = interfaceFactory.getCaptureManager();
    RecordingStatus.Builder recordingStatusBuilder = RecordingStatus.newBuilder();
    if (captureManager.isRecording()) {
      recordingStatusBuilder.setRecordingState(RecordingStatus.RecordingState.RECORDING);
      recordingStatusBuilder.setRecordingStartTime(
          captureManager.getRecordingStartTime().getTime());
    } else {
      recordingStatusBuilder.setRecordingState(RecordingStatus.RecordingState.IDLE);
    }
    LiveStreamStatus liveStreamStatus = captureManager.getLiveStreamStatus();
    if (liveStreamStatus != null) {
      recordingStatusBuilder.setLiveStreamStatus(liveStreamStatus);
    }
    statusBuilder.setRecordingStatus(recordingStatusBuilder.build());

    CaptureMode activeCaptureMode = interfaceFactory.getCaptureManager().getActiveCaptureMode();
    if (activeCaptureMode != null) {
      statusBuilder.setActiveCaptureMode(activeCaptureMode);
    }
    MobileNetworkStatus mobileNetworkStatus =
        interfaceFactory.getMobileNetworkManager().getMobileNetworkStatus();
    if (mobileNetworkStatus != null) {
      statusBuilder.setMobileNetworkStatus(mobileNetworkStatus);
    }
    Vector3 gravityVector = interfaceFactory.getGravityVectorProvider().getGravityVector();
    if (gravityVector != null) {
      statusBuilder.setGravityVector(gravityVector);
    }

    WifiAccessPointInfo hotspotInfo =
        interfaceFactory.getHotspotManager().getHotspotAccessPointInfo();
    if (hotspotInfo != null) {
      hotspotInfo =
          hotspotInfo
              .toBuilder()
              .setPadding(
                  ByteString.copyFrom(PaddingCalculator.computePadding(hotspotInfo.getPassword())))
              .build();
      statusBuilder.setHotspotAccessPointInfo(hotspotInfo);
    }

    AudioVolumeStatus audioVolumeStatus =
        interfaceFactory.getAudioVolumeManager().getVolumeStatus();
    if (audioVolumeStatus != null) {
      statusBuilder.setAudioVolume(audioVolumeStatus);
    }

    IndicatorBrightnessConfiguration brightness =
        interfaceFactory.getCameraSettings().getIndicatorBrightness();
    if (brightness != null) {
      statusBuilder.setIndicatorBrightness(brightness);
    }

    SleepConfiguration sleepConfiguration =
        interfaceFactory.getCameraSettings().getSleepConfiguration();
    if (sleepConfiguration != null) {
      statusBuilder.setSleepConfiguration(sleepConfiguration);
    }

    for (ApiHandlerExtension extension : apiHandlerExtensions) {
      extension.addStatus(statusBuilder);
    }

    return okResponse().setCameraStatus(statusBuilder.build());
  }

  private CameraApiResponse.Builder handleConfigurationRequest(ConfigurationRequest request) {
    if (request.hasCaptureMode()) {
      try {
        setCaptureMode(request.getCaptureMode());
      } catch (InvalidRequestException e) {
        return invalidRequestResponse();
      }
    }
    if (request.hasWifiNetworkConfiguration()) {
      mainHandler.post(
          () ->
              interfaceFactory
                  .getNetworkManager()
                  .updateWifiNetworkConfiguration(request.getWifiNetworkConfiguration()));
    }
    if (request.hasLocalWifiInfo()) {
      mainHandler.post(
          () -> interfaceFactory.getNetworkManager().startWifiConnect(request.getLocalWifiInfo()));
    }
    if (request.hasMobileDataConfiguration()) {
      try {
        interfaceFactory
            .getMobileNetworkManager()
            .setMobileNetworkEnabled(request.getMobileDataConfiguration().getEnabled());
      } catch (UnsupportedOperationException e) {
        return notSupportedResponse();
      }
    }
    if (request.hasUpdateConfiguration()) {
      interfaceFactory.getUpdateManager().applyUpdate(request.getUpdateConfiguration());
    }
    if (request.hasTimeConfiguration()) {
      interfaceFactory.getCameraSettings().setTime(request.getTimeConfiguration());
    }
    if (request.hasWifiHotspotConfiguration()) {
      if (request.getWifiHotspotConfiguration().getEnableHotspot()) {
        ChannelPreference channelPreference =
            request.getWifiHotspotConfiguration().hasChannelPreference()
                ? request.getWifiHotspotConfiguration().getChannelPreference()
                : null;
        interfaceFactory.getHotspotManager().startHotspot(channelPreference);
      } else {
        interfaceFactory.getHotspotManager().shutdownHotspot();
      }
    }
    if (request.hasAudioConfiguration()) {
      interfaceFactory
          .getAudioVolumeManager()
          .updateAudioConfiguration(request.getAudioConfiguration());
    }
    if (request.hasIndicatorBrightnessConfiguration()) {
      interfaceFactory
          .getCameraSettings()
          .setIndicatorBrightness(request.getIndicatorBrightnessConfiguration());
    }
    if (request.hasSleepConfiguration()) {
      interfaceFactory.getCameraSettings().setSleepConfiguration(request.getSleepConfiguration());
    }

    for (ApiHandlerExtension extension : apiHandlerExtensions) {
      try {
        extension.handleConfigurationRequest(request);
      } catch (InvalidRequestException e) {
        return invalidRequestResponse();
      }
    }

    return okResponse();
  }

  private CameraApiResponse.Builder handleGetDebugLogsRequest(DebugLogsRequest request) {
    return okResponse()
        .addAllDebugLogs(interfaceFactory.getDebugLogsProvider().getDebugLogs(request));
  }

  private CameraApiResponse.Builder handleGetCapabilitiesRequest() {
    return okResponse()
        .setCapabilities(interfaceFactory.getCapabilitiesProvider().getCapabilities());
  }

  private CameraApiResponse.Builder handleGetCameraSt3DBoxRequest() {
    return okResponse()
        .setSt3DBoxResponse(
            St3DBoxResponse.newBuilder()
                .setData(interfaceFactory.getCameraSettings().getCameraCalibration().getSt3DBox()));
  }

  private CameraApiResponse.Builder handleGetCameraSv3DBoxRequest(
      CameraSv3DBoxRequest request) {
    CameraApiResponse.Builder response = okResponse();
    ByteString sv3DBox = interfaceFactory.getCameraSettings().getCameraCalibration().getSv3DBox();
    int startIndex = Math.min(sv3DBox.size(), request.getStartIndex());
    int endIndex;
    if (request.getLength() == 0) {
      endIndex = sv3DBox.size();
    } else {
      endIndex = Math.min(sv3DBox.size(), startIndex + request.getLength());
    }
    int length = endIndex - startIndex;
    ByteString responseBytes = ByteString.copyFrom(sv3DBox.toByteArray(), startIndex, length);
    Log.d(TAG, "Sending response of " + responseBytes.size() + "/" + sv3DBox.size());
    response.setSv3DBoxResponse(
        Sv3DBoxResponse.newBuilder()
            .setData(responseBytes)
            .setChecksum(getChecksum(sv3DBox.toByteArray()))
            .setTotalSize(sv3DBox.size())
            .build());
    return response;
  }

  private void setCaptureMode(CaptureMode requestedCaptureMode) throws InvalidRequestException {
    CaptureMode.Builder newCaptureMode;
    CaptureMode activeCaptureMode = interfaceFactory.getCaptureManager().getActiveCaptureMode();
    if (activeCaptureMode == null) {
      newCaptureMode = CaptureMode.newBuilder();
    } else {
      newCaptureMode = activeCaptureMode.toBuilder();
    }
    newCaptureMode.mergeFrom(requestedCaptureMode);
    interfaceFactory.getCaptureManager().setActiveCaptureMode(newCaptureMode.build());
  }

  private CameraApiResponse.Builder handleListMediaRequest(ListMediaRequest listMediaRequest) {
    MediaProvider provider = interfaceFactory.getMediaProvider();
    ListMediaResponse.Builder result = ListMediaResponse.newBuilder();
    List<Media> media =
        provider.getMedia(listMediaRequest.getStartIndex(), listMediaRequest.getMediaCount());
    for (Media mediaItem : media) {
      result.addMedia(mediaItem);
    }
    result.setTotalCount(provider.getMediaCount());
    result.setLastModifiedTime(provider.getLastModified().getTime());
    return okResponse().setMedia(result.build());
  }

  private CameraApiResponse.Builder handleDeleteMediaRequest(
      List<DeleteMediaRequest> deleteMediaRequests) {
    CameraApiResponse.Builder result = okResponse();
    for (DeleteMediaRequest request : deleteMediaRequests) {
      // TODO: Validate the file checksum before deleting.
      try {
        interfaceFactory.getFileProvider().deleteFile(request.getFilename());
      } catch (FileNotFoundException e) {
        // If the file is already missing, that's ok.
        Log.d(TAG, "Tried to delete a missing file.");
      }
      result.addDeleteMediaStatus(createResponseStatus(ResponseStatus.StatusCode.OK));
    }
    return result;
  }

  private CameraApiResponse.Builder handleWifiNetworkStatusRequest() {
    WifiNetworkStatus wifiNetworkStatus =
        interfaceFactory.getNetworkManager().getWifiNetworkStatus();
    if (wifiNetworkStatus != null) {
      return okResponse()
          .setWifiNetworkStatus(interfaceFactory.getNetworkManager().getWifiNetworkStatus());
    } else {
      return createResponse(ResponseStatus.StatusCode.ERROR);
    }
  }

  private CameraApiResponse.Builder handleConnectionTestRequest(
      ConnectionTestRequest connectionTestRequest) {
    return okResponse()
        .setConnectionTestResponse(
            interfaceFactory.getConnectionTester().testConnection(connectionTestRequest));
  }

  private CameraApiResponse.Builder handleGetThumbnailRequest(
      List<ThumbnailRequest> thumbnailRequests) {
    MediaProvider provider = interfaceFactory.getMediaProvider();
    CameraApiResponse.Builder response = okResponse();
    for (ThumbnailRequest request : thumbnailRequests) {
      byte[] thumbnail;
      try {
        thumbnail = provider.getThumbnail(request);
      } catch (Exception e) {
        Log.e(TAG, "Error generating thumbnail.", e);
        return createResponse(ResponseStatus.StatusCode.ERROR);
      }

      int offset = Math.min(thumbnail.length, request.getStartIndex());
      int end;
      if (request.getLength() == 0) {
        end = thumbnail.length;
      } else {
        end = Math.min(thumbnail.length, offset + request.getLength());
      }
      int length = end - offset;
      ByteString responseBytes = ByteString.copyFrom(thumbnail, offset, length);
      Log.d(TAG, "Sending response of " + responseBytes.size() + "/" + thumbnail.length);
      response.addThumbnail(
          ThumbnailResponse.newBuilder()
              .setData(responseBytes)
              .setChecksum(getChecksum(thumbnail))
              .setTotalSize(thumbnail.length)
              .build());
    }
    return response;
  }

  private CameraApiResponse.Builder handleStartCaptureRequest() {
    try {
      interfaceFactory.getCaptureManager().startCapture();
    } catch (CriticallyLowBatteryException e) {
      return criticallyLowBatteryResponse();
    } catch (InsufficientStorageException e) {
      return insufficientStorageResponse();
    } catch (ThermalException e) {
      return thermalErrorResponse();
    }
    return okResponse();
  }

  private CameraApiResponse.Builder handleStopCaptureRequest() {
    interfaceFactory.getCaptureManager().stopCapture();
    return okResponse();
  }

  private CameraApiResponse.Builder handleStartViewfinderWebrtcRequest(WebRtcRequest request) {
    WebRtcSessionDescription answerDescription = null;
    try {
      answerDescription = interfaceFactory.getViewfinderProvider().startViewfinderWebrtc(request);
    } catch (IOException e) {
      Log.e(TAG, "Error handling WebRtc request", e);
      return invalidRequestResponse();
    }

    if (answerDescription == null) {
      return notSupportedResponse();
    }
    return okResponse().setWebrtcAnswer(answerDescription);
  }

  private CameraApiResponse.Builder handleStopViewfinderWebrtcRequest(WebRtcRequest request) {
    interfaceFactory.getViewfinderProvider().stopViewfinderWebrtc(request);
    return okResponse();
  }

  private CameraApiResponse.Builder handleFormatSdCardRequest() {
    // Queue the formatting on a background thread since it can be quite slow.
    getBackgroundExecutor().execute(() -> interfaceFactory.getCameraSettings().formatStorage());
    return okResponse();
  }

  private CameraApiResponse.Builder handleFactoryResetRequest() {
    interfaceFactory.getCameraSettings().factoryReset();
    return okResponse();
  }

  private CameraApiResponse.Builder handleExtensionsRequest(CameraApiRequest request) {
    for (ApiHandlerExtension extension : apiHandlerExtensions) {
      CameraApiResponse.Builder response = extension.handleRequest(request);
      // Only one extension is allowed to handle for each request.
      if (response != null) {
        return response;
      }
    }
    return notSupportedResponse();
  }

  private static int getChecksum(byte[] data) {
    CRC32 crc = new CRC32();
    crc.update(data);
    return (int) crc.getValue();
  }

  /** Overridden in unit tests to use a different executor for background tasks. */
  @VisibleForTesting
  protected Executor getBackgroundExecutor() {
    return AsyncTask.THREAD_POOL_EXECUTOR;
  }
}
