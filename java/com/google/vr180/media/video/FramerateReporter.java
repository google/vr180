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

import com.google.vr180.common.logging.Log;
import java.util.ArrayDeque;

/** Class for estimating and reporting framerate from timestamps. */
public class FramerateReporter {
  private static final String TAG = "FramerateReporter";
  private static final int MAX_TIMESTAMP_QUEUE_LENGTH = 300;
  private static final int NUM_FRAMES_TO_REPORT_FRAMERATE = 300;
  private static final long NS_PER_SERCOND = 1_000_000_000L;
  private final ArrayDeque<Long> timestampQueue = new ArrayDeque<>();
  private final String name;
  private int frameCount;

  public FramerateReporter(String name) {
    this.name = name;
  }

  public void addTimestamp(long timestampNs) {
    if (timestampNs == 0) {
      reset();
      return;
    }
    if (timestampQueue.size() == MAX_TIMESTAMP_QUEUE_LENGTH) {
      timestampQueue.removeFirst();
    }
    ++frameCount;
    timestampQueue.add(timestampNs);

    // Report in log
    if (frameCount % NUM_FRAMES_TO_REPORT_FRAMERATE == 0) {
      Log.d(TAG, name + " rate = " + getFramerate());
    }
  }

  public double getFramerate() {
    if (timestampQueue.size() < 2) {
      return 0.0;
    }
    double durationNs = timestampQueue.getLast() - timestampQueue.getFirst();
    return NS_PER_SERCOND * (timestampQueue.size() - 1) / durationNs;
  }

  public int getFrameCount() {
    return frameCount;
  }

  public void reset() {
    frameCount = 0;
    timestampQueue.clear();
  }
}
