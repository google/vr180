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

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Looper;
import android.support.annotation.NonNull;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.BaseEncoder;
import com.google.vr180.media.MediaConstants;
import com.google.vr180.media.MediaEncoder;
import com.google.vr180.media.SlowmoFormat;
import com.google.vr180.media.muxer.MediaMux;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Implementation of {@link MediaEncoder} that provides audio encoding support, as well as
 * connection to an input source.
 */
@TargetApi(21)
@NotThreadSafe
public class AudioEncoder extends BaseEncoder
    implements AudioInput.FillBufferCallback, AudioInput.ErrorCallback {

  private static final String TAG = "DefaultAudioEncoder";

  private final AudioInput audioInput;
  private final int speedFactor;
  private boolean needEosFlag;
  private boolean hasSentEos;
  private int pendingBufferCount;

  AudioEncoder(MediaFormat format, AudioInput audioInput, MediaMux mediaMux) throws IOException {
    // The method to slow-down audio is as follows:
    // 1. Create AudioInput using original rate
    // 2. Create Encoder using the adjusted rate
    // 3. Send audio data to encoder with adjusted timestamp.
    super(SlowmoFormat.getSpeedAdjustedAudioFormat(format), mediaMux, /*useMediaCodec=*/ true);
    speedFactor = SlowmoFormat.getSpeedFactor(format);

    // Note that audioInput is already created with the original sample rate.
    this.audioInput = audioInput;
    this.audioInput.setFillBufferResponseHandler(this);
    this.audioInput.setErrorCallback(this);
  }

  @Override
  protected void signalEndOfStream() {
    needEosFlag = true;
    audioInput.stop();
  }

  @Override
  public boolean start() {
    // Ensure audio input is started, as {@link #onInputBufferAvailable} may be called
    // as soon as the encoder is started, and the audio input must be ready at that point.
    // In a properly configured system, the audio input should already be started, but
    // better to make sure.
    if (!audioInput.start()) {
      Log.e(TAG, "Failed to ensure audio input was started");
      return false;
    }

    // Start the encoder itself, which will prompt it to start sending buffers to be filled.
    return super.start();
  }

  @Override
  public boolean stop() {
    if (!audioInput.stop()) {
      Log.w(TAG, "Error stopping audio encoder");
    }
    return super.stop();
  }

  @Override
  public boolean release() {
    if (pendingBufferCount > 0) {
      Log.w(TAG, "Buffers still pending from audio input at release: count=" + pendingBufferCount);
    }

    if (!audioInput.release()) {
      Log.w(TAG, "Error releasing audio input");
    }
    return super.release();
  }

  @Override
  public void setTargetBitrate(int bitrate) {
    Log.w(TAG, "Changing bitrate for audio not supported.");
  }

  @Override
  public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int bufferId) {
    Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());

    if (bufferId < 0) {
      // Unexpected buffer index.  Ignore it
      Log.e(TAG, "Unexpected buffer index for codec: " + bufferId);
      return;
    }

    try {
      ByteBuffer buffer = encoder.getInputBuffer(bufferId);
      if (buffer == null) {
        Log.e(TAG, "Got a null buffer. Valid buffer should be present");
        return;
      }

      // Send a request to fill the buffer to the audio input
      pendingBufferCount++;
      audioInput.fillBufferRequest(bufferId, buffer);
    } catch (Exception e) {
      Log.e(TAG, "Error obtaining input buffer for audio encoder", e);
      if (!needEosFlag) {
        notifyError(MediaConstants.STATUS_CODEC_ERROR);
      }
    }
  }

  @Override
  public void onBufferFilled(
      int bufferId, ByteBuffer buffer, int flags, int offset, int count, long timestamp) {
    pendingBufferCount--;
    if (count >= 0) {
      try {
        if (hasSentEos || !isActive()) {
          Log.e(TAG, "Received full buffer after sending end: bufferId=" + bufferId);
        } else {
          encoder.queueInputBuffer(bufferId, offset, count, timestamp * speedFactor, flags);
          hasSentEos = ((flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0);
          if (hasSentEos && !needEosFlag) {
            Log.e(TAG, "Unexpected EOS from audio data");
            notifyError(MediaConstants.STATUS_CODEC_ERROR);
          }
        }
      } catch (Exception e) {
        Log.e(TAG, "Error queuing input to audio encoder", e);
        notifyError(MediaConstants.STATUS_CODEC_ERROR);
      }
    } else if (!needEosFlag) {
      Log.e(TAG, "Error reading audio data: " + count);
      notifyError(MediaConstants.STATUS_CODEC_ERROR);
    }
  }

  @Override
  public void onError(int errorCode) {
    Log.w(TAG, "Audio input error " + errorCode);
  }
}
