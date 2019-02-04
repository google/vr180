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

/**
 * An interface for implementations that can provide the current value of time after system boot.
 *
 * Tests can use mock implementations to manipulate the perception of time of the tested class.
 */
public interface Clock {
  /**
   * Gets values of time that always monotonically increase but reset to 0 when the device
   * restarts. Values returned from this are not representative of the actual time the user sees.
   *
   * @return the elapsed time in milliseconds since device restart.
   */
  long elapsedMillis();

  /** Gets the current system time in milliseconds. */
  long getCurrentTimeMillis();
}
