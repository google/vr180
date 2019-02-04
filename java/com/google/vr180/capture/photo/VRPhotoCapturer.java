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

package com.google.vr180.capture.photo;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.SizeF;
import android.view.Surface;
import com.google.vr180.capture.motion.MotionCaptureSource.MotionEventListener;
import com.google.vr180.capture.renderer.TextureRenderer;
import com.google.vr180.common.logging.Log;
import com.google.vr180.common.media.StereoMode;
import com.google.vr180.common.opengl.EglSurface;
import com.google.vr180.common.opengl.Texture;
import com.google.vr180.media.metadata.StereoReprojectionConfig;
import com.google.vr180.media.motion.MotionEvent;
import com.google.vr180.media.photo.ExifWriter;
import com.google.vr180.media.photo.PhotoWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;

/** Handles image processing, reprojection, and formatting for VR photos. */
public class VRPhotoCapturer
    implements PhotoCapturer,
        SurfaceTexture.OnFrameAvailableListener,
    MotionEventListener {

  private static final String TAG = "VRPhotoCapturer";
  private static final long BUFFER_LENGTH_NS = 1_000_000_000L; // 1 second in ns.

  private final Context context;
  private final ImageReader imageReader;
  private final Texture texture;
  private final SurfaceTexture surfaceTexture;
  private final Surface surface;
  private final EglSurface eglSurface;
  private final TextureRenderer renderer;
  private final float[] textureMatrix = new float[16];
  private final StereoReprojectionConfig stereoReprojectionConfig;
  private final PhotoCaptureQueue queue;
  private final ExifWriter exifWriter;
  private final ConcurrentSkipListMap<Long, float[]> orientationMap;

  public VRPhotoCapturer(
      Context context,
      int inputWidth,
      int inputHeight,
      int outputWidth,
      int outputHeight,
      CameraCharacteristics cameraCharacteristics,
      @Nullable StereoReprojectionConfig stereoReprojectionConfig,
      Handler glHandler) {
    this.context = context;
    queue = new PhotoCaptureQueue(this);
    exifWriter = new ExifWriter(cameraCharacteristics);
    this.stereoReprojectionConfig = stereoReprojectionConfig;
    orientationMap = new ConcurrentSkipListMap<>();

    // If dewarp is disabled, the output size is the same as input.
    if (stereoReprojectionConfig == null) {
      outputWidth = inputWidth;
      outputHeight = inputHeight;
    }

    imageReader =
        ImageReader.newInstance(outputWidth, outputHeight, PixelFormat.RGBA_8888, queue.size());
    imageReader.setOnImageAvailableListener(queue, glHandler);
    eglSurface = new EglSurface(EGL14.eglGetCurrentContext(), imageReader.getSurface(), true);
    eglSurface.makeCurrent();
    // Raw camera snapshots are drawn to a Texture and passed to the renderer via a SurfaceTexture.
    texture = new Texture(inputWidth, inputHeight, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
    surfaceTexture = new SurfaceTexture(texture.getName());
    surfaceTexture.setDefaultBufferSize(inputWidth, inputHeight);
    surfaceTexture.setOnFrameAvailableListener(this, glHandler);
    surface = new Surface(surfaceTexture);
    // The renderer dewarps the camera images and writes to the ImageReader via the EglSurface.
    renderer =
        new TextureRenderer(
            texture,
            stereoReprojectionConfig,
            cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
    renderer.warmup();
    eglSurface.makeNonCurrent();
  }

  @Override
  public int getWidth() {
    return texture.getWidth();
  }

  @Override
  public int getHeight() {
    return texture.getHeight();
  }

  @Override
  public List<Surface> getTargetSurfaces() {
    List<Surface> surfaces = new ArrayList<>();
    surfaces.add(surface);
    return surfaces;
  }

  @Override
  public void close() {
    imageReader.close();
    surfaceTexture.release();
    surface.release();

    eglSurface.makeCurrent();
    texture.delete();
    renderer.release();
    eglSurface.makeNonCurrent();
    eglSurface.release();

    queue.close();
  }

  @Override
  public boolean isReadyForCapture() {
    return !queue.isFull();
  }

  @Override
  public boolean hasPendingRequests() {
    return queue.hasPendingRequests();
  }

  @Override
  public void addPhotoRequest(int id, String path) {
    queue.addPhotoRequest(id, path);
  }

  @Override
  public void onCaptureResult(CaptureResult result) {
    exifWriter.onCaptureResult(result);
  }

  @Override
  public void processPhoto(Image image, int id, String path) {
    int width = image.getWidth();
    int height = image.getHeight();
    long timestamp = image.getTimestamp();

    Image.Plane plane = image.getPlanes()[0];
    int stride = plane.getRowStride();
    byte[] data = new byte[stride * height];
    plane.getBuffer().get(data);
    image.close();
    SizeF fov =
        stereoReprojectionConfig == null ? new SizeF(0, 0) : stereoReprojectionConfig.getFov();
    int stereoMode =
        stereoReprojectionConfig == null
            ? StereoMode.MONO
            : stereoReprojectionConfig.getStereoMode();
    if (PhotoWriter.nativeWriteVRPhotoToFile(
        data,
        width,
        height,
        stride,
        fov.getWidth(),
        fov.getHeight(),
        getOrientation(timestamp),
        stereoMode,
        path)) {
      int monoWidth = width / (stereoMode == StereoMode.LEFT_RIGHT ? 2 : 1);
      int monoHeight = height / (stereoMode == StereoMode.TOP_BOTTOM ? 2 : 1);
      exifWriter.onPhotoResult(path, monoWidth, monoHeight, timestamp);
      // Trigger media scanner to update database.
      MediaScannerConnection.scanFile(context, new String[] {path}, null, null);
    } else {
      Log.e(TAG, "Failed to capture photo: " + path);
    }
  }

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    eglSurface.makeCurrent();
    surfaceTexture.updateTexImage();
    surfaceTexture.getTransformMatrix(textureMatrix);
    GLES20.glViewport(0, 0, imageReader.getWidth(), imageReader.getHeight());
    renderer.render(textureMatrix);
    eglSurface.setPresentationTime(surfaceTexture.getTimestamp());
    eglSurface.swapBuffers();
  }

  @Override
  public void onMotionEvent(MotionEvent event) {
    if (event.type != MotionEvent.CammType.ORIENTATION) {
      return;
    }
    // Save the orientation for processing when we receive a photo.
    orientationMap.put(event.timestamp, event.values);
    // Delete old data whenever we double the buffer length and we have no pending photo requests.
    if (!hasPendingRequests()
        && orientationMap.firstKey() < event.timestamp - (BUFFER_LENGTH_NS * 2)) {
      orientationMap.headMap(event.timestamp - BUFFER_LENGTH_NS).clear();
    }
  }

  private float[] getOrientation(long timestamp) {
    Entry<Long, float[]> floorEntry = orientationMap.floorEntry(timestamp);
    if (floorEntry != null) {
      return floorEntry.getValue();
    } else {
      Log.e(TAG, "No photo orientation for timestamp");
      return new float[] {0, 0, 0};
    }
  }
}
