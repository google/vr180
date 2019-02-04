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

import static org.junit.Assert.assertThrows;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import com.google.common.truth.Truth;
import com.google.vr180.testhelpers.FakeSharedPreferences;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

/** Test for {@link RtmpContinuityManager} */
@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = 28,
    manifest = Config.NONE,
    shadows = {ShadowLooper.class})
public class RtmpContinuityManagerTest {

  private final FakeSharedPreferences sharedPrefs = new FakeSharedPreferences();

  private RtmpContinuityManager rtmpContinuityManager;
  private ShadowLooper shadowLooper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    sharedPrefs.edit().clear().apply();

    HandlerThread thread = new HandlerThread("RtmpContinuityManagerTestThread");
    thread.start();
    Looper looper = thread.getLooper();
    Handler prefsHandler = new Handler(looper);
    shadowLooper = (ShadowLooper) Shadow.extract(looper);

    rtmpContinuityManager = new RtmpContinuityManager(sharedPrefs, prefsHandler);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testZeroStartTime() throws Exception {
    rtmpContinuityManager.startNewStream(0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNegativeStartTime() throws Exception {
    rtmpContinuityManager.startNewStream(-1);
  }

  @Test
  public void testZeroAdjustTime() throws Exception {
    rtmpContinuityManager.startNewStream(1);
    assertThrows(IllegalArgumentException.class, () -> rtmpContinuityManager.adjustTimestamp(0));
  }

  @Test
  public void testNegtiveAdjustTime() throws Exception {
    rtmpContinuityManager.startNewStream(1);
    assertThrows(IllegalArgumentException.class, () -> rtmpContinuityManager.adjustTimestamp(-1));
  }

  @Test(expected = IllegalStateException.class)
  public void testAdjustTimeWithNoStart() throws Exception {
    rtmpContinuityManager.adjustTimestamp(1);
  }

  @Test
  public void testAdjustTimeZeroAdjustment() throws Exception {
    rtmpContinuityManager.startNewStream(10);

    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(1)).isLessThan(0);
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(10)).isEqualTo(0);
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(11)).isEqualTo(1);
    testAdjustment(1015 /* timestamp */, 1005 /* expectedAdj */, 1005 /* lastTimestamp */);
  }

  @Test
  public void testAdjustTimeZeroAdjustmentWithSavedTime() throws Exception {
    setLastTimestamp(TimeUnit.SECONDS.toMillis(15));
    rtmpContinuityManager.startNewStream(10);

    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(1)).isLessThan(0);
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(10)).isEqualTo(0);
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(11)).isEqualTo(1);
    testAdjustment(1015 /* timestamp */, 1005 /* expectedAdj */, 1005 /* lastTimestamp */);
  }

  @Test
  public void testAdjustTimeZeroAdjustmentWithSavedNegativeTime() throws Exception {
    setLastTimestamp(-1);
    rtmpContinuityManager.startNewStream(10);

    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(1)).isLessThan(0);
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(10)).isEqualTo(0);
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(11)).isEqualTo(1);
    testAdjustment(1015 /* timestamp */, 1005 /* expectedAdj */, 1005 /* lastTimestamp */);
  }

  @Test
  public void testAdjustTimePositiveAdjustment() throws Exception {
    setLastTimestamp(TimeUnit.SECONDS.toMillis(15) - 1);
    rtmpContinuityManager.startNewStream(10);

    long expectedOffset = TimeUnit.SECONDS.toMillis(45) - 1;
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(1)).isLessThan(0);
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(10)).isEqualTo(expectedOffset);
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(11)).isEqualTo(expectedOffset + 1);
    testAdjustment(
        1015 /* timestamp */,
        expectedOffset + 1005 /* expectedAdj */,
        expectedOffset /* lastTimestamp */);
  }

  @Test
  public void testAdjustTimeStoreTimestampsFromZero() throws Exception {
    rtmpContinuityManager.startNewStream(10);

    // Start from 0 with no value in prefs.
    testAdjustment(11 /* timestamp */, 1 /* expectedAdj */, 1 /* lastTimestamp */);
    testAdjustment(1009 /* timestamp */, 999 /* expectedAdj */, 1 /* lastTimestamp */);
    testAdjustment(2009 /* timestamp */, 1999 /* expectedAdj */, 1999 /* lastTimestamp */);
    testAdjustment(15009 /* timestamp */, 14999 /* expectedAdj */, 14999 /* lastTimestamp */);
    testAdjustment(15060 /* timestamp */, 15050 /* expectedAdj */, 14999 /* lastTimestamp */);
    testAdjustment(16060 /* timestamp */, 16050 /* expectedAdj */, 16050 /* lastTimestamp */);
    testAdjustment(56060 /* timestamp */, 56050 /* expectedAdj */, 16050 /* lastTimestamp */);

    // Went past threshold. Restart again from 0.
    rtmpContinuityManager.startNewStream(10);
    long lastTime = 14999;
    testAdjustment(11 /* timestamp */, 1 /* expectedAdj */, 1 /* lastTimestamp */);
    testAdjustment(1009 /* timestamp */, 999 /* expectedAdj */, 1 /* lastTimestamp */);
    testAdjustment(2009 /* timestamp */, 1999 /* expectedAdj */, 1999 /* lastTimestamp */);
    testAdjustment(lastTime + 10 /* timestamp */, lastTime, lastTime);
    testAdjustment(15060 /* timestamp */, 15050 /* expectedAdj */, lastTime);

    // Don't wait for last store to complete, which will cause a restart within the threshold.
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(56060)).isEqualTo(56050);

    // Create discontinuity from last stored timestamp
    rtmpContinuityManager.startNewStream(10);
    long expectedAdj = lastTime + TimeUnit.SECONDS.toMillis(30);
    testAdjustment(11 /* timestamp */, expectedAdj + 1, expectedAdj + 1);
    testAdjustment(1009 /* timestamp */, expectedAdj + 999, expectedAdj + 1);
    testAdjustment(15060 /* timestamp */, expectedAdj + 15050, expectedAdj + 1);
  }

  @Test
  public void testAdjustTimeStoreTimestampsWithDiscontinuity() throws Exception {
    // Start from discontinuity due to value in prefs below threshold.
    setLastTimestamp(TimeUnit.SECONDS.toMillis(5));
    long expectedAdj = TimeUnit.SECONDS.toMillis(35);
    rtmpContinuityManager.startNewStream(10);
    testAdjustment(1009 /* timestamp */, expectedAdj + 999, expectedAdj + 999);
    testAdjustment(15060 /* timestamp */, expectedAdj + 15050, expectedAdj + 999);
    testAdjustment(45060 /* timestamp */, expectedAdj + 45050, expectedAdj + 999);

    // Test start, adj, start, adj (from 0 and from non-zero)
    rtmpContinuityManager.startNewStream(100);
    testAdjustment(1009 /* timestamp */, 909 /* expectedAdj */, 909 /* lastTimestamp */);
    testAdjustment(14100 /* timestamp */, 14000 /* expectedAdj */, 14000 /* lastTimestamp */);
    testAdjustment(14200 /* timestamp */, 14100 /* expectedAdj */, 14000 /* lastTimestamp */);
    testAdjustment(14300 /* timestamp */, 14200 /* expectedAdj */, 14000 /* lastTimestamp */);
    testAdjustment(14400 /* timestamp */, 14300 /* expectedAdj */, 14000 /* lastTimestamp */);
    testAdjustment(15100 /* timestamp */, 15000 /* expectedAdj */, 15000 /* lastTimestamp */);
    testAdjustment(16100 /* timestamp */, 16000 /* expectedAdj */, 15000 /* lastTimestamp */);
    testAdjustment(155100 /* timestamp */, 155000 /* expectedAdj */, 15000 /* lastTimestamp */);
  }

  private void testAdjustment(
      long timestamp, long expectedAdjustedTimestamp, long expectedLastTimestamp) {
    Truth.assertThat(rtmpContinuityManager.adjustTimestamp(timestamp))
        .isEqualTo(expectedAdjustedTimestamp);
    shadowLooper.idle();
    Truth.assertThat(getLastTimestamp()).isEqualTo(expectedLastTimestamp);
  }

  private long getLastTimestamp() {
    return sharedPrefs.getLong(RtmpContinuityManager.KEY_LAST_TIMESTAMP, -1);
  }

  private void setLastTimestamp(long timestamp) {
    sharedPrefs.edit().putLong(RtmpContinuityManager.KEY_LAST_TIMESTAMP, timestamp).apply();
  }
}
