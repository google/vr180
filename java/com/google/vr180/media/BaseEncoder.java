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

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.MediaFormat;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.muxer.MediaMux;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Base implementation of the {@link MediaEncoder}. Traps all Exceptions and returns success/fail
 * instead so that clients can be written in a clean fashion.
 */
@TargetApi(21)
@NotThreadSafe
public abstract class BaseEncoder extends MediaCodec.Callback implements MediaEncoder {
  private static final String TAG = "BaseEncoder";

  private final MediaMux mediaMux;
  private final MediaFormat originalFormat;
  private final String encoderName;

  protected MediaCodec encoder;
  protected int targetBitrate;

  private long lastTimestampUs = -1L;
  private int trackIndex = MediaMux.INVALID_TRACK_INDEX;

  // Encoder states
  enum EncoderState {
    INITIAL,
    STARTED,
    STOPPED,
    RELEASED
  }

  private EncoderState currentState = EncoderState.INITIAL;
  private boolean eosReached;
  private boolean eosRequested;

  // Callback handlers
  private EndOfStreamCallback eosCallback;
  private ErrorCallback errorCallback;

  public BaseEncoder(MediaFormat format, MediaMux mediaMux, boolean useMediaCodec)
      throws IOException {
    originalFormat = Preconditions.checkNotNull(format);
    String name = "";
    if (useMediaCodec) {
      this.encoder = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME));
      this.encoder.setCallback(this);
      this.encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      try {
        name = this.encoder.getName();
      } catch (IllegalStateException e) {
        Log.e(TAG, "Error obtaining codec name", e);
      }
    }
    encoderName = name;
    this.mediaMux = Preconditions.checkNotNull(mediaMux);
  }

  @Override
  public String getName() {
    return encoderName;
  }

  @Override
  public boolean isActive() {
    return (currentState == EncoderState.STARTED);
  }

  @Override
  public boolean start() {
    if (isActive()) {
      // Allow multiple calls without error
      return true;
    }
    if (currentState != EncoderState.INITIAL) {
      Log.e(TAG, "Cannot start once stopped or released: " + getName());
      return false;
    }
    EncoderState oldState = currentState;

    try {
      Log.d(TAG, "Start encoder " + getName());
      if (encoder != null) {
        encoder.start();
      }
      currentState = EncoderState.STARTED;
    } catch (Exception e) {
      Log.d(TAG, "Starting encoder failed: " + getName(), e);
    }

    logStateChange(oldState, currentState);
    return (currentState == EncoderState.STARTED);
  }

  @Override
  public void setErrorCallback(@Nullable ErrorCallback errorCallback) {
    this.errorCallback = errorCallback;
  }

  @Override
  public boolean stop() {
    if (currentState == EncoderState.STOPPED) {
      // Allow multiple calls without error
      return true;
    }
    if (!isActive()) {
      Log.e(TAG, "Encoder not active: " + getName());
      return false;
    }
    EncoderState oldState = currentState;

    try {
      Log.d(TAG, "Stop encoder " + getName());
      if (encoder != null) {
        encoder.stop();
      }
      currentState = EncoderState.STOPPED;
    } catch (Exception e) {
      Log.d(TAG, "Stopping encoder failed: " + getName(), e);
    }

    logStateChange(oldState, currentState);
    return (currentState == EncoderState.STOPPED);
  }

  @Override
  public boolean signalEndOfStream(EndOfStreamCallback callback) {
    if (!isActive()) {
      Log.e(TAG, "Cannot signal EOS unless active: " + getName());
      return false;
    }
    if (eosRequested) {
      // Allow multiple calls without error.
      return true;
    }

    Log.d(TAG, "Signal EOS for encoder " + getName());
    eosRequested = true;
    eosCallback = callback;
    signalEndOfStream();

    return true;
  }

  protected abstract void signalEndOfStream();

  @Override
  public boolean release() {
    if (currentState == EncoderState.RELEASED) {
      // Allow multiple calls without error.
      return true;
    }
    EncoderState oldState = currentState;

    try {
      Log.d(TAG, "Release encoder " + getName());
      if (encoder != null) {
        encoder.release();
      }
      currentState = EncoderState.RELEASED;
    } catch (Exception e) {
      Log.d(TAG, "Releasing encoder failed: " + getName(), e);
    }

    logStateChange(oldState, currentState);
    return (currentState == EncoderState.RELEASED);
  }

  @Override
  public ByteBuffer getOutputBuffer(int index) {
    Preconditions.checkNotNull(encoder);
    return encoder.getOutputBuffer(index);
  }

  @Override
  public void releaseOutputBuffer(int index) {
    Preconditions.checkNotNull(encoder);
    encoder.releaseOutputBuffer(index, false /* no render */);
  }

  @Override
  public void onOutputBufferAvailable(MediaCodec codec, int bufferIndex, BufferInfo bufferInfo) {
    if (codec != encoder || trackIndex < 0) {
      Log.e(TAG, "Skipping request to process buffer on missing codec: " + getName());
      return;
    }

    if (bufferIndex < 0) {
      // Unexpected buffer index.  Ignore it
      Log.e(TAG, "Unexpected buffer index (" + bufferIndex + ") for codec: " + getName());
      return;
    }

    if (eosReached) {
      Log.e(TAG, "Unexpected buffer index (" + bufferIndex + ")  after EOS on codec: " + getName());
      return;
    }

    int flags = bufferInfo.flags;
    if ((flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
      // The codec config data was pulled out and fed to the muxer when we got
      // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
      bufferInfo.size = 0;
    }

    if (!sendSampleDataToMuxer(bufferIndex, bufferInfo)) {
      releaseOutputBuffer(bufferIndex);
    }

    // Handle end of stream for the encoder.
    handlePossibleEndOfStream(flags);
  }

  @Override
  public int getTargetBitrate() {
    return targetBitrate;
  }

  // Visible for testing
  void finishStream() {
    eosReached = true;
    if (eosCallback != null) {
      eosCallback.onEndOfStream(this);
    }
  }

  @Override
  public void onError(MediaCodec mediaCodec, CodecException e) {
    Log.e(TAG, "Encoder encountered error: " + getName(), e);
    notifyError(MediaConstants.STATUS_CODEC_ERROR);
  }

  @Override
  public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
    // Retrieve any values dropped from the original format
    copyFormatIntField(mediaFormat, originalFormat, MediaFormat.KEY_BIT_RATE);
    copyFormatIntField(mediaFormat, originalFormat, MediaFormat.KEY_SAMPLE_RATE);
    copyFormatIntField(mediaFormat, originalFormat, MediaFormat.KEY_FRAME_RATE);
    targetBitrate = mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE);

    int newTrackIndex = mediaMux.addTrack(mediaFormat, this);
    if (newTrackIndex < 0) {
      Log.e(TAG, "Encoder could not add track to muxer: " + getName());
      notifyError(MediaConstants.STATUS_CODEC_ERROR);
    } else {
      trackIndex = newTrackIndex;
      if (mediaMux.hasAllTracks()) {
        if (!mediaMux.start()) {
          Log.e(TAG, "Encoder could not start muxer: " + getName());
          notifyError(MediaConstants.STATUS_IO_ERROR);
        }
      }
    }
  }

  // Returns whether the buffer is sent to a muxer.
  private boolean sendSampleDataToMuxer(int bufferIndex, BufferInfo bufferInfo) {
    if (bufferInfo.size == 0 || bufferInfo.presentationTimeUs <= 0) {
      return false;
    }
    if (bufferInfo.presentationTimeUs < lastTimestampUs) {
      Log.e(TAG, getName() + " timewarp:" + lastTimestampUs + "->" + bufferInfo.presentationTimeUs);
      return false;
    }
    lastTimestampUs = bufferInfo.presentationTimeUs;
    if (!mediaMux.isStarted()) {
      return false;
    }
    // Trigger an error if muxer failed to write the sample.
    if (!mediaMux.writeSampleDataAsync(trackIndex, bufferIndex, bufferInfo)) {
      notifyError(MediaConstants.STATUS_IO_ERROR);
    }
    return true;
  }

  // Handle possible end-of-stream flag.
  private void handlePossibleEndOfStream(int flags) {
    if ((flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
      if (eosReached) {
        Log.w(TAG, "End of stream already reached for codec: " + getName());
      } else {
        if (eosRequested) {
          Log.d(TAG, "End of stream reached for codec: " + getName());
          finishStream();
        } else {
          Log.w(TAG, "Reached end of stream unexpectedly for codec: " + getName());
          notifyError(MediaConstants.STATUS_CODEC_ERROR);
        }
      }
    }
  }

  private void copyFormatIntField(MediaFormat destFormat, MediaFormat srcFormat, String key) {
    if (!destFormat.containsKey(key) && srcFormat.containsKey(key)) {
      destFormat.setInteger(key, srcFormat.getInteger(key));
    }
  }

  protected void notifyError(int errorCode) {
    if (errorCallback != null) {
      errorCallback.onError(this, errorCode);
    }
  }

  private void logStateChange(EncoderState oldState, EncoderState newState) {
    Log.d(TAG, "Set state for " + getName() + " from " + oldState + " to " + newState);
  }

  @VisibleForTesting
  void setLastTimestampUs(long lastTimestampUs) {
    this.lastTimestampUs = lastTimestampUs;
  }

  @VisibleForTesting
  void setEosReached(boolean eosReached) {
    this.eosReached = eosReached;
  }

  @VisibleForTesting
  void setTrackIndex(int trackIndex) {
    this.trackIndex = trackIndex;
  }

  @VisibleForTesting
  void setCurrentState(EncoderState currentState) {
    this.currentState = currentState;
  }
}
