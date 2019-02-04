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

import android.content.Context;
import android.hardware.camera2.CaptureResult;
import android.os.Handler;
import com.google.vr180.api.camerainterfaces.ViewfinderCaptureSource;
import com.google.vr180.capture.camera.CameraProcessor;
import com.google.vr180.common.media.StereoMode;
import com.google.vr180.common.opengl.Texture;
import java.util.Arrays;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.TextureBufferImpl;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoFrame.TextureBuffer;
import org.webrtc.YuvConverter;

/**
 * An implementation of {@link VideoCapturer} that links a {@link CameraProcessor} texture to a
 * {@link org.webrtc.CapturerObserver} while converting, scaling, and throttling gltexture updates.
 */
public class SurfaceViewfinderCaptureSource
    implements VideoCapturer, ViewfinderCaptureSource, CameraProcessor {
  private static final String TAG = "SurfaceViewfinderCaptureSource";
  private static final int NS_PER_SERCOND = 1_000_000_000;

  private org.webrtc.CapturerObserver capturerObserver;
  private Texture texture;
  private Handler yuvHandler;
  private int width;
  private int height;
  private float[] textureMatrix;
  private int nsPerFrame;
  private long lastTimestampNs;
  private TextureBuffer textureBuffer;
  private int stereoMode = StereoMode.MONO;
  private volatile boolean isActive;

  public void setStereoMode(int stereoMode) {
    this.stereoMode = stereoMode;
    maybeUpdateTextureBuffer();
  }

  @Override
  public VideoCapturer getVideoCapturer() {
    return this;
  }

  @Override
  public synchronized void initialize(
      SurfaceTextureHelper surfaceTextureHelper,
      Context applicationContext,
      org.webrtc.CapturerObserver capturerObserver) {
    this.capturerObserver = capturerObserver;
  }

  @Override
  public synchronized void startCapture(int width, int height, int fps) {
    if (!isActive) {
      isActive = true;
      changeCaptureFormat(width, height, fps);
      if (capturerObserver != null) {
        capturerObserver.onCapturerStarted(true);
      }
    }
  }

  @Override
  public synchronized void stopCapture() {
    if (isActive) {
      isActive = false;
      if (capturerObserver != null) {
        capturerObserver.onCapturerStopped();
      }
    }
  }

  @Override
  public synchronized void changeCaptureFormat(int width, int height, int fps) {
    this.width = width;
    this.height = height;
    this.nsPerFrame = NS_PER_SERCOND / fps;
    maybeUpdateTextureBuffer();
  }

  @Override
  public synchronized void dispose() {
    if (!isActive) {
      capturerObserver = null;
      if (textureBuffer != null) {
        textureBuffer.release();
        textureBuffer = null;
      }
      textureMatrix = null;
      width = 0;
      height = 0;
    }
  }

  @Override
  public boolean isScreencast() {
    return false;
  }

  @Override
  public synchronized void onTextureCreated(Texture texture) {
    this.texture = texture;
    yuvHandler = new Handler();
    maybeUpdateTextureBuffer();
  }

  @Override
  public synchronized void onFrameAvailable(float[] textureMatrix, long timestampNs) {
    if (!isActive || capturerObserver == null || timestampNs - lastTimestampNs < nsPerFrame) {
      return;
    }

    if (!Arrays.equals(textureMatrix, this.textureMatrix)) {
      this.textureMatrix = textureMatrix;
      maybeUpdateTextureBuffer();
    }

    if (textureBuffer == null) {
      return;
    }

    // Process and release frame.
    VideoFrame frame = new VideoFrame(textureBuffer.toI420(), /* rotation= */ 0, timestampNs);
    capturerObserver.onFrameCaptured(frame);
    frame.release();
    lastTimestampNs = timestampNs;
  }

  @Override
  public void onCaptureResult(CaptureResult result) {}

  @Override
  public void waitUntilReady() {}

  private void maybeUpdateTextureBuffer() {
    if (texture == null
        || yuvHandler == null
        || width == 0
        || height == 0
        || textureMatrix == null) {
      return;
    }
    if (textureBuffer != null) {
      textureBuffer.release();
    }
    YuvConverter yuvConverter =
        ThreadUtils.invokeAtFrontUninterruptibly(yuvHandler, () -> new YuvConverter());
    TextureBuffer buffer =
        new TextureBufferImpl(
            texture.getWidth(),
            texture.getHeight(),
            TextureBuffer.Type.OES,
            texture.getName(),
            RendererCommon.convertMatrixToAndroidGraphicsMatrix(textureMatrix),
            yuvHandler,
            yuvConverter,
            () ->
                ThreadUtils.invokeAtFrontUninterruptibly(yuvHandler, () -> yuvConverter.release()));

    // Crop to first eye if we have a stereo image.
    int cropWidth = texture.getWidth();
    int cropHeight = texture.getHeight();
    if (stereoMode == StereoMode.LEFT_RIGHT) {
      cropWidth /= 2;
    } else if (stereoMode == StereoMode.TOP_BOTTOM) {
      cropHeight /= 2;
    }
    textureBuffer = (TextureBuffer) buffer.cropAndScale(0, 0, cropWidth, cropHeight, width, height);
    buffer.release();
  }
}
