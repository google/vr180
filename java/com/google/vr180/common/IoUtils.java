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

package com.google.vr180.common;

import android.content.Context;
import android.net.Uri;
import com.google.vr180.common.logging.Log;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * Utility methods for IO.
 */
public final class IoUtils {

  private static final String TAG = "IoUtils";

  /** Closes the given closeable. Exception is ignored. */
  public static void closeSilently(@Nullable Closeable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (IOException | NullPointerException e) {
      // ignore;
    }
  }

  /**
   * Synchronously gets the raw bytes of the contents of the given URI.
   *
   * @param context The Android Context we're working in.
   * @param uri The URI of the data we want to get.
   * @param canceled Cancels the method when set to true.
   * @return The data at the given URI, or null if it is canceled or there is an error.
   */
  @Nullable
  public static byte[] getDataAtUri(Context context, Uri uri, AtomicBoolean canceled) {
    if (canceled.get()) {
      return null;
    }

    InputStream inputStream = null;
    try {
      inputStream = context.getContentResolver().openInputStream(uri);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      // Read 8KB of data at a time.
      byte[] readBuffer = new byte[8192];

      int bytesRead;
      while ((bytesRead = inputStream.read(readBuffer)) > 0) {
        outputStream.write(readBuffer, 0, bytesRead);
        if (canceled.get()) {
          return null;
        }
      }

      return outputStream.toByteArray();

    } catch (IOException e) {
      Log.e(TAG, "Failed to get data for URI: " + uri + " : ", e);
      return null;
    } finally {
      closeSilently(inputStream);
    }
  }
}
