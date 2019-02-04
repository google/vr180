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

import android.os.Handler;
import android.util.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.muxer.MediaMux;
import com.google.vr180.media.rtmp.Clock;
import com.google.vr180.media.rtmp.TrueMovingAverage;
import com.google.vr180.media.video.VideoEncoder;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Controller for managing adaptive bitrate settings and behavior in the capture pipeline. */
public class AbrController {
  private static final String TAG = "AbrController";

  // Rate to execute the abr controller algorithm.
  @VisibleForTesting static final long SAMPLE_DELAY_MILLIS = 200L;

  private static final long MILLIS_PER_SECOND = 1000;
  private static final double SECONDS_PER_MILLIS = 1.0 / 1000.0;

  private static final long BUFFER_LIMIT_MAX_MILLIS = 500;
  private static final double INCREASE_MULTIPLIER_FAST = 1.25;
  private static final double INCREASE_MULTIPLIER_SLOW = 1.10;
  private static final double DECREASE_MULTIPLIER_FAST = 0.80;
  // How much throughput to dedicate to emptying the buffer as the fraction 1/<X>.
  private static final int BUFFER_CLEAR_DIVIDER = 3;
  // Indicate how often to allow an increase.
  private static final int BUFFER_LOW_FLAG_PERIOD_MILLIS = 4000;
  private static final long MOVING_AVERAGE_BITRATE_MILLIS = 2000;
  private static final double BUFFER_NEAR_FULL_RATIO = 0.80;

  private static final double UPPER_AVERAGE_EXPONENTIAL_SCALER = 0.3;


  // Keep track of the buffer size for the last 3 samples.
  private static final int BUFFER_USED_QUEUE_LENGTH = 3;

  // Limit throughput buffer to 4 seconds.
  private static final int BUFFER_LIMIT_MILLIS = 4000;

  private static final int AUDIO_BITRATE = 128000;
  private static final int METADATA_BITRATE = 16000;

  private final VideoEncoder videoEncoder;
  private final MediaMux mediaMux;
  private final int minBitrate;
  private final int maxBitrate;
  private final Handler codecHandler;
  // Average bitrate out of the network buffer, including video, audio, and packetization overhead.
  private TrueMovingAverage averageOutputBitrate;

  private final Clock clock;
  private final ScheduledExecutorService scheduledExecutorService;

  // Keep track of the buffer size for the last 3 samples.
  private ArrayDeque<Integer> bufferDeltaQueue = new ArrayDeque<>();;

  private int videoBitrate;

  // Limit to the size (bytes) of the output buffer.
  private int outputBufferLimit = Integer.MAX_VALUE;

  private ScheduledFuture<?> scheduleHandle;

  private int targetBitrate;
  private int previousBufferLengthMillis;
  private int previousBufferLengthBytes;
  private int upperAverageBitrate = -1;
  private long lastBufferLowMillis = -1;
  private long lastUpdateMillis = -1;

  public AbrController(
      int minBitrate,
      int maxBitrate,
      int targetBitrate,
      VideoEncoder videoEncoder,
      MediaMux mediaMux,
      ScheduledExecutorService executorService,
      Handler codecHandler,
      Clock clock) {
    this.minBitrate = minBitrate;
    this.targetBitrate = targetBitrate;
    this.maxBitrate = maxBitrate;
    this.videoEncoder = Preconditions.checkNotNull(videoEncoder);
    this.mediaMux = Preconditions.checkNotNull(mediaMux);
    this.codecHandler = Preconditions.checkNotNull(codecHandler);
    this.clock = Preconditions.checkNotNull(clock);
    this.scheduledExecutorService = Preconditions.checkNotNull(executorService);

    Preconditions.checkArgument(minBitrate <= targetBitrate && targetBitrate <= maxBitrate);
    Preconditions.checkArgument(targetBitrate > 0);
    Log.d(TAG, "Bitrate: min=" + minBitrate + ", target=" + targetBitrate + ", max=" + maxBitrate);
    videoBitrate = targetBitrate;

    averageOutputBitrate =
        new TrueMovingAverage((int) (MOVING_AVERAGE_BITRATE_MILLIS / SAMPLE_DELAY_MILLIS));
    videoEncoder.setTargetBitrate(targetBitrate);
   }

  /**
   * Test whether the {@link AbrController} is active.
   *
   * @return {@code true} if active, {@code false} otherwise.
   */
  public boolean isActive() {
    return (scheduleHandle != null);
  }

  /**
   * Set whether the {@link AbrController} is active.
   *
   * @return {@code true} if active, {@code false} otherwise.
   */
  public boolean setActive(boolean makeActive) {
    if (makeActive && scheduleHandle == null) {
      outputBufferLimit =
          calcOutputBufferLimit(0 /* bufferUsedMillis */, 0 /* bufferUsedBytes */, videoBitrate);
      codecHandler.post(() -> mediaMux.setOutputBufferLimit(outputBufferLimit));
      averageOutputBitrate.reset();
      upperAverageBitrate = -1;
      scheduleHandle =
          scheduledExecutorService.scheduleWithFixedDelay(
              new UpdateRunnable(),
              SAMPLE_DELAY_MILLIS,
              SAMPLE_DELAY_MILLIS,
              TimeUnit.MILLISECONDS);
    } else if (!makeActive && scheduleHandle != null) {
      scheduleHandle.cancel(true);
      scheduleHandle = null;
    }
    return true;
  }

