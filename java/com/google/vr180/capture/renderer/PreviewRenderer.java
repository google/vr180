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

package com.google.vr180.capture.renderer;

import android.content.Context;
import android.graphics.RectF;
import android.hardware.camera2.CaptureResult;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.WindowManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.vr180.capture.camera.CameraProcessor;
import com.google.vr180.common.logging.Log;
import com.google.vr180.common.media.StereoMode;
import com.google.vr180.common.opengl.Texture;
import com.google.vr180.media.metadata.StereoReprojectionConfig;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** A renderer for capture activity. */
public class PreviewRenderer implements CameraProcessor, GLSurfaceView.Renderer {
  private static final String TAG = "PreviewRenderer";
  // Constant used for converting display orientations reported by the OS to degrees.
  private static final int DISPLAY_ORIENTATION_TO_DEGREES = 90;
  private final int cameraOrientation;
  // Minimum delay between two consecutive rendering call for captureView.
  private final long minRenderDelayNs;
  private final ShaderRenderer shaderRenderer;

  private GLSurfaceView glSurfaceView = null;
  private Texture texture = null;
  private int viewportWidth = 1;
  private int viewportHeight = 1;
  private long lastRenderedFrameTimestamp = 0;
  private int displayOrientation = 0;

  public PreviewRenderer(Context context, int cameraOrientation) {
    this.cameraOrientation = cameraOrientation;
    // Make sure we do not render more than the display refresh rate, with some tolerance.
    float refreshRate = getDisplayRefreshRate(context);
    minRenderDelayNs = 900_000_000L / (long) refreshRate;
    // Assuming the OpenGL context is already made current.
    shaderRenderer = createShaderRenderer();
  }

  // Sets or resets the GLSurfaceView.
  public void setGLSurfaceView(GLSurfaceView glSurfaceView) {
    this.glSurfaceView = glSurfaceView;
    if (glSurfaceView != null) {
      glSurfaceView.setRenderer(this);
      glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }
  }

  @Override
  public void onTextureCreated(Texture texture) {
    shaderRenderer.setTexture(texture);
    this.texture = texture;
    updateTransform();
  }

  @Override
  public void onFrameAvailable(float[] textureMatrix, long timestampNs) {
    // Rendering the UI up to some frame rate.
    if (timestampNs > lastRenderedFrameTimestamp + minRenderDelayNs) {
      shaderRenderer.setTextureTransform(textureMatrix);
      glSurfaceView.requestRender();
      lastRenderedFrameTimestamp = timestampNs;
    }
  }

  @Override
  public void onCaptureResult(CaptureResult result) {
    // Ignore capture results for preview.
  }

  @Override
  public void waitUntilReady() {}

  /** Implements GLSurfaceView.Renderer.onSurfaceCreated */
  @Override
  public void onSurfaceCreated(GL10 unused, EGLConfig config) {}

  /** Implements GLSurfaceView.Renderer.onSurfaceChanged */
  @Override
  public void onSurfaceChanged(GL10 unused, int width, int height) {
    Log.d(TAG, "onSurfaceChanged " + width + " " + height);
    viewportWidth = width;
    viewportHeight = height;
    updateTransform();
  }

  /** Implements GLSurfaceView.Renderer.onDrawFrame */
  @Override
  public void onDrawFrame(GL10 unused) {
    GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    if (texture != null) {
      shaderRenderer.draw();
    }
  }

  @VisibleForTesting
  ShaderRenderer createShaderRenderer() {
    Log.d(TAG, "Camera orientation: " + cameraOrientation);
    return new ShaderRenderer(cameraOrientation);
  }

  private void updateTransform() {
    if (viewportWidth <= 0 || viewportHeight <= 0 || texture == null) {
      return;
    }

    WindowManager window =
        (WindowManager) glSurfaceView.getContext().getSystemService(Context.WINDOW_SERVICE);
    int deviceOrientation =
        window.getDefaultDisplay().getRotation() * DISPLAY_ORIENTATION_TO_DEGREES;
    Log.d(TAG, "Device orientation: " + deviceOrientation);

    // Relative orientation from camera to display.
    displayOrientation = (deviceOrientation - cameraOrientation + 360) % 360;
    Log.d(TAG, "Display orientation: " + displayOrientation);

    // Compute transforms for the fullscreen (initial) mode.
    float[] vertexTransform = new float[16];
    computeImageTransform(
        viewportWidth,
        viewportHeight,
        texture.getWidth(),
        texture.getHeight(),
        displayOrientation,
        vertexTransform);
    Matrix.rotateM(vertexTransform, 0, displayOrientation, 0, 0, 1.0f);
    shaderRenderer.setVertexTransform(vertexTransform);
  }

