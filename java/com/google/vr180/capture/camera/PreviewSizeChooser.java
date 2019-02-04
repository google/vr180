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

import android.util.Size;
import com.google.vr180.common.logging.Log;

/** Util class for picking a size form a list or an array of sizes. */
public class PreviewSizeChooser {
  private static final String TAG = "PreviewSizeChooser";
  private static final int MAX_PREVIEW_FRAME_PIXELS = 4032 * 3024;

  // Pick a previews size from an array of PreviewSize.
  public static Size choose(Size[] previewSizes) {
    if (previewSizes == null || previewSizes.length == 0) {
      return null;
    }

    // Pick the largest preview size below the max size.
    Size chosenSize = null;
    int previewSizeIndex = 0;
    for (int i = 0; i < previewSizes.length; ++i) {
      Size size = previewSizes[i];
      if (size.getWidth() * size.getHeight() > MAX_PREVIEW_FRAME_PIXELS) {
        // Skip the size if it is too big.
        Log.d(TAG, "Skip large preview size: " + size.getWidth() + "x" + size.getHeight());
        continue;
      }
      if (chosenSize != null && size.getWidth() < chosenSize.getWidth()) {
        // Do not pick a smaller size.
        continue;
      }
      if (chosenSize == null
          || size.getWidth() > chosenSize.getWidth()
          || size.getHeight() > chosenSize.getHeight()) {
        chosenSize = size;
        previewSizeIndex = i;
      }
    }

    chosenSize = previewSizes[previewSizeIndex];
    Log.d(TAG, "Preview size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
    return chosenSize;
  }
}
