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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import java.util.concurrent.TimeUnit;

/**
 * Manage timestamp continuity across stream reconnects by ensuring a minimum delta from the last
 * timestamp of the previous stream to the beginning timestamp of the next stream.
 */
public final class RtmpContinuityManager implements TimestampContinuityManager {
  private static final String TAG = "RtmpContinuityManager";
  private static final String RTMP_CONTINUITY_MANAGER_THREAD_NAME = "RtmpContinuityManager";

  private static final String PREFS_NAME = "vr180";
  private static final String PREFIX = RtmpContinuityManager.class.getName();
  private static final long MIN_DISCONTUINITY_MILLIS = TimeUnit.SECONDS.toMillis(15);
  private static final long SAVE_TIMESTAMP_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(1);

  // Visible for testing.
  static final String KEY_LAST_TIMESTAMP = PREFIX + ".KEY_LAST_TIMESTAMP";

  private final SharedPreferences sharedPreferences;
  private final Handler prefsHandler;
  private long startTimeMs;
  private long adjustmentMs;
  private long lastSavedTimestamp;
  private boolean shouldSaveTimestamps;
  private boolean needFirstTimestamp;

  public static RtmpContinuityManager newInstance(Context context) {
    SharedPreferences sharedPreferences =
        context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    HandlerThread prefsHandlerThread = new HandlerThread(RTMP_CONTINUITY_MANAGER_THREAD_NAME);
    prefsHandlerThread.start();
    Handler prefsHandler = new Handler(prefsHandlerThread.getLooper());
    return new RtmpContinuityManager(sharedPreferences, prefsHandler);
  }

  // Visible for testing.
  RtmpContinuityManager(SharedPreferences sharedPreferences, Handler prefsHandler) {
    this.sharedPreferences = Preconditions.checkNotNull(sharedPreferences);
    this.prefsHandler = Preconditions.checkNotNull(prefsHandler);
  }

  @Override
  public void startNewStream(long startTimeMs) {
    Preconditions.checkArgument(startTimeMs > 0);
    this.startTimeMs = startTimeMs;
    long lastTimestamp = sharedPreferences.getLong(KEY_LAST_TIMESTAMP, MIN_DISCONTUINITY_MILLIS);

    if (lastTimestamp < 0 || lastTimestamp >= MIN_DISCONTUINITY_MILLIS) {
      // Last stream had a large enough timestamp that starting over at 0 will
      // ensure a discontinuity.
      adjustmentMs = 0;
    } else {
      // Force a discontinuity by leaping past the last known timestamp.
      adjustmentMs = lastTimestamp + (2 * MIN_DISCONTUINITY_MILLIS);
    }

    Log.d(TAG, "Start stream: lastTimeMs=" + lastTimestamp + ", adjustmentMs=" + adjustmentMs);
    shouldSaveTimestamps = true;
    needFirstTimestamp = true;
  }

  @Override
  public long getStartTimeMs() {
    return startTimeMs;
  }

  @Override
  public int adjustTimestamp(long timestampMs) {
    Preconditions.checkArgument(timestampMs > 0);
    Preconditions.checkState(startTimeMs > 0);

    long relativeTimeMs = timestampMs - startTimeMs;
    if (relativeTimeMs < 0) {
      // Don't go backward beyond start time.
      return -1;
    }

    final long adjustedTimestamp = relativeTimeMs + adjustmentMs;
    if (adjustedTimestamp > Integer.MAX_VALUE) {
      Log.w(TAG, "Timestamp overflow: " + adjustedTimestamp);
    }

    if (shouldSaveTimestamps) {
      if (needFirstTimestamp
          || ((adjustedTimestamp - lastSavedTimestamp) >= SAVE_TIMESTAMP_INTERVAL_MILLIS)) {
        // Save the latest timestamp.
        prefsHandler.post(
            new Runnable() {
              @Override
              public void run() {
                Editor editor = sharedPreferences.edit();
                editor.putLong(KEY_LAST_TIMESTAMP, adjustedTimestamp);
                editor.apply();
              }
            });

        // Keep saving timestamps until returning to 0 would create a discontinuity.
        lastSavedTimestamp = adjustedTimestamp;
        shouldSaveTimestamps = (lastSavedTimestamp < MIN_DISCONTUINITY_MILLIS);
        needFirstTimestamp = false;
      }
    }

    return (int) adjustedTimestamp;
  }

  @Override
  protected void finalize() throws Throwable {
    // Shut down the thread on cleanup.
    Looper looper = prefsHandler.getLooper();
    if (looper != null) {
      Thread thread = looper.getThread();
      if (thread instanceof HandlerThread
          && TextUtils.equals(RTMP_CONTINUITY_MANAGER_THREAD_NAME, thread.getName())) {
        ((HandlerThread) thread).quitSafely();
      }
    }
    super.finalize();
  }
}