  /**
   * Given the OpenGL view port size, sets the bounding rectangle in the canonical screen space
   * ([-1,1]*[-1,1]) over which the preview image will be rendered.
   */
  @VisibleForTesting
  static void computeImageTransform(
      int viewportWidth,
      int viewportHeight,
      int previewWidth,
      int previewHeight,
      int displayOrientation,
      float[] imageTransform) {
    RectF fullViewport =
        computeFullViewport(
            viewportWidth, viewportHeight, previewWidth, previewHeight, displayOrientation);
    float left = fullViewport.left * 2 / viewportWidth - 1;
    float right = fullViewport.right * 2 / viewportWidth - 1;
    // OpenGL has the y-axis inverted w.r.t. the camera.
    float bottom = fullViewport.top * 2 / viewportHeight - 1;
    float top = fullViewport.bottom * 2 / viewportHeight - 1;
    setTransformFromCorners(left, right, bottom, top, imageTransform);
  }

  // Computes a vertex transform for {@link ShaderRenderer} based on the positions
  // of the corners of a rectangle within the [-1, 1] x [-1, 1] screen
  // coordinates.
  private static void setTransformFromCorners(
      float left, float right, float bottom, float top, float[] transform) {
    transform[0] = (right - left) / 2f;
    transform[1] = 0f;
    transform[2] = 0f;
    transform[3] = 0f;
    transform[4] = 0f;
    transform[5] = (top - bottom) / 2f;
    transform[6] = 0f;
    transform[7] = 0f;
    transform[8] = 0f;
    transform[9] = 0f;
    transform[10] = 1f;
    transform[11] = 0f;
    transform[12] = left + (right - left) * (1 - 0.5f);
    transform[13] = bottom + (top - bottom) * (1 - 0.5f);
    transform[14] = 0f;
    transform[15] = 1f;
  }

  /**
   * Computes the "ideal" viewport that fully contains the real viewport and has the same aspect
   * ratio as the camera preview.
   */
  static RectF computeFullViewport(
      int viewportWidth,
      int viewportHeight,
      int previewWidth,
      int previewHeight,
      int displayOrientation) {
    int rotatedPreviewWidth = previewWidth;
    int rotatedPreviewHeight = previewHeight;
    if (displayOrientation % 180 != 0) {
      rotatedPreviewWidth = previewHeight;
      rotatedPreviewHeight = previewWidth;
    }

    float scalingX = viewportWidth / (float) rotatedPreviewWidth;
    float scalingY = viewportHeight / (float) rotatedPreviewHeight;

    // Choose the scaling factor to make sure we see everything both horizontally and vertically.
    float scaling = Math.min(scalingX, scalingY);

    float previewWidthInViewport = rotatedPreviewWidth * scaling;
    float previewHeightInViewport = rotatedPreviewHeight * scaling;
    return new RectF(
        (viewportWidth - previewWidthInViewport) / 2,
        (viewportHeight - previewHeightInViewport) / 2,
        (viewportWidth + previewWidthInViewport) / 2,
        (viewportHeight + previewHeightInViewport) / 2);
  }

  public void setStereoReprojectionConfig(StereoReprojectionConfig config) {
    if (config != null && config.getStereoMode() != StereoMode.MONO && texture != null) {
      // Current implementation does not draw reprojection correctly for the following cases:
      // Note that this check ignores dynamic orientation changes.
      if (displayOrientation != 0
          || (config.getStereoMode() == StereoMode.LEFT_RIGHT
              && viewportWidth * texture.getHeight() > texture.getWidth() * viewportHeight)
          || (config.getStereoMode() == StereoMode.TOP_BOTTOM
              && viewportWidth * texture.getHeight() < texture.getWidth() * viewportHeight)) {
        Log.e(TAG, "PreviewRenderer does not support current display mode");
        shaderRenderer.setStereoReprojectionConfig(null);
        return;
      }
    }
    shaderRenderer.setStereoReprojectionConfig(config);
  }

  private static float getDisplayRefreshRate(Context context) {
    return
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
            .getDefaultDisplay()
            .getRefreshRate();
  }
}
