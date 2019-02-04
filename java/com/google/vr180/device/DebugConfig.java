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

package com.google.vr180.device;

import android.graphics.Rect;
import android.util.Size;
import com.google.vr180.common.SystemUtil;
import com.google.vr180.common.logging.Log;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Class for accessing debug configurations of the app. */
public class DebugConfig {
  private static final String TAG = "DebugConfig";
  // Format: width height
  private static final String PREVIEW_SIZE_RROP = "debug.vr180.preview.size";
  // Format: left top right bottom
  private static final String PREVIEW_CROP_PROP = "debug.vr180.preview.crop";
  // Format: fps
  private static final String PREVIEW_FPS_PROP = "debug.vr180.preview.fps";
  // Format: ImageFormat enum jpeg(256), raw10(37). Defalt to yuv.
  private static final String PHOTO_CAPTURE_IMAGE_FORMAT_PROP = "debug.vr180.photo.format";
  // Format: ms
  private static final String SENSOR_EXPOSURE_TIME_MS_PROP = "debug.vr180.sensor.exposure_time_ms";
  // Format: iso
  private static final String SENSOR_SENSITIVITY_PROP = "debug.vr180.sensor.sensitivity";
  // Format: 1(true) / 0(false)
  private static final String CALIBRATION_PROP = "debug.vr180.calibration";
  private static final String DISABLE_NOISE_REDUCTION_PROP = "debug.vr180.disable_noise_reduction";

  private static final String DISABLE_PHOTO_DEWARP_PROP = "debug.vr180.photo.disable_dewarp";
  private static final String ENABLE_EXTRA_CAMM_DATA_PROP =
      "debug.vr180.camm.enable_extra_camm_data";

  public static Size getPreviewSize() {
    return readSizeProperty(PREVIEW_SIZE_RROP);
  }

  public static Rect getPreviewCrop() {
    return readRectProperty(PREVIEW_CROP_PROP);
  }

  public static int getPreviewFps() {
    return readIntProperty(PREVIEW_FPS_PROP);
  }

  public static int getSensorExposureTimeMs() {
    return readIntProperty(SENSOR_EXPOSURE_TIME_MS_PROP);
  }

  public static int getSensorSensitivity() {
    return readIntProperty(SENSOR_SENSITIVITY_PROP);
  }

  public static boolean isNoiseReductionDisabled() {
    return readIntProperty(DISABLE_NOISE_REDUCTION_PROP) != 0;
  }

  public static int getPhotoCaptureImageFormat() {
    return readIntProperty(PHOTO_CAPTURE_IMAGE_FORMAT_PROP);
  }

  public static boolean isPhotoDewarpEnabled() {
    return readIntProperty(DISABLE_PHOTO_DEWARP_PROP) != 1;
  }

  public static boolean isCalibrationEnabled() {
    return readIntProperty(CALIBRATION_PROP) != 0;
  }

  public static boolean isExtraCammDataEnabled() {
    return readIntProperty(ENABLE_EXTRA_CAMM_DATA_PROP) != 0;
  }

  // Read a property as an integer.
  public static int readIntProperty(String property) {
    int[] numbers = readIntArrayProperty(property, 1);
    return numbers != null ? numbers[0] : 0;
  }

  // Read a property as a Size object.
  private static Size readSizeProperty(String property) {
    int[] numbers = readIntArrayProperty(property, 2);
    if (numbers == null) {
      return null;
    }
    return new Size(numbers[0], numbers[1]);
  }

  // Read a property as a Rect object.
  private static Rect readRectProperty(String property) {
    int[] numbers = readIntArrayProperty(property, 4);
    if (numbers == null) {
      return null;
    }
    return new Rect(numbers[0], numbers[1], numbers[2], numbers[3]);
  }

  // Read a properaty and parse it to an integer array.
  private static int[] readIntArrayProperty(String property, int count) {
    String value = SystemUtil.getProperty(property);
    Log.i(TAG, property + " = " + value);
    if (value == null) {
      return null;
    }
    Scanner scanner = new Scanner(value);
    int[] result = new int[count];
    try {
      for (int i = 0; i < count; ++i) {
        if (!scanner.hasNextInt()) {
          return null;
        }
        result[i] = scanner.nextInt();
      }
      scanner.close();
    } catch (IllegalStateException | NoSuchElementException e) {
      return null;
    }
    return result;
  }
}
