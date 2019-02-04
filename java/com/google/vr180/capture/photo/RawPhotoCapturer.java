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
import android.graphics.ImageFormat;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Handler;
import android.view.Surface;
import com.google.vr180.common.logging.Log;
import com.google.vr180.device.DebugConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

/** Handles raw photo capture. */
public class RawPhotoCapturer implements PhotoCapturer {
  private static final String TAG = "RawPhotoCapturer";

  private final Context context;
  private final ImageReader imageReader;
  private final ImageReader jpegReader;
  private final PhotoCaptureQueue queue;

  public RawPhotoCapturer(Context context, int width, int height, Handler cameraHandler) {
    this.context = context;
    int format = getImageFormat();
    Log.d(TAG, "format: " + format);

    queue = new PhotoCaptureQueue(this);
    imageReader = ImageReader.newInstance(width, height, format, queue.size());
    imageReader.setOnImageAvailableListener(queue, cameraHandler);

    // RAW capture needs a JPEG target surface to work.
    if (isRaw(format)) {
      jpegReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, queue.size());
      jpegReader.setOnImageAvailableListener(queue, cameraHandler);
    } else {
      jpegReader = null;
    }
  }

  @Override
  public int getWidth() {
    return imageReader.getWidth();
  }

  @Override
  public int getHeight() {
    return imageReader.getHeight();
  }

  @Override
  public List<Surface> getTargetSurfaces() {
    List<Surface> surfaces = new ArrayList<>();
    surfaces.add(imageReader.getSurface());
    if (jpegReader != null) {
      surfaces.add(jpegReader.getSurface());
    }
    return surfaces;
  }

  @Override
  public void close() {
    imageReader.close();
    if (jpegReader != null) {
      jpegReader.close();
    }
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
  public synchronized void addPhotoRequest(int id, String path) {
    queue.addPhotoRequest(id, path);
    if (jpegReader != null) {
      // Add a second request for JPEG.
      queue.addPhotoRequest(id, path);
    }
  }

  @Override
  public void onCaptureResult(CaptureResult result) {
    Log.i(
        TAG,
        ("CaptureResult #" + result.getSequenceId())
            + ("; exp = " + result.get(CaptureResult.SENSOR_EXPOSURE_TIME))
            + ("; gain = " + result.get(CaptureResult.SENSOR_SENSITIVITY))
            + ("; rs = " + result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)));
  }

  @Override
  public void processPhoto(Image image, int id, String path) {
    if (isRaw(image.getFormat())) {
      path = path.replaceFirst("vr.jpg", "raw");
    }
    try {
      FileOutputStream output = new FileOutputStream(new File(path));
      WritableByteChannel channel = Channels.newChannel(output);
      for (Image.Plane plane : image.getPlanes()) {
        channel.write(plane.getBuffer());
      }
      image.close();
      output.close();
      // Trigger media scanner to update database.
      MediaScannerConnection.scanFile(context, new String[] {path}, null, null);
    } catch (Exception e) {
      Log.e(TAG, "Exception getting image: ", e);
      image.close();
    }
  }

  public static boolean handlesFormat(int format) {
    return format == ImageFormat.JPEG || format == ImageFormat.RAW10 || format == ImageFormat.RAW12;
  }

  private static boolean isRaw(int format) {
    return format == ImageFormat.RAW10 || format == ImageFormat.RAW12;
  }

  private static int getImageFormat() {
    int format = DebugConfig.getPhotoCaptureImageFormat();
    if (handlesFormat(format)) {
      return format;
    }
    return ImageFormat.JPEG;
  }
}
