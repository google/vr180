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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaFormat;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.MediaCreationUtils;

/** Factory for creating instances of {@link AudioInput}. */
public class AudioInputFactory {
  private static final int ENCODING_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
  private static final int AUDIO_SAMPLE_RATE_REQUIRED = 44100;
  private static final String TAG = "AudioInputFactory";

  private static AudioInputFactory factory;

  public static AudioInputFactory getInstance() {
    if (factory == null) {
      factory = new AudioInputFactory();
    }
    return factory;
  }

  private AudioInputFactory() {}

  /**
   * Create a new instance of an {@link AudioInput}
   *  @return the created {@link AudioInput} object or {@code null} on error.
   *
   * @param format format for the audio with encoding parameters. Must not be null, and must
   * represent an audio format.
   * @param clientHandler Handler on background thread for accessing the input device.
   */
  public AudioInput createAudioInput(MediaFormat format, Handler clientHandler) {
    Preconditions.checkNotNull(format);
    Preconditions.checkNotNull(clientHandler);
    if (!MediaCreationUtils.isAudioFormat(format)) {
      Log.e(TAG, "Not an audio format");
      return null;
    }

    try {
      // Open the mic with 16-bit PCM
      int encoding = ENCODING_FORMAT;
      int audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      int channelIn = format.getInteger(MediaFormat.KEY_CHANNEL_MASK);
      int bufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);

      AudioRecord audioRecorder =
          new AudioRecord(AudioSource.MIC, audioSampleRate, channelIn, encoding, bufferSize);
      if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
        // Try again with guaranteed params
        audioSampleRate = AUDIO_SAMPLE_RATE_REQUIRED;
        channelIn = AudioFormat.CHANNEL_IN_MONO;

        audioRecorder.release();
        audioRecorder =
            new AudioRecord(AudioSource.MIC, audioSampleRate, channelIn, encoding, bufferSize);
        if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
          Log.e(TAG, "Could not get an audio recorder for the mic");
          return null;
        }
      }

      return new MicInput(audioRecorder, channelIn, audioSampleRate, bufferSize, clientHandler);
    } catch (Exception e) {
      Log.e(TAG, "Could not create mic input", e);
      return null;
    }
  }
}
