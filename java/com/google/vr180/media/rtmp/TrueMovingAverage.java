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

import com.google.common.base.Preconditions;
import java.util.Arrays;

/** Utility class for maintaining a true moving average. */
public final class TrueMovingAverage {

  private static final int DEFAULT_WINDOW_SIZE = 10;

  private final int size;
  private int sampleCount;
  private double movingSum;
  private int bufferIndex;
  private final double[] buffer;

  /** Create a moving average with the default window size. */
  public TrueMovingAverage() {
    this(DEFAULT_WINDOW_SIZE);
  }

  /** Create a moving average with the given window size measured in number of samples. */
  public TrueMovingAverage(int windowSize) {
    Preconditions.checkArgument(windowSize > 0);
    buffer = new double[windowSize];
    size = windowSize;
    reset();
  }

  /**
   * Reset the moving average value, as well as the initial time of the measurement period.
   */
  public void reset() {
    Arrays.fill(buffer, 0);
    sampleCount = 0;
    movingSum = 0;
    bufferIndex = 0;
  }

  /**
   * Add the given sample value to the moving average and return the updated average.
   */
  public void addSample(double value) {
    movingSum -= buffer[bufferIndex];
    buffer[bufferIndex++] = value;
    movingSum += value;
    if (bufferIndex >= size) {
      bufferIndex = 0;
    }
    if (sampleCount < size) {
      sampleCount++;
    }
  }

  /**
   * @return the current moving average.
   */
  public double getMovingAverage() {
    if (sampleCount == 0) {
      return 0;
    }
    return movingSum / sampleCount;
  }
}
