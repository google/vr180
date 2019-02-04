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

package com.google.vr180.capture;

import android.content.Context;
import android.opengl.GLSurfaceView;
import com.google.vr180.CameraApi.CameraCalibration;
import com.google.vr180.CameraApi.CameraStatus.RecordingStatus.LiveStreamStatus;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.CaptureMode.CaptureType;
import com.google.vr180.CameraApi.LiveStreamMode;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.CaptureManager;
import com.google.vr180.api.camerainterfaces.Exceptions.InsufficientStorageException;
import com.google.vr180.api.camerainterfaces.Exceptions.InvalidRequestException;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.api.camerainterfaces.ViewfinderCaptureSource;
import com.google.vr180.capture.camera.CameraCapture;
import com.google.vr180.capture.camera.CameraConfigurator;
import com.google.vr180.capture.camera.PreviewConfigProvider;
import com.google.vr180.capture.motion.MotionCaptureSource;
import com.google.vr180.capture.video.SurfaceVideoCaptureSource;
import com.google.vr180.capture.video.SurfaceViewfinderCaptureSource;
import com.google.vr180.common.logging.Log;
import com.google.vr180.device.DebugConfig;
import com.google.vr180.device.DeviceInfo;
import com.google.vr180.media.metadata.ProjectionMetadata;
import com.google.vr180.media.metadata.ProjectionMetadataProvider;
import com.google.vr180.media.metadata.VrMetadataInjector;
import java.util.Date;

/** A implementation of CaptureManager. */
public class CaptureManagerImpl implements CaptureManager {
  /** Callback on capture errors */
  public interface CaptureErrorCallback {
    /** An error occurred for capture. */
    void onError();
  }

  private static final String TAG = "CaptureManagerImpl";

  private final CameraCapture cameraCapture;
  private final SurfaceVideoCaptureSource videoCaptureSource;
  private final MotionCaptureSource motionCaptureSource;
  private final SurfaceViewfinderCaptureSource viewfinderCaptureSource;
  private final MediaMuxCapturePipelineManager capturePipelineManager;
  private final StatusNotifier statusNotifier;
  private final CaptureErrorCallback errorCallback;
  private final CameraSettings settings;
  private final CapturePathProvider capturePathProvider;
  private final CalibrationRecorder calibrationRecorder;
  private final FreeSpaceChecker freeSpaceChecker;
  private ProjectionMetadata projectionMetadata;
  private boolean recording;
  private Date recordingStartTime;
  private boolean paused = true;

  public CaptureManagerImpl(
      Context context,
      CameraSettings settings,
      StorageStatusProvider storageStatusProvider,
      StatusNotifier statusNotifier,
      ProjectionMetadataProvider metadataProvider,
      PreviewConfigProvider previewConfigProvider,
      CameraConfigurator cameraConfigurator,
      DeviceInfo deviceInfo,
      CaptureErrorCallback errorCallback) {
    this.statusNotifier = statusNotifier;
    this.errorCallback = errorCallback;
    this.settings = settings;
    motionCaptureSource =
        new MotionCaptureSource(
            context, deviceInfo.deviceToImuTransform(), deviceInfo.getImuTimestampOffsetNs());
    cameraCapture =
        new CameraCapture(
            context,
            metadataProvider,
            motionCaptureSource,
            previewConfigProvider,
            cameraConfigurator);
    viewfinderCaptureSource = new SurfaceViewfinderCaptureSource();
    videoCaptureSource =
        new SurfaceVideoCaptureSource(cameraCapture.getCameraPreview().getCameraOrientation());
    cameraCapture.addCameraProcessor(videoCaptureSource);
    cameraCapture.addCameraProcessor(viewfinderCaptureSource);
    calibrationRecorder = new CalibrationRecorder(context, videoCaptureSource, motionCaptureSource);
    capturePipelineManager =
        new MediaMuxCapturePipelineManager(
            context, videoCaptureSource, motionCaptureSource, status -> notifyCaptureError(status));
    freeSpaceChecker = new FreeSpaceChecker(storageStatusProvider);
    capturePathProvider = new CapturePathProvider(storageStatusProvider);
  }

  public void onResume() {
    Log.d(TAG, "onResume");
    cameraCapture.onResume();
    updateCaptureMode(getActiveCaptureMode());
    motionCaptureSource.configureLatency(true);
    paused = false;
  }

  public void onPause() {
    paused = true;
    Log.d(TAG, "onPause");
    stopCapture();
    motionCaptureSource.configureLatency(false);
    cameraCapture.onPause();
  }

