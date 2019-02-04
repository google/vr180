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

package com.google.vr180.capture.camera;

import android.content.Context;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Range;
import android.util.SizeF;
import android.view.Surface;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.device.DebugConfig;
import java.util.List;

/**
 * A class that provides a convenient image callback and starts/stops the Android camera preview
 * using the Camera 2 API.
 *
 * <p>The CameraPreview object will be used by multiple threads: AsyncTask threads: for the
 * open/close/capture mode logics, Main thread: for handling the camera callbacks, OpenGL thread:
 * for starting the preview with a SurfaceTexture.
 *
 * <p>Upon any camera exception or error, the CameraPreview object will simply close the opened
 * camera device, and the object will no longer be usable.
 */
public class CameraPreview {

  /** Callback to notify client when a new video frame capture result is available. */
  public interface CaptureResultCallback {
    /** CaptureResult is available from the given video source. */
    void onCaptureResult(CaptureResult result);
  }

  private static final String TAG = "CameraPreview";
  private static final int HIGHSPEED_FRAMERATE = 120;

  private final Handler eventHandler;
  private final Handler callbackHandler;
  private final CameraManager cameraManager;
  private final String cameraId;
  private final CameraCharacteristics cameraCharacteristics;
  private final CameraConfigurator cameraConfigurator;
  private CameraDevice cameraDevice;
  private CameraCaptureSession currentCaptureSession;

  private float physicalFocalLength;

  // The boolean cameraOpened is set to true when calling CameraPreview.open (which
  // happends before the camera is actually opened), and set to false when closing.
  //
  // This allows us to differentiate the state between CameraPreview.open -> actual open
  // and the state after closeCamera.
  private boolean cameraOpened;

  // A runnable that should be run when camera is opened.
  private Runnable cameraOpenRunnable = null;

