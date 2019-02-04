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

package com.google.vr180.media.photo;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.ExifInterface;
import android.os.Build;
import android.util.Rational;
import com.google.vr180.common.logging.Log;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentSkipListMap;

/** A class that handles writing exif data to photos. */
public class ExifWriter {

  private static final String TAG = "ExifWriter";
  private static final int RATIONAL_PRECISION = 1000;

  /** A simple class for storing photo metadata */
  private static class PhotoMetadata {
    public final String path;
    public final int width;
    public final int height;
    public final long timestamp;

    PhotoMetadata(String path, int width, int height, long timestamp) {
      this.path = path;
      this.width = width;
      this.height = height;
      this.timestamp = timestamp;
    }
  }

  private final ConcurrentSkipListMap<Long, CaptureResult> captureResultMap;
  private final ConcurrentSkipListMap<Long, PhotoMetadata> photoMetadataMap;
  private final CameraCharacteristics cameraCharacteristics;

  public ExifWriter(CameraCharacteristics cameraCharacteristics) {
    this.cameraCharacteristics = cameraCharacteristics;
    captureResultMap = new ConcurrentSkipListMap<>();
    photoMetadataMap = new ConcurrentSkipListMap<>();
  }

  public synchronized void onCaptureResult(CaptureResult result) {
    long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
    PhotoMetadata metadata = photoMetadataMap.remove(timestamp);
    if (metadata == null) {
      captureResultMap.put(timestamp, result);
    } else {
      saveExifData(result, metadata);
    }
  }

  public synchronized void onPhotoResult(String path, int width, int height, long timestamp) {
    PhotoMetadata metadata = new PhotoMetadata(path, width, height, timestamp);
    CaptureResult result = captureResultMap.remove(timestamp);
    if (result == null) {
      photoMetadataMap.put(timestamp, metadata);
    } else {
      saveExifData(result, metadata);
    }
  }

  private Rational toRational(float value, int precision) {
    return new Rational((int) (value * precision), precision);
  }

  private void saveExifData(CaptureResult result, PhotoMetadata metadata) {
    // Clear old requests.
    captureResultMap.headMap(metadata.timestamp).clear();
    photoMetadataMap.headMap(metadata.timestamp).clear();
    try {
      ExifInterface exif = new ExifInterface(metadata.path);
      exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
      exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL);
      Date now = new Date();
      String datetime = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").format(now);
      exif.setAttribute(ExifInterface.TAG_DATETIME, datetime);
      exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, datetime);
      exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, datetime);
      String width = String.valueOf(metadata.width);
      exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width);
      String height = String.valueOf(metadata.height);
      exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height);
      exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, width);
      exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, height);
      exif.setAttribute(
          ExifInterface.TAG_FLASH, String.valueOf(result.get(CaptureResult.FLASH_MODE)));
      float aperture =
          cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)[0];
      String rationalApature = toRational(aperture, RATIONAL_PRECISION).toString();
      exif.setAttribute(ExifInterface.TAG_F_NUMBER, String.valueOf(aperture));
      exif.setAttribute(ExifInterface.TAG_APERTURE_VALUE, rationalApature);
      exif.setAttribute(
          ExifInterface.TAG_FOCAL_LENGTH,
          toRational(result.get(CaptureResult.LENS_FOCAL_LENGTH), RATIONAL_PRECISION).toString());
      exif.setAttribute(
          ExifInterface.TAG_EXPOSURE_TIME,
          String.valueOf(result.get(CaptureResult.SENSOR_EXPOSURE_TIME).doubleValue() / 1e9));
      int exposureMode =
          result.get(CaptureResult.CONTROL_AE_MODE) == CaptureResult.CONTROL_AE_MODE_OFF ? 1 : 0;
      exif.setAttribute(ExifInterface.TAG_EXPOSURE_MODE, String.valueOf(exposureMode));
      exif.setAttribute(
          ExifInterface.TAG_ISO_SPEED_RATINGS,
          String.valueOf(result.get(CaptureResult.SENSOR_SENSITIVITY)));
      int whiteBalanceMode =
          result.get(CaptureResult.CONTROL_AWB_MODE) == CaptureResult.CONTROL_AWB_MODE_OFF
              ? ExifInterface.WHITEBALANCE_MANUAL
              : ExifInterface.WHITEBALANCE_AUTO;
      exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, String.valueOf(whiteBalanceMode));
      exif.saveAttributes();
    } catch (IOException e) {
      Log.e(TAG, "Exception saving exif data: ", e);
    }
  }
}