  @Override
  public synchronized void startCapture() throws InsufficientStorageException {
    if (paused) {
      Log.d(TAG, "Cannot start capture when camera is paused");
      return;
    }
    if (!cameraCapture.getCameraPreview().isReadyForCapture()) {
      notifyError("Camera preview is not ready");
      return;
    }

    CaptureMode captureMode = getActiveCaptureMode();
    CaptureType activeCaptureType = captureMode.getActiveCaptureType();

    if (activeCaptureType != CaptureType.VIDEO
        && activeCaptureType != CaptureType.LIVE
        && activeCaptureType != CaptureType.PHOTO) {
      Log.d(TAG, "Capture type not implemented yet " + activeCaptureType);
      return;
    }

    if (activeCaptureType != CaptureType.LIVE && freeSpaceChecker.isLowSpace()) {
      notifyError("Low space");
      return;
    }

    boolean isCalibrationEnabled = DebugConfig.isCalibrationEnabled();
    if (isCalibrationEnabled && projectionMetadata.stereoReprojectionConfig != null) {
      // Current capture mode should be fisheye photo or video.
      notifyError("Calibration capture cannot be done with dewarp. Try toggling mode.");
      return;
    }

    recordingStartTime = new Date();

    Log.d(TAG, "Starting capture: " + captureMode);
    if (activeCaptureType == CaptureType.VIDEO) {
      if (isCalibrationEnabled
          && !calibrationRecorder.open(capturePathProvider.getCalibrationDir(recordingStartTime))) {
        recordingStartTime = null;
        notifyError("Failed to open calibration recorder.");
        return;
      }
      capturePipelineManager.startCapture(
          MediaFormatFactory.createVideoFormat(captureMode.getConfiguredVideoMode()),
          MediaFormatFactory.createAudioFormat(captureMode.getConfiguredVideoMode()),
          MediaFormatFactory.createMotionFormat(captureMode.getConfiguredVideoMode()),
          capturePathProvider.getVideoPath(recordingStartTime, isCalibrationEnabled),
          null,
          new VrMetadataInjector(projectionMetadata));
      recording = true;
      freeSpaceChecker.scheduleRepeatingFreeSpaceCheck(
          () -> {
            stopCapture();
            notifyError("Low space");
          });
    } else if (activeCaptureType == CaptureType.LIVE) {
      LiveStreamMode mode = captureMode.getConfiguredLiveMode();
      if (mode.getRtmpEndpoint().isEmpty() || mode.getStreamNameKey().isEmpty()) {
        Log.e(TAG, "Live streaming is not configured yet.");
        recordingStartTime = null;
        errorCallback.onError();
        return;
      }
      capturePipelineManager.startCapture(
          MediaFormatFactory.createVideoFormat(mode.getVideoMode()),
          MediaFormatFactory.createAudioFormat(mode.getVideoMode()),
          null,
          mode.getRtmpEndpoint(),
          mode.getStreamNameKey(),
          null);
      recording = true;
    } else if (activeCaptureType == CaptureType.PHOTO) {
      if (!cameraCapture.startPhotoCapture(
          capturePathProvider.getPhotoPath(recordingStartTime, isCalibrationEnabled))) {
        notifyError("Cannot start photo capture");
      }
      recordingStartTime = null;
    }
    statusNotifier.notifyStatusChanged();
  }

  @Override
  public synchronized void stopCapture() {
    if (recording) {
      freeSpaceChecker.cancelRepeatingFreeSpaceCheck();
      Log.d(TAG, "stopCapture");
      capturePipelineManager.stopCapture();
      recordingStartTime = null;
      recording = false;
      statusNotifier.notifyStatusChanged();
      settings.clearLiveEndPoint();
      calibrationRecorder.close();
    }
  }

  @Override
  public boolean isRecording() {
    return recording;
  }

  @Override
  public Date getRecordingStartTime() {
    return recordingStartTime;
  }

  @Override
  public CaptureMode getActiveCaptureMode() {
    return settings.getActiveCaptureMode();
  }

  @Override
  public void setActiveCaptureMode(CaptureMode mode) throws InvalidRequestException {
    if (paused
        || recording
        || cameraCapture.hasPendingCapture()
        || (mode.getActiveCaptureType() != CaptureType.VIDEO
            && mode.getActiveCaptureType() != CaptureType.LIVE
            && mode.getActiveCaptureType() != CaptureType.PHOTO)) {
      throw new InvalidRequestException();
    }

    settings.setActiveCaptureMode(mode);
    statusNotifier.notifyStatusChanged();
    updateCaptureMode(getActiveCaptureMode());
  }

  @Override
  public CameraCalibration getActiveCalibration() {
    return settings.getCameraCalibration();
  }

  @Override
  public LiveStreamStatus getLiveStreamStatus() {
    return null;
  }

  public void setCaptureView(GLSurfaceView glSurfaceView) {
    cameraCapture.setCaptureView(glSurfaceView);
  }

  // Notify capture error and stop recording
  private void notifyCaptureError(int status) {
    Log.d(TAG, "notifyCaptureError " + status);
    freeSpaceChecker.cancelRepeatingFreeSpaceCheck();
    recording = false;
    recordingStartTime = null;
    statusNotifier.notifyStatusChanged();
    errorCallback.onError();
  }

  // Update preview configuration according to capture mode.
  private void updateCaptureMode(CaptureMode mode) {
    Log.i(TAG, "updateCaptureMode");
    if (!cameraCapture.updatePreviewConfig(mode)) {
      Log.i(TAG, "PreviewConfig did not change");
      return;
    }

    // Update spherical metadata.
    projectionMetadata = cameraCapture.getProjectionMetadata();
    settings.setSphericalMetadata(projectionMetadata.st3d, projectionMetadata.sv3d);
    viewfinderCaptureSource.setStereoMode(projectionMetadata.stereoMode);

    // Update the mesh projections for the shaders.
    videoCaptureSource.setStereoReprojectionConfig(projectionMetadata.stereoReprojectionConfig);
  }

  public ViewfinderCaptureSource getViewfinderCaptureSource() {
    return viewfinderCaptureSource;
  }

  private void notifyError(String message) {
    Log.e(TAG, "Error: " + message);
    errorCallback.onError();
  }
}
