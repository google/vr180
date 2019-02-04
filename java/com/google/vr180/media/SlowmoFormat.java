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

package com.google.vr180.media;

import android.media.MediaFormat;

/** Format utility functions for slow motion videos. */
public class SlowmoFormat {
  /** key for setting slow-down speed factor */
  private static final String KEY_SLOW_DOWN = "vr180-slow-down";

  /** Saves the slow-down factor in the MediaFormat */
  public static void setSpeedFactor(MediaFormat format, int speedFactor) {
    format.setInteger(KEY_SLOW_DOWN, speedFactor);
  }

  /** Gets the saved the slow-down factor from the MediaFormat */
  public static int getSpeedFactor(MediaFormat format) {
    return format.containsKey(KEY_SLOW_DOWN) ? format.getInteger(KEY_SLOW_DOWN) : 1;
  }

  /** Gets the adjusted audio format according to the slow-down factor */
  public static MediaFormat getSpeedAdjustedAudioFormat(MediaFormat format) {
    int speedFactor = getSpeedFactor(format);
    if (speedFactor > 1) {
      format.setInteger(
          MediaFormat.KEY_SAMPLE_RATE,
          format.getInteger(MediaFormat.KEY_SAMPLE_RATE) / speedFactor);
      format.setInteger(
          MediaFormat.KEY_BIT_RATE, format.getInteger(MediaFormat.KEY_BIT_RATE) / speedFactor);
    }
    return format;
  }
}
