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
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CaptureResult;
import android.opengl.GLES11Ext;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import com.google.common.base.Preconditions;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.capture.motion.MotionCaptureSource;
import com.google.vr180.capture.photo.PhotoCapturer;
import com.google.vr180.capture.photo.RawPhotoCapturer;
import com.google.vr180.capture.photo.VRPhotoCapturer;
import com.google.vr180.capture.renderer.PreviewRenderer;
import com.google.vr180.common.logging.Log;
import com.google.vr180.common.opengl.EglSurface;
import com.google.vr180.common.opengl.Texture;
import com.google.vr180.device.DebugConfig;
import com.google.vr180.media.metadata.ProjectionMetadata;
import com.google.vr180.media.metadata.ProjectionMetadataProvider;
import com.google.vr180.media.video.FramerateReporter;
import java.util.ArrayList;
import java.util.List;

/** A class that processes and renders a camera video stream. */
public class CameraCapture
    implements SurfaceTexture.OnFrameAvailableListener, CameraPreview.CaptureResultCallback {

  private static final String TAG = "CameraCapture";

  private final Context context;
  private final ArrayList<CameraProcessor> processors = new ArrayList<CameraProcessor>();
  private final float[] textureMatrix = new float[16];
  private final FramerateReporter framerateReporter = new FramerateReporter("Preview");
  // Thread for handling camera events.
  private final HandlerThread cameraThread = new HandlerThread(TAG);
  private final Handler cameraHandler;
  // OpenGL thread for tasks that need OpenGL.
  private final HandlerThread glThread = new HandlerThread("GLCameraThread");
  private final Handler glHandler;
  private final CameraPreview cameraPreview;
  private final EglSurface eglSurface;
  private final SharedEGLContextFactory eglContextFactory;
  private final Texture texture;
  private final SurfaceTexture surfaceTexture;
  private final ProjectionMetadataProvider metadataProvider;
  private final PreviewRenderer previewRenderer;
  private final MotionCaptureSource motionCaptureSource;
  private final PreviewConfigProvider previewConfigProvider;

  // A temporary texture that holds the texture name and preview size.
  private Texture textureProxy;
  private Surface previewSurface;
  private PhotoCapturer photoCapturer;
  private GLSurfaceView captureView;
  private CaptureMode captureMode;
  private PreviewConfig previewConfig;
  private ProjectionMetadata projectionMetadata;

  public CameraCapture(
      Context context,
      ProjectionMetadataProvider metadataProvider,
      MotionCaptureSource motionCaptureSource,
      PreviewConfigProvider previewConfigProvider,
      CameraConfigurator cameraConfigurator) {
    this.context = context;
    this.previewConfigProvider = previewConfigProvider;

    // Create threads and handlers.
    cameraThread.start();
    cameraHandler = new Handler(cameraThread.getLooper());
    glThread.start();
    glHandler = new Handler(glThread.getLooper());

    // Create camera Preview and use GL thread for callbacks which involves GL operations.
    cameraPreview = new CameraPreview(context, cameraHandler, glHandler, cameraConfigurator);

    // Create OpenGL context.
    eglSurface = new EglSurface();
    eglContextFactory = new SharedEGLContextFactory(eglSurface);

    eglSurface.makeCurrent();
    // Create preview texture.
    texture = new Texture(1, 1, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
    surfaceTexture = new SurfaceTexture(texture.getName());
    surfaceTexture.setOnFrameAvailableListener(this, glHandler);
    // Create the preview renderer.
    previewRenderer = new PreviewRenderer(context, cameraPreview.getCameraOrientation());
    eglSurface.makeNonCurrent();

    this.metadataProvider = metadataProvider;
    this.motionCaptureSource = motionCaptureSource;
  }

  // Update PreviewConfig according to capture mode. Return whether the PreviewConfig is changed.
  public synchronized boolean updatePreviewConfig(CaptureMode captureMode) {
    PreviewConfig config =
        previewConfigProvider.getPreviewConfigForCaptureMode(
            cameraPreview.getCameraCharacteristics(), captureMode);
    ProjectionMetadata newProjectionMetadata = metadataProvider.getProjectionMetadata(captureMode);
    Preconditions.checkNotNull(config);
    Preconditions.checkNotNull(newProjectionMetadata);
    // Return false if both input and output are the same.
    if (!isCaptureConfigurationDifferent(config, newProjectionMetadata)) {
      return false;
    }

    this.captureMode = captureMode;
    this.previewConfig = config;
    projectionMetadata = newProjectionMetadata;

    // Disable reprojection if calibration is enabled.
    if (projectionMetadata.stereoReprojectionConfig != null && DebugConfig.isCalibrationEnabled()) {
      projectionMetadata = new ProjectionMetadata(projectionMetadata.stereoMode, null, null);
    }

    glHandler.post(() -> startPreview());
    return true;
  }

  public synchronized void setCaptureView(GLSurfaceView captureView) {
    if (this.captureView == captureView) {
      return;
    }
    this.captureView = captureView;
    Log.i(TAG, "setCaptureView " + captureView);

    processors.remove(previewRenderer);
    if (captureView != null) {
      // Configure the rendering.
      captureView.setEGLContextClientVersion(2);
      captureView.setPreserveEGLContextOnPause(true);
      captureView.setEGLContextFactory(eglContextFactory);
      previewRenderer.setGLSurfaceView(captureView);
      addCameraProcessor(previewRenderer);
    } else {
      previewRenderer.setGLSurfaceView(null);
    }
  }

  public CameraPreview getCameraPreview() {
    return cameraPreview;
  }

  /** Adds a processor to be run on each frame produced by the CameraPreview. */
  public synchronized void addCameraProcessor(CameraProcessor processor) {
    processors.add(processor);

    // Pass in the texture if it is already created.
    if (textureProxy != null) {
      eglSurface.makeCurrent();
      processor.onTextureCreated(textureProxy);
      eglSurface.makeNonCurrent();
    }
  }

  public synchronized void onResume() {
    Log.d(TAG, "onResume");
    if (previewConfig != null) {
      glHandler.post(() -> startPreview());
    }
  }

  public synchronized void onPause() {
    cameraPreview.release();

    // Destroy the photo capturer so a new one can be created when resuming.
    if (photoCapturer != null) {
      photoCapturer.close();
      motionCaptureSource.stop();
      photoCapturer = null;
    }
  }

  @Override
  public synchronized void onFrameAvailable(SurfaceTexture tex) {
    eglSurface.makeCurrent();
    // We need to lock the frame before we update the texture, since
    // the recorder might still using the data from the previous frame.
    for (CameraProcessor processor : processors) {
      processor.waitUntilReady();
    }
    surfaceTexture.updateTexImage();
    surfaceTexture.getTransformMatrix(textureMatrix);
    long timestampNs = surfaceTexture.getTimestamp();
    for (CameraProcessor processor : processors) {
      processor.onFrameAvailable(textureMatrix, timestampNs);
      // Restore OpenGL context, which may have changed.
      eglSurface.makeCurrent();
    }
    framerateReporter.addTimestamp(timestampNs);
  }

  private void updatePreviewSize() {
    // Update the texture according to preview size.
    eglSurface.makeCurrent();
    textureProxy =
        new Texture(
            previewConfig.previewWidth(),
            previewConfig.previewHeight(),
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            texture.getName());
    for (CameraProcessor processor : processors) {
      processor.onTextureCreated(textureProxy);
    }
    eglSurface.makeNonCurrent();

    // Update the output size and create the surface.
    Log.i(
        TAG, "Preview size " + previewConfig.previewWidth() + "x" + previewConfig.previewHeight());
    surfaceTexture.setDefaultBufferSize(
        previewConfig.previewWidth(), previewConfig.previewHeight());
    // Release and recreate the preview surface.
    if (previewSurface != null) {
      previewSurface.release();
    }
    previewSurface = new Surface(surfaceTexture);
  }

  // Release or create PhotoCapturer according to active mode.
  private void updatePhotoCapturer() {
    if (photoCapturer != null) {
      photoCapturer.close();
      if (photoCapturer instanceof MotionCaptureSource.MotionEventListener) {
        motionCaptureSource.removeMotionEventListener(
            (MotionCaptureSource.MotionEventListener) photoCapturer);
      }
      motionCaptureSource.stop();
    }

    photoCapturer = previewConfig.isPhotoMode() ? newPhotoCapturer() : null;
  }

  // TODO: Move to factory.
  private PhotoCapturer newPhotoCapturer() {
    if (DebugConfig.getPhotoCaptureImageFormat() != 0) {
      return new RawPhotoCapturer(
          context, previewConfig.photoWidth(), previewConfig.photoHeight(), cameraHandler);
    } else {
      eglSurface.makeCurrent();
      VRPhotoCapturer vrPhotoCapturer =
          new VRPhotoCapturer(
              context,
              previewConfig.photoWidth(),
              previewConfig.photoHeight(),
              captureMode.getConfiguredPhotoMode().getFrameSize().getFrameWidth(),
              captureMode.getConfiguredPhotoMode().getFrameSize().getFrameHeight(),
              cameraPreview.getCameraCharacteristics(),
              projectionMetadata.stereoReprojectionConfig,
              glHandler);
      eglSurface.makeNonCurrent();
      motionCaptureSource.addMotionEventListener(vrPhotoCapturer);
      motionCaptureSource.start();
      return vrPhotoCapturer;
    }
  }

  private synchronized void startPreview() {
    if (previewConfig == null) {
      return;
    }

    Log.d(TAG, "startPreview");
    updatePreviewSize();
    updatePhotoCapturer();
    previewRenderer.setStereoReprojectionConfig(projectionMetadata.stereoReprojectionConfig);

    // Open the camera if it is not.
    cameraPreview.open();

    // Prepare the list of surfaces that can be used for preview.
    List<Surface> sessionSurfaces = new ArrayList<>();
    sessionSurfaces.add(previewSurface);
    if (photoCapturer != null) {
      sessionSurfaces.addAll(photoCapturer.getTargetSurfaces());
    }
    // Start preview with just the previewSurface.
    List<Surface> activeSurfaces = new ArrayList<>();
    activeSurfaces.add(previewSurface);
    // Ignore preview CaptureResult for photo mode.
    CameraPreview.CaptureResultCallback callback = (photoCapturer != null ? null : this);
    cameraPreview.startPreview(sessionSurfaces, activeSurfaces, previewConfig, callback);
    framerateReporter.reset();
  }

  public synchronized boolean startPhotoCapture(String path) {
    if (photoCapturer == null || !photoCapturer.isReadyForCapture()) {
      Log.e(TAG, "PhotoCapturer is not ready");
      return false;
    }
    List<Surface> activeSurfaces = new ArrayList<>();
    activeSurfaces.addAll(photoCapturer.getTargetSurfaces());
    int id =
        cameraPreview.startPhotoCapture(
            activeSurfaces, previewConfig, result -> photoCapturer.onCaptureResult(result));
    if (id < 0) {
      Log.e(TAG, "Unable to startPhotoCapture");
      return false;
    }
    photoCapturer.addPhotoRequest(id, path);
    return true;
  }

  public synchronized boolean hasPendingCapture() {
    return photoCapturer != null && photoCapturer.hasPendingRequests();
  }

  @Override
  public synchronized void onCaptureResult(CaptureResult result) {
    for (CameraProcessor processor : processors) {
      processor.onCaptureResult(result);
    }
  }

  public ProjectionMetadata getProjectionMetadata() {
    return projectionMetadata;
  }

  // Checks if the capture configuration (input & output) has changed.
  public boolean isCaptureConfigurationDifferent(PreviewConfig config, ProjectionMetadata meta) {
    // Returns true if the input PreviewConfig changes.
    if (!config.equals(previewConfig)) {
      return true;
    }
    // Returns true if the new ProjectionMetadata has reprojection, but the current one does not.
    if (projectionMetadata == null || projectionMetadata.stereoReprojectionConfig == null) {
      return meta.stereoReprojectionConfig != null;
    }
    // Returns true if new mode does not do reprojection or has a different reprojection FOV.
    return (meta.stereoReprojectionConfig == null
        || !meta.stereoReprojectionConfig
            .getFov()
            .equals(projectionMetadata.stereoReprojectionConfig.getFov()));
  }
}
