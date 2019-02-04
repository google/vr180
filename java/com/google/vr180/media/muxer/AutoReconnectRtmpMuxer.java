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

package com.google.vr180.media.muxer;

import android.content.Context;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Pair;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.MediaConstants;
import com.google.vr180.media.MediaEncoder;
import java.io.IOException;
import java.util.Random;
import javax.annotation.Nullable;

/**
 * Implementation of the {@link MediaMux} interface based on {@link RtmpMuxer} with additional
 * support of auto reconnection to server.
 */
public final class AutoReconnectRtmpMuxer implements MediaMux {
  private static final String TAG = "AutoReconnectRtmpMuxer";

  // The maximum number of reconnect attempts.
  private static final int MAX_RECONNECT_ATTEMPTS = 16;
  // The initial wait before reconnect.
  private static final double INITIAL_WAIT_SECONDS = 1.0;
  // The maximum wait before each reconnect.
  private static final double MAX_WAIT_SECONDS = 60.0;

  private final Context context;
  private final Uri targetUri;
  private final String streamKey;
  private final MediaFormat[] formats = new MediaFormat[2];
  private final MediaEncoder[] encoders = new MediaEncoder[2];
  private final Random random = new Random();

  // Current muxer. It is not null when created, but can become null when reconnecting.
  private RtmpMuxer muxer;
  private ErrorCallback errorCallback;
  private boolean started = false;
  private int outputBufferLimit = Integer.MAX_VALUE;
  private long previousBytesWritten = 0L;

  // Reconnect attempt history.
  private int numReconnectAttempts = 0;
  private long timeToReconnect = 0L;

  public AutoReconnectRtmpMuxer(Context context, Uri targetUri, String streamKey)
      throws IOException {
    this.context = context;
    this.targetUri = targetUri;
    this.streamKey = streamKey;
    muxer = new RtmpMuxer(context, targetUri, streamKey);
    // When there is an error, schedule to reconnect with server.
    muxer.setErrorCallback(unused -> scheduleReconnect());
  }

  @Override
  public void setErrorCallback(@Nullable ErrorCallback errorCallback) {
    Preconditions.checkNotNull(muxer);
    this.errorCallback = errorCallback;
  }

  @Override
  public int addTrack(MediaFormat format, MediaEncoder encoder) {
    Preconditions.checkNotNull(muxer);
    int index = muxer.addTrack(format, encoder);
    if (index >= 0) {
      Preconditions.checkState(index <= 1);
      formats[index] = format;
      encoders[index] = encoder;
    }
    return index;
  }

  @Override
  public boolean hasAllTracks() {
    return (muxer != null && muxer.hasAllTracks());
  }

  @Override
  public boolean release() {
    started = false;
    return muxer != null ? muxer.release() : true;
  }

  @Override
  public int prepare() {
    return muxer != null ? muxer.prepare() : MediaConstants.STATUS_ERROR;
  }

  @Override
  public boolean start() {
    Preconditions.checkNotNull(muxer);
    started = muxer.start();
    return started;
  }

  @Override
  public boolean stop() {
    started = false;
    return muxer != null ? muxer.stop() : true;
  }

  @Override
  public boolean isStarted() {
    return started;
  }

  @Override
  public boolean writeSampleDataAsync(int trackIndex, int bufferIndex, BufferInfo bufferInfo) {
    if (muxer == null) {
      // Discard sample data until connection is restored.
      encoders[trackIndex].releaseOutputBuffer(bufferIndex);
      return reconnect();
    }

    boolean success = muxer.writeSampleDataAsync(trackIndex, bufferIndex, bufferInfo);
    if (started && !success) {
      scheduleReconnect();
    }
    return started || success;
  }

  // Close the current muxer and schedule reconnect.
  private void scheduleReconnect() {
    if (muxer != null) {
      previousBytesWritten += muxer.getBytesWritten();
      muxer.stop();
      muxer.release();
      muxer = null;
    }

    // Schedule to reconnect
    numReconnectAttempts = 0;
    long delaySeconds = getReconnectDelaySeconds(0);
    Log.i(TAG, "Reconnect in " + delaySeconds + " seconds.");
    timeToReconnect = SystemClock.elapsedRealtime() + delaySeconds * 1000L;
  }

  // Re-create RtmpMuxer after some wait.
  private boolean reconnect() {
    // Wait according to schedule.
    if (SystemClock.elapsedRealtime() < timeToReconnect) {
      // Not yet time to reconnect.
      return true;
    }

    Log.i(TAG, "reconnect to " + targetUri + " with key " + streamKey);
    try {
      muxer = new RtmpMuxer(context, targetUri, streamKey);
      muxer.setErrorCallback(unused -> scheduleReconnect());
      Preconditions.checkState(0 == muxer.addTrack(formats[0], encoders[0]));
      Preconditions.checkState(1 == muxer.addTrack(formats[1], encoders[1]));
      if (muxer.prepare() == MediaConstants.STATUS_SUCCESS && muxer.start()) {
        Log.i(TAG, "RTMP connection is restored");
        muxer.setOutputBufferLimit(outputBufferLimit);
        numReconnectAttempts = 0;
        return true;
      }
      muxer.release();
    } catch (Exception e) {
      Log.e(TAG, "Failed to reconnect", e);
    }

    muxer = null;
    numReconnectAttempts++;

    if (numReconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
      Log.e(TAG, "Reached the maximumber of reconnect attempts.");
      if (errorCallback != null) {
        errorCallback.onError(MediaConstants.STATUS_STREAM_ERROR);
      }
      return false;
    }

    long delaySeconds = getReconnectDelaySeconds(numReconnectAttempts);

    Log.i(TAG, "Reconnect in " + delaySeconds + " seconds.");
    timeToReconnect = SystemClock.elapsedRealtime() + delaySeconds * 1000L;
    return true;
  }

  @Override
  public long getBytesWritten() {
    return previousBytesWritten + (muxer != null ? muxer.getBytesWritten() : 0L);
  }

  @Override
  public int getOutputBufferUsed() {
    // A return value of -1 will let AbrController ignore the result of getCurrentByteThroughput.
    return muxer != null ? muxer.getOutputBufferUsed() : -1;
  }

  @Override
  public Pair<Integer, Integer> getCurrentByteThroughput() {
    return muxer != null ? muxer.getCurrentByteThroughput() : new Pair<>(0, 0);
  }

  @Override
  public void setOutputBufferLimit(int bytes) {
    if (muxer != null) {
      muxer.setOutputBufferLimit(bytes);
    }
    outputBufferLimit = bytes;
  }

  @Override
  public void cleanupPartialResults() {}

  // Randomized exponential backoff.
  private long getReconnectDelaySeconds(int numAttempts) {
    // Randomized ratio between 0.75 and 1.25;
    double randomFactor = (random.nextDouble() - 0.5) * 0.5 + 1.0;
    // Exponential increase of 1.5x.
    double exponentialFactor = Math.pow(1.5, numAttempts);
    return (long)
        Math.min(INITIAL_WAIT_SECONDS * randomFactor * exponentialFactor, MAX_WAIT_SECONDS);
  }
}
