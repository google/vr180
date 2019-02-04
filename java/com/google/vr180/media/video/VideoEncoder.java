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

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import com.google.vr180.media.BaseEncoder;
import com.google.vr180.media.MediaEncoder;
import com.google.vr180.media.SlowmoFormat;
import com.google.vr180.media.muxer.MediaMux;
import java.io.IOException;
import java.util.ArrayDeque;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Implementation of {@link MediaEncoder} that provides video encoding support, as well as
 * connection to an input source.
 */
@NotThreadSafe
public class VideoEncoder extends BaseEncoder {
  private static final String TAG = "VideoEncoder";
  private static final int MIN_BITRATE_UPDATE_MILLIS_NORMAL = 66;

  private final Bundle setBitrateBundle = new Bundle();
  private final Handler handler;
  private final int minBitrateUpdateMillis = MIN_BITRATE_UPDATE_MILLIS_NORMAL;
  private final int speedFactor;

  private final FramerateReporter framerateReporter = new FramerateReporter(TAG);
  private Surface inputSurface;
  private boolean hasReceivedInputBuffer;
  private boolean released;
  private int width;
  private int height;
  private final ArrayDeque<Integer> pendingBitrates = new ArrayDeque<>();
  private long lastBitrateChangeMillis;

  VideoEncoder(MediaFormat format, MediaMux mediaMux) throws IOException {
    super(format, mediaMux, /*useMediaCodec=*/ true);

    inputSurface = this.encoder.createInputSurface();
    if (inputSurface == null) {
      throw new RuntimeException("Could not create input surface");
    }

    speedFactor = SlowmoFormat.getSpeedFactor(format);
    width = format.getInteger(MediaFormat.KEY_WIDTH);
    height = format.getInteger(MediaFormat.KEY_HEIGHT);
    handler = new Handler(Looper.myLooper() == null ? Looper.getMainLooper() : Looper.myLooper());
  }

  public Surface getInputSurface() {
    return inputSurface;
  }

  @Override
  public boolean start() {
    framerateReporter.reset();
    return super.start();
  }

  @Override
  public boolean release() {
    released = true;
    boolean result = super.release();
    if (inputSurface != null) {
      inputSurface.release();
      inputSurface = null;
    }
    return result;
  }

  @Override
  protected void signalEndOfStream() {
    try {
      encoder.signalEndOfInputStream();
    } catch (IllegalStateException e) {
      Log.e(TAG, "Error ending stream for video encoder", e);
    }
  }

  @Override
  public void onOutputBufferAvailable(MediaCodec codec, int bufIndex, BufferInfo bufferInfo) {
    framerateReporter.addTimestamp(bufferInfo.presentationTimeUs * 1000L);
    bufferInfo.presentationTimeUs *= speedFactor;
    super.onOutputBufferAvailable(codec, bufIndex, bufferInfo);
  }

  @Override
  public void onInputBufferAvailable(MediaCodec mediaCodec, int bufIndex) {
    // This muxer should not call this routine, but if it does, log the event just once.
    if (!hasReceivedInputBuffer) {
      Log.e(TAG, "Video codec unexpectedly provided an input buffer");
      hasReceivedInputBuffer = true;
    }
  }

  @Override
  public void setTargetBitrate(int newBitrate) {
    boolean noChangePending = pendingBitrates.isEmpty();
    int bitrateBeforeChange = (noChangePending ? targetBitrate : pendingBitrates.peekLast());
    if (newBitrate == bitrateBeforeChange) {
      // Nothing to do.
      Log.d(TAG, "Ignoring change to same bitrate: " + newBitrate);
      return;
    }

    pendingBitrates.add(newBitrate);

    if (noChangePending) {
      // No changes are pending.  So, post an update immediately if there have been no changes
      // or if the time since the last change exceeds the minimum.  Otherwise, post the
      // remaining delta to the minimum.
      long nextPossibleChangeMillis = lastBitrateChangeMillis + minBitrateUpdateMillis;
      long delayMillis =
          (lastBitrateChangeMillis > 0)
              ? Math.max(nextPossibleChangeMillis - System.currentTimeMillis(), 0)
              : 0;
      handler.postDelayed(() -> updateBitrate(), delayMillis);
    }
  }

  @Override
  public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {
    super.onOutputFormatChanged(mediaCodec, mediaFormat);
    width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
    height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  private void updateBitrate() {
    if (released) {
      pendingBitrates.clear();
      return;
    }

    targetBitrate = pendingBitrates.removeFirst();
    long now = System.currentTimeMillis();
    long intervalMillis = (lastBitrateChangeMillis > 0) ? (now - lastBitrateChangeMillis) : 0;
    lastBitrateChangeMillis = now;
    Log.d(TAG, "Adjusting bitrate: target=" + targetBitrate + ", intervalMillis=" + intervalMillis);
    setBitrateBundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, targetBitrate);
    try {
      encoder.setParameters(setBitrateBundle);
    } catch (Exception e) {
      Log.e(TAG, "Failed to set bitrate: " + e);
    }
    if (!pendingBitrates.isEmpty()) {
      // Post the next pending change the minimum time in the future.
      handler.postDelayed(() -> updateBitrate(), minBitrateUpdateMillis);
    }
  }
}