  // CameraDevice.StateCallback is called when CameraDevice changes its state.
  private final CameraDevice.StateCallback cameraStateCallback =
      new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
          openCameraDevice(cameraDevice);
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
          closeCameraDevice(cameraDevice, "Camera disconnected: " + cameraDevice);
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
          closeCameraDevice(cameraDevice, "Camera error: " + error);
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
          // Camera device is already closed.
          Log.i(TAG, "Camera closed: " + cameraDevice);
        }
      };

  public CameraPreview(
      Context context,
      Handler eventHandler,
      Handler callbackHandler,
      CameraConfigurator cameraConfigurator) {
    this.eventHandler = eventHandler;
    this.callbackHandler = callbackHandler;
    this.cameraConfigurator = cameraConfigurator;
    cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

    // Select the camera.
    String selectedCameraId = null;
    CameraCharacteristics characteristics = null;
    try {
      selectedCameraId = cameraConfigurator.getPreviewCameraId(cameraManager);
      characteristics = cameraManager.getCameraCharacteristics(selectedCameraId);
    } catch (Exception e) {
      throw new RuntimeException("Exception getting camera and characteristics: ", e);
    }
    cameraId = selectedCameraId;
    cameraCharacteristics = characteristics;
    selectPreviewFocalLength();
  }

  public synchronized boolean open() {
    if (cameraId == null) {
      return false;
    }
    if (cameraOpened) {
      Log.d(TAG, "Camera with id : " + cameraId + " was already opened");
      return true;
    }

    try {
      Log.d(TAG, "Opening camera with id : " + cameraId + "...");
      cameraManager.openCamera(cameraId, cameraStateCallback, eventHandler);
      cameraOpened = true;
      return true;
    } catch (Exception e) {
      throw new RuntimeException("Exception opening camera", e);
    }
  }

  public synchronized void release() {
    closeCamera("Normal camera closure " + cameraDevice, null);
  }

  public synchronized boolean isReadyForCapture() {
    return currentCaptureSession != null;
  }

  /**
   * Creates a caputure session with a list of session surfaces and start preview with a list of
   * active surfaces, which must be a subset of the session surfaces.
   */
  public synchronized void startPreview(
      final List<Surface> sessionSurfaces,
      final List<Surface> activeSurfaces,
      final PreviewConfig previewConfig,
      @Nullable final CaptureResultCallback captureResultCallback) {
    Preconditions.checkNotNull(sessionSurfaces);
    Preconditions.checkState(!sessionSurfaces.isEmpty());
    Preconditions.checkNotNull(activeSurfaces);
    Preconditions.checkState(!activeSurfaces.isEmpty());
    Preconditions.checkNotNull(previewConfig);

    if (currentCaptureSession != null) {
      // Existing session will be auto closed when creating a new one.
      Log.w(TAG, "Replace existing capture session ");
      currentCaptureSession = null;
    }

    Runnable createCaptureSessionRunnable =
        () ->
            createCaptureSession(
                sessionSurfaces, activeSurfaces, previewConfig, captureResultCallback);
    if (cameraDevice == null) {
      Log.i(TAG, "Wait for camera open to create capture session");
      cameraOpenRunnable = createCaptureSessionRunnable;
    } else {
      cameraOpenRunnable = null;
      eventHandler.post(createCaptureSessionRunnable);
    }
  }

  /** Triggers a photo capture given the list of surfaces and returns the an unique id. */
  public synchronized int startPhotoCapture(
      List<Surface> photoSurfaces,
      PreviewConfig previewConfig,
      @Nullable CaptureResultCallback captureResultCallback) {
    Preconditions.checkNotNull(photoSurfaces);
    Preconditions.checkState(!photoSurfaces.isEmpty());
    if (currentCaptureSession == null) {
      Log.e(TAG, "Capture session is still being created");
      return -1;
    }

    // TEMPLATE_STILL_CAPTURE is preset for still capture.
    CaptureRequest.Builder photoRequestBuilder =
        createCaptureRequestBuilder(
            CameraDevice.TEMPLATE_STILL_CAPTURE, previewConfig, photoSurfaces);
    if (photoRequestBuilder == null) {
      Log.e(TAG, "Failed to create CaptureRequest for still capture");
      return -1;
    }

    try {
      return currentCaptureSession.capture(
          photoRequestBuilder.build(), getCaptureCallback(captureResultCallback), callbackHandler);
    } catch (Exception e) {
      Log.e(TAG, "Unable to startPhotoCapture", e);
      return -1;
    }
  }

  // Returns the camera charactersitics of the preview camera.
  public CameraCharacteristics getCameraCharacteristics() {
    return cameraCharacteristics;
  }

  public int getCameraOrientation() {
    return cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
  }

  public float getNormalizedFocalLengthPixels() {
    SizeF sensorSize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
    return physicalFocalLength / sensorSize.getWidth();
  }

  /** Configures the capture for our specific needs. */
  private CaptureRequest.Builder createCaptureRequestBuilder(
      int requestTemplate, PreviewConfig previewConfig, List<Surface> activeSurfaces) {
    Log.d(TAG, "Configuring capture request...");

    CaptureRequest.Builder captureRequestBuilder;

    try {
      captureRequestBuilder = cameraDevice.createCaptureRequest(requestTemplate);
    } catch (Exception e) {
      Log.e(TAG, "Exception creating request builder: ", e);
      return null;
    }

    configureCaptureRequest(captureRequestBuilder, previewConfig);

    // Add all current surfaces.
    for (Surface surface : activeSurfaces) {
      captureRequestBuilder.addTarget(surface);
    }
    return captureRequestBuilder;
  }

  private void configureCaptureRequest(
      CaptureRequest.Builder request, PreviewConfig previewConfig) {
    request.set(CaptureRequest.LENS_FOCAL_LENGTH, physicalFocalLength);
    request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
    request.set(CaptureRequest.CONTROL_AWB_MODE, previewConfig.whiteBalanceMode());
    request.set(CaptureRequest.CONTROL_AWB_LOCK, false);
    request.set(
        CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, previewConfig.exposureAdjustmentValue());

    // Configurate exposure
    if (previewConfig.isManualExposure()) {
      request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
      request.set(CaptureRequest.SENSOR_SENSITIVITY, previewConfig.sensitivity());
      request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewConfig.exposureTimeMs() * 1_000_000L);
    } else {
      request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
      request.set(CaptureRequest.CONTROL_AE_LOCK, false);
    }

    if (Integer.valueOf(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        .equals(request.get(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE))) {
      request.set(
          CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
          CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
    }

    if (Integer.valueOf(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        .equals(request.get(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE))) {
      request.set(
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
          CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
    }

    if (previewConfig.isPhotoMode()) {
      // Set JPEG quality for still capture in case the output format includes JPEG.
      request.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
      // Let the capture also output lens shading map.
      request.set(
          CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
          CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
    } else {
      // Set frame rate for video mode.
      request.set(
          CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
          new Range<Integer>(previewConfig.framesPerSecond(), previewConfig.framesPerSecond()));
    }

    if (previewConfig.scalarCrop() != null) {
      Log.i(TAG, "Set crop: " + previewConfig.scalarCrop());
      request.set(CaptureRequest.SCALER_CROP_REGION, previewConfig.scalarCrop());
    }

    if (DebugConfig.isNoiseReductionDisabled()) {
      Log.d(TAG, "Disable noise reduction");
      request.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
    }

    cameraConfigurator.setDeviceSpecificKeys(request);
  }

  private void selectPreviewFocalLength() {
    physicalFocalLength =
        cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)[0];
  }

  /**
   * Sets repeating request and run the given callback immedidately. When the hardware level is
   * full, the request will include the readback of exposure time and sensitivity.
   */
  private synchronized void setPreviewRepeatingRequest(
      CameraCaptureSession captureSession,
      PreviewConfig previewConfig,
      List<Surface> activeSurfaces,
      @Nullable final CaptureResultCallback captureResultCallback) {
    // Use TEMPLATE_RECORD for video record and TEMPLATE_PREVIEW for photo.
    int requestTemplate =
        previewConfig.isPhotoMode() ? CameraDevice.TEMPLATE_PREVIEW : CameraDevice.TEMPLATE_RECORD;
    CaptureRequest.Builder previewRequestBuilder =
        createCaptureRequestBuilder(requestTemplate, previewConfig, activeSurfaces);
    if (previewRequestBuilder == null) {
      return;
    }

    CameraCaptureSession.CaptureCallback captureCallback =
        getCaptureCallback(captureResultCallback);

    try {
      if (isHighSpeedPreview(previewConfig)) {
        captureSession.setRepeatingBurst(
            ((CameraConstrainedHighSpeedCaptureSession) captureSession)
                .createHighSpeedRequestList(previewRequestBuilder.build()),
            captureCallback,
            callbackHandler);
      } else {
        captureSession.setRepeatingRequest(
            previewRequestBuilder.build(), captureCallback, callbackHandler);
      }
    } catch (Exception e) {
      // It is possible the session is closed.
      Log.e(TAG, "Unable to setRepeatingRequest. Session was possibly closed.");
      return;
    }
    // Save the current capture session after success.
    currentCaptureSession = captureSession;
  }

  private synchronized void openCameraDevice(CameraDevice device) {
    // Checking if the camera is already closed.
    if (!cameraOpened) {
      return;
    }

    cameraDevice = device;
    Log.d(TAG, "Successfully opened camera " + cameraDevice.getId());

    if (cameraOpenRunnable != null) {
      eventHandler.post(cameraOpenRunnable);
      cameraOpenRunnable = null;
    }
  }

  private synchronized void createCaptureSession(
      List<Surface> sessionSurfaces,
      List<Surface> activeSurfaces,
      PreviewConfig previewConfig,
      @Nullable CaptureResultCallback captureResultCallback) {
    if (cameraDevice == null) {
      Log.e(TAG, "Setting preview surfaces without opening camera!");
      return;
    }

    CameraCaptureSession.StateCallback captureSessionCallback =
        getCaptureSessionStateCallback(previewConfig, activeSurfaces, captureResultCallback);

    try {
      cameraConfigurator.preparePreview(previewConfig);
      if (isHighSpeedPreview(previewConfig)) {
        cameraDevice.createConstrainedHighSpeedCaptureSession(
            sessionSurfaces, captureSessionCallback, eventHandler);
      } else {
        cameraDevice.createCaptureSession(sessionSurfaces, captureSessionCallback, eventHandler);
      }
    } catch (Exception e) {
      closeCamera("Exception while creating capture session", e);
      throw new RuntimeException("Camera exception. Killing process", e);
    }
  }

  private CameraCaptureSession.StateCallback getCaptureSessionStateCallback(
      final PreviewConfig previewConfig,
      final List<Surface> activeSurfaces,
      final CaptureResultCallback captureResultCallback) {
    return new CameraCaptureSession.StateCallback() {
      @Override
      public void onClosed(CameraCaptureSession session) {
        Log.d(TAG, "Session closed " + session);
      }

      @Override
      public void onConfigureFailed(CameraCaptureSession session) {
        Log.e(TAG, "Configuration failed. Ignoring for now.");
      }

      @Override
      public void onConfigured(final CameraCaptureSession session) {
        Log.d(TAG, "Capture session configured " + session);
        if (cameraDevice == null) {
          Log.e(TAG, "Camera is closed before CameraCaptureSession.onConfigured");
          return;
        }

        setPreviewRepeatingRequest(session, previewConfig, activeSurfaces, captureResultCallback);
      }
    };
  }

  private CameraCaptureSession.CaptureCallback getCaptureCallback(
      @Nullable final CaptureResultCallback captureResultCallback) {
    if (captureResultCallback != null) {
      return new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(
            CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
          captureResultCallback.onCaptureResult(result);
        }
      };
    }
    return null;
  }

  private synchronized void closeCameraDevice(CameraDevice device, String message) {
    cameraDevice = device;
    closeCamera(message, null);
  }

  private synchronized void closeCamera(String message, @Nullable Throwable tr) {
    // Capture session will be closed when closing the device.
    currentCaptureSession = null;

    if (cameraDevice != null) {
      cameraDevice.close();
      cameraDevice = null;
    }

    cameraOpenRunnable = null;
    cameraOpened = false;
    Log.d(TAG, "Close camera: " + message, tr);
  }

  // Whether the preview needs to use high speed capture session. (120fps).
  private static boolean isHighSpeedPreview(PreviewConfig config) {
    return config.framesPerSecond() >= HIGHSPEED_FRAMERATE;
  }
}
