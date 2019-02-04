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

import com.google.vr180.common.logging.Log;
import java.text.ParseException;

/**
 * Helper method to extract the stereo mode from a st3d box.
 *
 * <p>This implementation is designed to be standalone to avoid a dependency on Exoplayer.
 */
public class St3dBoxParser {
  private static final String TAG = "St3dBoxParser";

  // The st3d box contains 4 bytes size, 4 bytes fourcc, 4 bytes version/flags, and 1 byte stereo
  // mode.
  private static final int ST3D_LENGTH = 13;

  // The version comes after the size (4 bytes) and the fourcc (4 bytes).
  private static final int VERSION_OFFSET = 8;

  // The stereo mode comes after size, fourcc, and version flags (each 4 bytes)
  private static final int STEREO_MODE_OFFSET = 12;

  public static int parseStereoMode(byte[] st3dBox) throws ParseException {
    if (st3dBox.length < ST3D_LENGTH) {
      Log.e(TAG, "St3d box was too small " + st3dBox.length);
      throw new ParseException("Box too small", st3dBox.length);
    }

    if (st3dBox[VERSION_OFFSET] != 0) {
      Log.e(TAG, "Unsupported st3d version " + st3dBox[VERSION_OFFSET]);
      throw new ParseException("Unsupported version", VERSION_OFFSET);
    }

    int stereoMode = st3dBox[STEREO_MODE_OFFSET];
    switch (stereoMode) {
      case 0:
        return StereoMode.MONO;
      case 1:
        return StereoMode.TOP_BOTTOM;
      case 2:
        return StereoMode.LEFT_RIGHT;
      case 3:
        return StereoMode.STEREO_CUSTOM;
      default:
        throw new ParseException("Unknown stereo  mode", STEREO_MODE_OFFSET);
    }
  }
}
