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

package com.google.vr180.media.audio;

import android.media.MediaFormat;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.MediaCreationUtils;
import com.google.vr180.media.muxer.MediaMux;

/** Factory for creating instances of {@link AudioEncoder}. */
public class AudioEncoderFactory {

  private static final String TAG = "AudioEncoderFactory";
  private static AudioEncoderFactory factory;

  public static AudioEncoderFactory getInstance() {
    if (factory == null) {
      factory = new AudioEncoderFactory();
    }
    return factory;
  }

  private AudioEncoderFactory() {}

  /**
   * Create a new instance and return {@code null} on error.
   *
   * @param format format for the audio with encoding parameters. Must not be null, and must
   * represent an audio format.
   * @param audioInput Input source for this encoder
   * @param mediaMux The muxer into which output encoded data should be fed.
   */
  public AudioEncoder createEncoder(MediaFormat format, AudioInput audioInput, MediaMux mediaMux) {
    Preconditions.checkNotNull(format);
    if (!MediaCreationUtils.isAudioFormat(format)) {
      Log.e(TAG, "Not an audio format");
      return null;
    }

    try {
      return new AudioEncoder(format, audioInput, mediaMux);
    } catch (Exception e) {
      Log.e(TAG, "Could not create audio encoder", e);
      return null;
    }
  }
}
