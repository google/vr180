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

package com.google.vr180.media.video;

import android.content.Context;
import android.media.MediaFormat;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.MediaCreationUtils;
import com.google.vr180.media.muxer.MediaMux;

/** Factory for creating instances of {@link VideoEncoder}. */
public class VideoEncoderFactory {
  private static final String TAG = "VideoEncoderFactory";

  // Enumeration of possible bitrate control types (from openmax/OMX_Video.h)
  // All values included for completeness and future proofing and tagged to ignore warnings.
  @SuppressWarnings("unused")
  private static final int OMX_VIDEO_CONTROL_RATE_DISABLE = 0;

  @SuppressWarnings("unused")
  private static final int OMX_VIDEO_CONTROL_RATE_VARIABLE = 1;

  @SuppressWarnings("unused")
  private static final int OMX_VIDEO_CONTROL_RATE_CONSTANT = 2;

  private static VideoEncoderFactory factory;

  public static VideoEncoderFactory getInstance() {
    if (factory == null) {
      factory = new VideoEncoderFactory();
    }
    return factory;
  }

  private VideoEncoderFactory() {}

  /**
   * Create a new instance and return {@code null} on error.
   *
   * @param format format for the video with encoding parameters and orientation. Must not be null,
   *     and must represent a video format.
   * @param mediaMux The muxer into which output encoded data should be fed.
   */
  public VideoEncoder createEncoder(Context context, MediaFormat format, MediaMux mediaMux) {
    Preconditions.checkNotNull(format);
    if (!MediaCreationUtils.isVideoFormat(format)) {
      Log.e(TAG, "Not a video format");
      return null;
    }

    // Forcing to VBR mode which is required for 60+fps capture.
    format.setInteger(MediaFormat.KEY_BITRATE_MODE, OMX_VIDEO_CONTROL_RATE_VARIABLE);

    try {
      return new VideoEncoder(format, mediaMux);
    } catch (Exception e) {
      Log.e(TAG, "Could not create video encoder", e);
      return null;
    }
  }
}
