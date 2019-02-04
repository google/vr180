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

package com.google.vr180.media.rtmp;

import com.google.common.collect.EvictingQueue;
import javax.annotation.concurrent.GuardedBy;

/** Updates estimates of the video stream bitrate. */
public class BitrateEstimator {
  private static final int BITS_PER_BYTE = 8;
  private static final double SEC_PER_MS = 1.0 / 1000.0;

  private final long minTimeBetweenSamplesMs;

  @GuardedBy("this")
  private final EvictingQueue<Sample> samples;

  @GuardedBy("this")
  private Sample latestSample;

  public BitrateEstimator(int minTimeBetweenSamplesMs, int numSamples) {
    this.minTimeBetweenSamplesMs = minTimeBetweenSamplesMs;
    this.samples = EvictingQueue.create(numSamples);
  }

  /** Resets the estimator, clearing all samples. */
  public synchronized void reset() {
    samples.clear();
    latestSample = null;
  }

  /**
   * Adds a sample for bitrate estimation.
   * Samples should be added in order of increasing timestamp.
   * @param timestampMs The timestamp of the sample in milliseconds.
   * @param bytesSent The total number of bytes sent on the channel. This should be monotonically
   *   increasing.
   */
  public synchronized void addSample(long timestampMs, long bytesSent) {
    if (latestSample != null && timestampMs - latestSample.timestampMs < minTimeBetweenSamplesMs) {
      return;
    }

    Sample sample = new Sample();
    sample.timestampMs = timestampMs;
    sample.bytesSent = bytesSent;
    samples.add(sample);
    latestSample = sample;
  }

  /** Returns the estimated bitrate based on samples. If not enough data is available, returns 0. */
  public synchronized double getBitrateEstimate() {
    if (samples.size() < 2) {
      return 0;
    }

    Sample oldestSample = samples.peek();
    return BITS_PER_BYTE
        * (latestSample.bytesSent - oldestSample.bytesSent)
        / (SEC_PER_MS * (latestSample.timestampMs - oldestSample.timestampMs));
  }

  private static class Sample {
    public long timestampMs;
    public long bytesSent;
  }
}
