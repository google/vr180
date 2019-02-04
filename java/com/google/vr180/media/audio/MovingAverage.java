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

import com.google.common.base.Preconditions;

/**
 * Utility class for maintaining a moving average with a sliding window, while also tracking the
 * time over which the average has been tracked.
 */
public final class MovingAverage {

  private static final int DEFAULT_WINDOW_SIZE = 10;

  private final double windowSize;
  private double movingAverage;
  private long startTimeNanos;
  private long lastSampleTimeNanos;
  private int sampleCount;

  /**
   * Create a moving average with the default window size.
   */
  public MovingAverage() {
    this(DEFAULT_WINDOW_SIZE);
  }

  /**
   * Create a moving average with the given window size measured in number of samples.
   */
  public MovingAverage(int windowSize) {
    Preconditions.checkArgument(windowSize > 0);
    this.windowSize = windowSize;
    reset();
  }

  /**
   * Reset the moving average value, as well as the initial time of the measurement period.
   */
  public void reset() {
    movingAverage = 0.0;
    sampleCount = 0;
    startTimeNanos = System.nanoTime();
    lastSampleTimeNanos = startTimeNanos;
  }

  /**
   * Add the given sample value to the moving average and return the updated average.
   */
  public void addSample(double value) {
    lastSampleTimeNanos = System.nanoTime();
    if (sampleCount == 0) {
      movingAverage = value;
    } else {
      movingAverage += ((value - movingAverage) / windowSize);
    }
    sampleCount++;
  }

  /**
   * @return the current moving average.
   */
  public double getMovingAverage() {
    return movingAverage;
  }

  /**
   * @return the absolute value of the current moving average.
   */
  public double getAbsoluteMovingAverage() {
    return Math.abs(movingAverage);
  }

  /**
   * @return the period of real time, in microseconds, over which the current moving average
   *   value was calculated
   */
  public long getMeasurementPeriodMicros() {
    return (lastSampleTimeNanos - startTimeNanos) / 1000L;
  }
}