  /** Get the target video bitrate (bits/sec). */
  public int getTargetVideoBitrate() {
    return targetBitrate;
  }

  // Determine the output buffer limit (bytes) based on the video bitrate and existing buffer data.
  @VisibleForTesting
  int calcOutputBufferLimit(int bufferUsedMillis, int bufferUsedBytes, int videoBitrate) {
    int bufferLimit;
    if (bufferUsedMillis >= BUFFER_LIMIT_MILLIS) {
      double ratio = (double) (BUFFER_LIMIT_MILLIS) / bufferUsedMillis;
      bufferLimit = (int) (ratio * bufferUsedBytes);
    } else {
      int estimatedBitrate = videoBitrate + AUDIO_BITRATE + METADATA_BITRATE;
      bufferLimit =
          bufferUsedBytes
              + (int)
                  (((double) (BUFFER_LIMIT_MILLIS - bufferUsedMillis) / MILLIS_PER_SECOND)
                      * estimatedBitrate
                      / 8 /* bits/byte */);
    }
    return bufferLimit;
  }

  @VisibleForTesting
  int estimateUsedBufferMillis(
      int lastUsedBufferMillis,
      int lastUsedBufferBytes,
      int currentUsedBuffer,
      int currentBytesPerSecond,
      int inBytes) {

    if (currentUsedBuffer == 0) {
      return 0;
    }
    int leftoverBytes = Math.max(currentUsedBuffer - inBytes, 0);
    double leftoverSeconds = 0;
    if ((lastUsedBufferBytes > 0 && lastUsedBufferMillis > 0) || leftoverBytes > 0) {
      leftoverSeconds =
          (double) leftoverBytes
              * (((double) lastUsedBufferMillis * SECONDS_PER_MILLIS) / lastUsedBufferBytes);
    }
    int newBytes = Math.max((currentUsedBuffer - leftoverBytes), 0);

    double bufferSeconds = leftoverSeconds;
    if (currentBytesPerSecond > 0) {
      bufferSeconds = leftoverSeconds + ((double) newBytes / (currentBytesPerSecond));
    }
    return (int) (bufferSeconds * MILLIS_PER_SECOND);
  }

  /**
   * Responsible for adaptively adjusting the video bitrate depending on the quality of the network
   * stream.
   */
  private class UpdateRunnable implements Runnable {

