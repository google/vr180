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

package com.google.vr180.common.media;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import com.google.vr180.common.logging.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Helper functions to deal with {@Bitmap} objects.
 *
 * 
 * 
 */
public class BitmapIO {
  private static final String TAG = "BitmapIO";
  private static final int PNG_DEFAULT_QUALITY = 100;

  /**
   * Encodes a {@code Bitmap} as a byte array. The image is encoded as a PNG or JPEG byte array.
   *
   * @param bitmap The {@code Bitmap} to encode
   * @param quality The quality of the image. If -1, PNG encoding is used. Otherwise, JPEG is used
   * and quality is expected in [0, 100].
   * @return the byte array
   */
  public static byte[] toByteArray(Bitmap bitmap, int quality) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    if (quality < 0) {
      bitmap.compress(CompressFormat.PNG, PNG_DEFAULT_QUALITY, stream);
    } else {
      bitmap.compress(CompressFormat.JPEG, quality, stream);
    }
    return stream.toByteArray();
  }

  /**
   * Encodes a {@code Bitmap} as a webp byte array.
   *
   * @param bitmap The {@code Bitmap} to encode
   * @param quality The quality of the image in [0, 100].
   * @return the byte array
   */
  public static byte[] toWebpByteArray(Bitmap bitmap, int quality) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    bitmap.compress(CompressFormat.WEBP, quality, stream);
    return stream.toByteArray();
  }

  /**
   * Saves a {@code Bitmap} to file. If the quality is -1, it will be saved as PNG. Otherwise it
   * will be saved as JPEG.
   *
   * @param image an image to be saved
   * @param quality the quality to encode the {@code Bitmap}
   * @param name file name
   * @return false if the image cannot be saved
   */
  public static boolean saveBitmap(Bitmap image, int quality, String name) {
    File file = new File(name);

    try {
      FileOutputStream fos = new FileOutputStream(file);
      if (quality < 0) {
        image.compress(Bitmap.CompressFormat.PNG, PNG_DEFAULT_QUALITY, fos);
      } else {
        image.compress(Bitmap.CompressFormat.JPEG, quality, fos);
      }
      try {
        fos.close();
      } catch (IOException e) {
        Log.e(TAG, "Failed to close file.", e);
      }
    } catch (FileNotFoundException e) {
      Log.e(TAG, "Failed to save bitmap.", e);
    }

    return true;
  }
}
