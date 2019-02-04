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

/** Interface for managing timestamp continuity across stream reconnects. */
public interface TimestampContinuityManager {

  /** Start a new stream with the given initial timestamp in milliseconds. */
  void startNewStream(long startTimeMs);

  /** @return timestamp in milliseconds of the most recent {@link #startNewStream(long)}. */
  long getStartTimeMs();

  /**
   * Adjust the given 64-bit timestamp to be (normally) 0-based while ensuring a discontinuity on
   * stream reconnect.
   *
   * @param timestampMs timestamp in milliseconds to adjust, given the starting time and the
   *     reconnect state.
   * @return an adjusted 32-bit timestamp, or < 0 if the input value is out of range, i.e. precedes
   *     the start time.
   */
  int adjustTimestamp(long timestampMs);
}
