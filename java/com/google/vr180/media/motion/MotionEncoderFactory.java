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

package com.google.vr180.media.motion;

import android.media.MediaFormat;
import android.os.Handler;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.MediaCreationUtils;
import com.google.vr180.media.muxer.MediaMux;

/** Factory for creating instances of {@link MotionEncoder}. */
public class MotionEncoderFactory {

  private static final String TAG = "MotionEncoderFactory";
  private static MotionEncoderFactory factory;

  public static MotionEncoderFactory getInstance() {
    if (factory == null) {
      factory = new MotionEncoderFactory();
    }
    return factory;
  }

  private MotionEncoderFactory() {}

  /**
   * Create a new instance and return {@code null} on error.
   *
   * @param format format for the motion with encoding parameters. Must not be null, and must
   * represent a motion format
   * @param mediaMux muxer into which output encoded data should be fed
   * @param handler handler for the thread to process buffer requests on
   */
  public MotionEncoder createEncoder(MediaFormat format, MediaMux mediaMux, Handler handler) {
    Preconditions.checkNotNull(format);
    Preconditions.checkNotNull(handler);
    if (!MediaCreationUtils.isMotionFormat(format)) {
      Log.e(TAG, "Not a motion format");
      return null;
    }
    try {
      return new MotionEncoder(format, mediaMux, handler);
    } catch (Exception e) {
      Log.e(TAG, "Could not create motion encoder", e);
      return null;
    }
  }
}