    @Override
    public void run() {
      int bufferUsed = mediaMux.getOutputBufferUsed();
      if (bufferUsed < 0) {
        return;
      }

      long curMillis = clock.elapsedMillis();

      // Get statistics on number of bytes entering and exiting the output buffer.
      int inBytes;
      int outBytes;
      Pair<Integer, Integer> throughputPair = mediaMux.getCurrentByteThroughput();
      inBytes = throughputPair.first;
      outBytes = throughputPair.second;
      Log.d(TAG, "Raw Throughput: " + inBytes + " -> " + outBytes);

      // Calculate the bitrate based on the time since last throughput request.
      int bufferDelta = inBytes - outBytes;
      long deltaMillis = Math.max(curMillis - lastUpdateMillis, 1);
      lastUpdateMillis = curMillis;
      double inMbps = (((double) inBytes * 8 / ((double) deltaMillis)) / MILLIS_PER_SECOND);
      double outMbps = (((double) outBytes * 8 / ((double) deltaMillis)) / MILLIS_PER_SECOND);
      Log.d(
          TAG,
          "Throughput: "
              + "in = "
              + String.format("%.3f", inMbps)
              + "Mbps "
              + "out = "
              + String.format("%.3f", outMbps)
              + "Mbps ");

      int inBytesPerSecond = 0;
      if (lastUpdateMillis > 0) {
        inBytesPerSecond =
            (int)
                (inBytes
                    / ((double) Math.max(deltaMillis, SAMPLE_DELAY_MILLIS) * SECONDS_PER_MILLIS));
      }
      int bufferUsedMillis =
          estimateUsedBufferMillis(
              previousBufferLengthMillis,
              previousBufferLengthBytes,
              bufferUsed,
              inBytesPerSecond,
              inBytes);
      previousBufferLengthBytes = bufferUsed;
      previousBufferLengthMillis = bufferUsedMillis;

      double usedBufferRatio = ((double) bufferUsed / (double) outputBufferLimit);
      Log.d(TAG, "Used Buffer: " + bufferUsed + " Ratio:" + usedBufferRatio);

      // Keep track of the buffer size.
      if (bufferDeltaQueue.size() == BUFFER_USED_QUEUE_LENGTH) {
        bufferDeltaQueue.remove();
      }
      bufferDeltaQueue.add(bufferDelta);

      // Check if a majority of the buffer changes have increased its size.
      int deltaThroughputChange = 0;
      for (Iterator<Integer> iter = bufferDeltaQueue.iterator(); iter.hasNext(); ) {
        int curDeltaThroughput = iter.next();
        if (curDeltaThroughput > 0) {
          // More being queued.
          ++deltaThroughputChange;
        } else {
          // Queue is draining.
          --deltaThroughputChange;
        }
      }

      // Do a moving average of the network output to level any bouncing.
      if (deltaMillis > 0) {
        averageOutputBitrate.addSample((outBytes * 8) * ((double) MILLIS_PER_SECOND / deltaMillis));
      }
      // If 2+ buffer increases in a row, use this in the exponential upper throughput average.
      if ((outBytes > 0)
          && (deltaMillis > 0)
          && ((deltaThroughputChange >= 2) || (bufferDelta > 0))) {
        if (upperAverageBitrate < 0) {
          upperAverageBitrate = (int) (averageOutputBitrate.getMovingAverage());
        } else {
          upperAverageBitrate =
              (int)
                  (averageOutputBitrate.getMovingAverage() * UPPER_AVERAGE_EXPONENTIAL_SCALER
                      + (upperAverageBitrate * (1 - UPPER_AVERAGE_EXPONENTIAL_SCALER)));
        }
      }

      // Primary Algorithm Flags.
      boolean isGrowth = false;
      boolean isBufferLimit = false;
      boolean isBufferLow = false;
      boolean isFrameRateLimiting = false;

      // Three consecutive increases in throughput buffer.
      if (deltaThroughputChange == 3) {
        isGrowth = true;
      }

      // Frame rate is being limited if the buffer is near full.
      if (bufferUsed > (outputBufferLimit * BUFFER_NEAR_FULL_RATIO)) {
        isFrameRateLimiting = true;
      }

      // Buffer Limit when buffer greater than max.
      if (bufferUsedMillis > BUFFER_LIMIT_MAX_MILLIS) {
        isBufferLimit = true;

        // Hold off on isBufferLow flag.
        lastBufferLowMillis = curMillis;
      }

      // Buffer low flag limited to once every 4 seconds.
      if (bufferUsedMillis < SAMPLE_DELAY_MILLIS) {
        if (curMillis - lastBufferLowMillis > BUFFER_LOW_FLAG_PERIOD_MILLIS) {
          lastBufferLowMillis = curMillis;
          isBufferLow = true;
        }
      }

      boolean increaseBitrate = false;
      boolean decreaseBitrate = false;
      if (isGrowth || isBufferLimit || isFrameRateLimiting) {
        // Lower bitrate to the average throughput minus a portion of the used buffer.
        int newVideoBitrate = ((upperAverageBitrate - ((bufferUsed * 8) / BUFFER_CLEAR_DIVIDER)));
        if (newVideoBitrate < videoBitrate) {
          videoBitrate = newVideoBitrate;
        } else {
          // It could be possible the upperAverageBitrate is high, if so, then scale down.
          videoBitrate = (int) ((double) videoBitrate * DECREASE_MULTIPLIER_FAST);
        }
        decreaseBitrate = true;
      } else if (isBufferLow) {
        if (videoBitrate < upperAverageBitrate || upperAverageBitrate <= 0) {
          videoBitrate = (int) ((double) videoBitrate * INCREASE_MULTIPLIER_FAST);
        } else {
          // Slower increase rate when above the upper average bitrate.
          videoBitrate = (int) ((double) videoBitrate * INCREASE_MULTIPLIER_SLOW);
        }
        increaseBitrate = true;
      } else {
        // no change.
      }
      // Enforce encoder limits.
      videoBitrate = Math.min(Math.max(videoBitrate, minBitrate), maxBitrate);
      Log.d(
          TAG,
          "EncoderBitrate: " + (videoBitrate / 1000) + "kbps Buffer: " + bufferUsedMillis + "ms");
      outputBufferLimit = calcOutputBufferLimit(bufferUsedMillis, bufferUsed, videoBitrate);

      final boolean increase = increaseBitrate;
      final boolean decrease = decreaseBitrate;
      codecHandler.post(
          () -> {
            if (increase) {
              // When increasing bitrate, set the limit higher first.
              mediaMux.setOutputBufferLimit(outputBufferLimit);
              videoEncoder.setTargetBitrate(videoBitrate);
            } else {
              // When decreasing bitrate, set the bitrate first.
              if (decrease) {
                videoEncoder.setTargetBitrate(videoBitrate);
              }
              // Always update the buffer limit.
              mediaMux.setOutputBufferLimit(outputBufferLimit);
            }
          });
    }
  }

  @VisibleForTesting
  Runnable getUpdateRunnable() {
    return new UpdateRunnable();
  }

  @VisibleForTesting
  int getSetTargetBitrate() {
    return videoBitrate;
  }
}
