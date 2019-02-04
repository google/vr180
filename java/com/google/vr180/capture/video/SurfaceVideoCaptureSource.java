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

package com.google.vr180.capture.video;

import android.hardware.camera2.CaptureResult;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.os.Handler;
import android.view.Surface;
import com.google.common.base.Preconditions;
import com.google.vr180.capture.camera.CameraProcessor;
import com.google.vr180.capture.renderer.TextureRenderer;
import com.google.vr180.common.logging.Log;
import com.google.vr180.common.opengl.EglSurface;
import com.google.vr180.common.opengl.Texture;
import com.google.vr180.media.metadata.StereoReprojectionConfig;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import javax.annotation.Nullable;

/** A VideoCapture source that can renders from an input texture to an output surface */
public class SurfaceVideoCaptureSource implements VideoCaptureSource, CameraProcessor {
  private static final String TAG = "SurfaceVideoCaptureSource";
  private static final long BUFFER_LENGTH_NS = 1_000_000_000L; // 1 second in ns.

  private final float[] textureTransform = new float[16];
  private final int orientation;
  private Surface surface;
  private boolean active;
  private EglSurface eglSurface;
  private TextureRenderer renderer;
  private CaptureResultCallback captureResultCallback;
  private Handler captureResultCallbackHandler;
  private EGLContext sharedContext;
  private Texture texture;
  private int frameNumber = 0;
  private long uncopiedFrameTimestamp = 0;
  private final SortedMap<Long, CaptureResult> captureResultMap;
  private StereoReprojectionConfig stereoReprojectionConfig;

  public SurfaceVideoCaptureSource(int orientation) {
    this.orientation = orientation;
    this.captureResultMap = new ConcurrentSkipListMap<>();
  }

  @Override
  public void setTargetSurface(Surface targetSurface) {
    release();
    this.surface = targetSurface;
  }

  @Override
  public void setCaptureResultCallback(@Nullable CaptureResultCallback callback, Handler handler) {
    captureResultCallback = callback;
    captureResultCallbackHandler = handler;
  }

  @Override
  public boolean prepare() {
    Preconditions.checkNotNull(sharedContext);
    Preconditions.checkNotNull(surface);
    eglSurface = new EglSurface(sharedContext, surface, true);
    eglSurface.makeCurrent();
    renderer = new TextureRenderer(texture, stereoReprojectionConfig, orientation);
    renderer.warmup();
    eglSurface.makeNonCurrent();
    return true;
  }

  @Override
  public boolean start() {
    Log.d(TAG, "start");
    frameNumber = 0;
    active = true;
    return true;
  }

  @Override
  public boolean stop() {
    Log.d(TAG, "stop");
    active = false;
    return true;
  }

  @Override
  public boolean release() {
    if (renderer != null) {
      eglSurface.makeCurrent();
      renderer.release();
      eglSurface.makeNonCurrent();
      renderer = null;
    }
    if (eglSurface != null) {
      eglSurface.release();
      eglSurface = null;
    }
    return true;
  }

  @Override
  public void setErrorCallback(@Nullable ErrorCallback errorCallback, @Nullable Handler handler) {
    // Ignore for now.
  }

  @Override
  public void copyFrame(long timestampNs) {
    eglSurface.makeCurrent();
    renderer.render(textureTransform);
    eglSurface.setPresentationTime(timestampNs);
    eglSurface.swapBuffers();
    frameNumber = frameNumber + 1;
  }

  @Override
  public int getFrameNumber() {
    return frameNumber;
  }

  @Override
  public void onTextureCreated(Texture texture) {
    sharedContext = EGL14.eglGetCurrentContext();
    this.texture = texture;
  }

  // Called when the texture is updated. Note this is assumed to be running on the same thread as
  // onCaptureResult.
  @Override
  public void onFrameAvailable(float[] textureMatrix, final long timestampNs) {
    if (!active) {
      return;
    }
    System.arraycopy(textureMatrix, 0, textureTransform, 0, 16);
    if (captureResultMap.isEmpty() || timestampNs > captureResultMap.lastKey()) {
      // Texture data arrives before CaptureResult.
      uncopiedFrameTimestamp = timestampNs;
    } else {
      // CaptureResult is already available.
      copyFrame(getAdjustedTimestamp(timestampNs));
      uncopiedFrameTimestamp = 0;
    }
  }

  // Called when the metadata for a frame is available from camera. Note this is assumed to be
  // running on the same thread as onFrameAvailable.
  @Override
  public void onCaptureResult(CaptureResult result) {
    long timestampNs = result.get(CaptureResult.SENSOR_TIMESTAMP);
    // Save the capture result for processing.
    captureResultMap.put(timestampNs, result);
    // Delete old data whenever we double the buffer length.
    if (captureResultMap.firstKey() < timestampNs - (BUFFER_LENGTH_NS * 2)) {
      captureResultMap.headMap(timestampNs - BUFFER_LENGTH_NS).clear();
    }
    if (active && timestampNs == uncopiedFrameTimestamp) {
      copyFrame(getAdjustedTimestamp(timestampNs));
      uncopiedFrameTimestamp = 0;
    }
  }

  @Override
  public void waitUntilReady() {}

  // Get adjusted timestamp according to exposure and rolling sutter.
  private long getAdjustedTimestamp(long timestampNs) {
    CaptureResult result = processCaptureResult(timestampNs);
    if (result != null) {
      timestampNs +=
          (result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                  + result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW))
              / 2;
    } else {
      Log.w(TAG, "Unable to adjust timestamp for rolling shutter and exposure. @" + timestampNs);
    }
    return timestampNs;
  }

  // Find the CaptureResult for a given timestamp.
  private CaptureResult processCaptureResult(long timestampNs) {
    // Remove all capture results older than this one.
    captureResultMap.headMap(timestampNs).clear();
    try {
      // Process the current capture result.
      CaptureResult result = captureResultMap.remove(timestampNs);
      if (result != null) {
        final Handler handler = captureResultCallbackHandler;
        final CaptureResultCallback callback = captureResultCallback;
        if (callback != null) {
          if (handler != null) {
            handler.post(() -> callback.onCaptureResult(result));
          } else {
            callback.onCaptureResult(result);
          }
        }
      }
      return result;
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
    }
    return null;
  }

  public void setStereoReprojectionConfig(StereoReprojectionConfig config) {
    stereoReprojectionConfig = config;
  }
}
