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

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Pair;
import com.google.common.truth.Truth;
import com.google.vr180.media.muxer.MediaMux;
import com.google.vr180.media.rtmp.Clock;
import com.google.vr180.media.video.VideoEncoder;
import com.google.vr180.testhelpers.shadows.ShadowPair;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = 28,
    shadows = {ShadowLooper.class, ShadowPair.class})
public class AbrControllerTest {
  @Mock MediaMux mockMediaMux;
  @Mock VideoEncoder videoEncoder;
  @Mock Clock mockClock;
  private ScheduledExecutorService schedulerExecutor;
  private ArgumentCaptor<Integer> videoBitrate = ArgumentCaptor.forClass(Integer.class);

  private static final int AUDIO_BITRATE = 128000;
  private static final int METADATA_BITRATE = 16000;

  private static final int MIN_BITRATE = 200000;
  private static final int MAX_BITRATE = 2000000;
  private static final int START_BITRATE = 1000000;

  private ShadowLooper shadowLooper;
  private Handler codecHandler;
  private AbrController abrController;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    HandlerThread thread = new HandlerThread("CodecThreadTest", Process.THREAD_PRIORITY_DEFAULT);
    thread.start();
    Looper looper = thread.getLooper();
    codecHandler = new Handler(looper);
    shadowLooper = (ShadowLooper) Shadow.extract(looper);
    schedulerExecutor = Executors.newSingleThreadScheduledExecutor();

    abrController =
        new AbrController(
            MIN_BITRATE,
            MAX_BITRATE,
            START_BITRATE,
            videoEncoder,
            mockMediaMux,
            schedulerExecutor,
            codecHandler,
            mockClock);
    abrController.setActive(true);
    // Disable the update done by the schedulerExecutor internally and runn all update manuall in
    // the tests.
    abrController.setActive(false);
  }

  @Test
  public void testHighQualityConnection_bitrateReachesMaxBitrate() throws Exception {
    Runnable updateRunnable = abrController.getUpdateRunnable();
    Truth.assertThat(updateRunnable).isNotNull();
    for (int i = 0; i < 1000; i++) {
      when(mockClock.elapsedMillis()).thenReturn(i * AbrController.SAMPLE_DELAY_MILLIS);
      when(mockMediaMux.getOutputBufferUsed()).thenReturn(0);
      when(mockMediaMux.getCurrentByteThroughput())
          .thenReturn(new Pair<>(MAX_BITRATE * 2 / 8, MAX_BITRATE * 2 / 8));
      updateRunnable.run();
      shadowLooper.idle(AbrController.SAMPLE_DELAY_MILLIS);
    }
    verify(videoEncoder, atLeast(1)).setTargetBitrate(videoBitrate.capture());
    List<Integer> values = videoBitrate.getAllValues();
    Truth.assertThat(values.get(values.size() - 1)).isEqualTo(MAX_BITRATE);
  }

  @Test
  public void testLowQualityConnection_bitrateReachesMinBitrate() throws Exception {
    Runnable updateRunnable = abrController.getUpdateRunnable();
    Truth.assertThat(updateRunnable).isNotNull();
    for (int i = 0; i < 20; i++) {
      when(mockClock.elapsedMillis()).thenReturn(i * AbrController.SAMPLE_DELAY_MILLIS);
      when(mockMediaMux.getOutputBufferUsed()).thenReturn(i);
      // Test delta increasing (isGrowth flag).
      when(mockMediaMux.getCurrentByteThroughput())
          .thenReturn(
              new Pair<>(
                  abrController.getSetTargetBitrate() / 8 /* inBytes */,
                  MIN_BITRATE / 8 /* outBytes */));
      updateRunnable.run();
      shadowLooper.idle(AbrController.SAMPLE_DELAY_MILLIS);
    }

    verify(videoEncoder, atLeast(1)).setTargetBitrate(videoBitrate.capture());
    List<Integer> values = videoBitrate.getAllValues();
    Truth.assertThat(values.get(values.size() - 1)).isEqualTo(MIN_BITRATE);
  }

  @Test
  public void testLowQualityConnection_bitrateReachesTargetAboveStart() throws Exception {
    int networkBitrate = 1500000;
    Runnable updateRunnable = abrController.getUpdateRunnable();
    Truth.assertThat(updateRunnable).isNotNull();
    int bufferUsed = 0;
    for (int i = 0; i < 100; i++) {
      when(mockClock.elapsedMillis()).thenReturn(i * AbrController.SAMPLE_DELAY_MILLIS);
      bufferUsed =
          Math.max(
              bufferUsed
                  + getSampleBits((abrController.getSetTargetBitrate() - networkBitrate) / 8),
              0);
      Truth.assertThat(bufferUsed).isLessThan(93750 /* 500 ms @ 1.5 Mbps */);
      when(mockMediaMux.getOutputBufferUsed()).thenReturn(bufferUsed);
      // Test delta increasing (isGrowth flag).
      when(mockMediaMux.getCurrentByteThroughput())
          .thenReturn(
              new Pair<>(
                  getSampleBits(abrController.getSetTargetBitrate()) / 8 /* inBytes */,
                  getSampleBits(networkBitrate) / 8 /* outBytes */));
      updateRunnable.run();
      shadowLooper.idle(AbrController.SAMPLE_DELAY_MILLIS);
    }

    verify(videoEncoder, atLeast(1)).setTargetBitrate(videoBitrate.capture());
    List<Integer> values = videoBitrate.getAllValues();
    // Overall range should stay within 500 Kbps.
    for (Integer value : values) {
      Truth.assertThat(value).isAtLeast(Math.min(networkBitrate, START_BITRATE) - 500000);
      Truth.assertThat(value).isLessThan(Math.max(networkBitrate, START_BITRATE) + 500000);
    }

    Truth.assertThat((double) values.get(values.size() - 1))
        .isWithin(networkBitrate * 0.25)
        .of((double) networkBitrate);
  }

  @Test
  public void testLowQualityConnection_bitrateReachesTargetBelowStart() throws Exception {
    int networkBitrate = 500000;
    Runnable updateRunnable = abrController.getUpdateRunnable();
    Truth.assertThat(updateRunnable).isNotNull();
    int bufferUsed = 0;
    for (int i = 0; i < 100; i++) {
      when(mockClock.elapsedMillis()).thenReturn(i * AbrController.SAMPLE_DELAY_MILLIS);
      bufferUsed =
          Math.max(
              bufferUsed
                  + getSampleBits((abrController.getSetTargetBitrate() - networkBitrate) / 8),
              0);
      Truth.assertThat(bufferUsed).isLessThan(93750 /* 500 ms @ 1.5 Mbps */);
      when(mockMediaMux.getOutputBufferUsed()).thenReturn(bufferUsed);
      // Test delta increasing (isGrowth flag).
      when(mockMediaMux.getCurrentByteThroughput())
          .thenReturn(
              new Pair<>(
                  getSampleBits(abrController.getSetTargetBitrate()) / 8 /* inBytes */,
                  getSampleBits(networkBitrate) / 8 /* outBytes */));
      updateRunnable.run();
      shadowLooper.idle(AbrController.SAMPLE_DELAY_MILLIS);
    }

    verify(videoEncoder, atLeast(1)).setTargetBitrate(videoBitrate.capture());
    List<Integer> values = videoBitrate.getAllValues();
    // Overall range should stay within 500 Kbps.
    for (Integer value : values) {
      Truth.assertThat(value).isAtLeast(Math.min(networkBitrate, START_BITRATE) - 500000);
      Truth.assertThat(value).isLessThan(Math.max(networkBitrate, START_BITRATE) + 500000);
    }

    Truth.assertThat((double) values.get(values.size() - 1))
        .isWithin(networkBitrate * 0.25)
        .of((double) networkBitrate);
  }

  @Test
  public void testLowQualityConnection_noNetworkPulse_stabilizes() throws Exception {
    int networkBitrate = 1500000;
    Runnable updateRunnable = abrController.getUpdateRunnable();
    Truth.assertThat(updateRunnable).isNotNull();
    int bufferUsed = 0;
    for (int i = 0; i < 100; i++) {

      int nowNetworkBitrate;
      if (i == 50) {
        nowNetworkBitrate = 0;
      } else {
        nowNetworkBitrate = networkBitrate;
      }
      when(mockClock.elapsedMillis()).thenReturn(i * AbrController.SAMPLE_DELAY_MILLIS);
      bufferUsed =
          Math.max(
              bufferUsed
                  + getSampleBits((abrController.getSetTargetBitrate() - nowNetworkBitrate) / 8),
              0);
      Truth.assertThat(bufferUsed).isLessThan(93750 /* 500 ms @ 1.5 Mbps */);
      when(mockMediaMux.getOutputBufferUsed()).thenReturn(bufferUsed);
      // Test delta increasing (isGrowth flag).
      when(mockMediaMux.getCurrentByteThroughput())
          .thenReturn(
              new Pair<>(
                  getSampleBits(abrController.getSetTargetBitrate()) / 8 /* inBytes */,
                  getSampleBits(nowNetworkBitrate) / 8 /* outBytes */));
      updateRunnable.run();
      shadowLooper.idle(AbrController.SAMPLE_DELAY_MILLIS);
    }

    verify(videoEncoder, atLeast(1)).setTargetBitrate(videoBitrate.capture());
    List<Integer> values = videoBitrate.getAllValues();
    // Overall range should stay within 500 Kbps.
    for (Integer value : values) {
      Truth.assertThat(value).isAtLeast(Math.min(networkBitrate, START_BITRATE) - 500000);
      Truth.assertThat(value).isLessThan(Math.max(networkBitrate, START_BITRATE) + 500000);
    }

    Truth.assertThat((double) values.get(values.size() - 1))
        .isWithin(networkBitrate * 0.25)
        .of((double) networkBitrate);
  }

  private int getSampleBits(int bitrate) {
    return (int) ((double) bitrate * ((double) AbrController.SAMPLE_DELAY_MILLIS / 1000.0));
  }

  @Test
  public void testBufferNoisyHighQualityConnection_bitrateReachesMaxBitrate() throws Exception {
    Runnable updateRunnable = abrController.getUpdateRunnable();
    Truth.assertThat(updateRunnable).isNotNull();
    for (int i = 0; i < 1000; i++) {
      when(mockClock.elapsedMillis()).thenReturn(i * AbrController.SAMPLE_DELAY_MILLIS);
      when(mockMediaMux.getOutputBufferUsed()).thenReturn(1500 + (int) (getNonRandomValue(i) * 10));
      when(mockMediaMux.getCurrentByteThroughput())
          .thenReturn(new Pair<>(2500000 / 8, 2500000 / 8));
      updateRunnable.run();
      shadowLooper.idle(AbrController.SAMPLE_DELAY_MILLIS);
    }
    verify(videoEncoder, atLeast(1)).setTargetBitrate(videoBitrate.capture());
    List<Integer> values = videoBitrate.getAllValues();
    Truth.assertThat(values.get(values.size() - 1)).isEqualTo(MAX_BITRATE);
  }

  @Test
  public void testCalcOutputBufferLimit() {
    int bufferLimitSeconds = 4;
    int bufferUsedMillis = 1000;
    int bufferUsedBytes = 100000;
    int videoBitrate = 250000;
    int outputLimit =
        abrController.calcOutputBufferLimit(bufferUsedMillis, bufferUsedBytes, videoBitrate);
    Truth.assertThat(outputLimit)
        .isEqualTo(
            ((videoBitrate + AUDIO_BITRATE + METADATA_BITRATE)
                        * (bufferLimitSeconds - bufferUsedMillis / 1000))
                    / 8
                + bufferUsedBytes);
  }

  @Test
  public void testCalcOutputBufferLimit_usedBufferGreaterThanLimit() {
    int bufferLimitSeconds = 4;
    int bufferUsedMillis = 5000;
    int bufferUsedBytes = 1000;
    int videoBitrate = 250000;
    int outputLimit =
        abrController.calcOutputBufferLimit(bufferUsedMillis, bufferUsedBytes, videoBitrate);
    Truth.assertThat(outputLimit)
        .isEqualTo((int) ((double) bufferLimitSeconds * 1000 / bufferUsedMillis * bufferUsedBytes));
  }

  @Test
  public void testCalcOutputBufferLimit_emptyBuffer() {
    int bufferLimitSeconds = 4;
    int bufferUsedMillis = 0;
    int bufferUsedBytes = 0;
    int videoBitrate = 250000;
    int outputLimit =
        abrController.calcOutputBufferLimit(bufferUsedMillis, bufferUsedBytes, videoBitrate);
    Truth.assertThat(outputLimit)
        .isEqualTo(
            ((videoBitrate + AUDIO_BITRATE + METADATA_BITRATE)
                        * (bufferLimitSeconds - bufferUsedMillis / 1000))
                    / 8 /* seconds */
                + bufferUsedBytes);
  }

  @Test
  public void testEstimateUsedBufferMillis() {
    // Test zero state.
    int previousBufferMillis = 0;
    int previousBufferBytes = 0;
    int currentUnusedBuffer = 0;
    int currentBytesPerSecond = 0;
    int inBytes = 0;
    int millis =
        abrController.estimateUsedBufferMillis(
            previousBufferMillis,
            previousBufferBytes,
            currentUnusedBuffer,
            currentBytesPerSecond,
            inBytes);
    Truth.assertThat(millis).isEqualTo(0);

    // Test normal state.
    previousBufferMillis = 0;
    previousBufferBytes = 0;
    currentUnusedBuffer = 0;
    currentBytesPerSecond = 250000;
    inBytes = 1000000;
    millis =
        abrController.estimateUsedBufferMillis(
            previousBufferMillis,
            previousBufferBytes,
            currentUnusedBuffer,
            currentBytesPerSecond,
            inBytes);
    Truth.assertThat(millis).isEqualTo(0);

    // First fill of buffer, no outgoing bytes.
    previousBufferMillis = 0;
    previousBufferBytes = 0;
    currentUnusedBuffer = 125000;
    currentBytesPerSecond = 125000;
    inBytes = 125000;
    millis =
        abrController.estimateUsedBufferMillis(
            previousBufferMillis,
            previousBufferBytes,
            currentUnusedBuffer,
            currentBytesPerSecond,
            inBytes);
    Truth.assertThat(millis).isEqualTo(1000);

    // Buffer growing one from previous + three from new = 4 seconds.
    previousBufferMillis = 1000;
    previousBufferBytes = 1000000;
    currentUnusedBuffer = 2500000;
    currentBytesPerSecond = 500000;
    inBytes = 1500000;
    millis =
        abrController.estimateUsedBufferMillis(
            previousBufferMillis,
            previousBufferBytes,
            currentUnusedBuffer,
            currentBytesPerSecond,
            inBytes);
    Truth.assertThat(millis).isEqualTo(4000);

    // One second from previous + one second new = 2 seconds.
    previousBufferMillis = 2000;
    previousBufferBytes = 125000;
    currentUnusedBuffer = 62500 + 25000;
    currentBytesPerSecond = 25000;
    inBytes = 25000;
    millis =
        abrController.estimateUsedBufferMillis(
            previousBufferMillis,
            previousBufferBytes,
            currentUnusedBuffer,
            currentBytesPerSecond,
            inBytes);
    Truth.assertThat(millis).isEqualTo(2000);

    // None added, some removed.
    previousBufferMillis = 5000;
    previousBufferBytes = 375000;
    currentUnusedBuffer = 250000;
    currentBytesPerSecond = 0;
    inBytes = 0;
    millis =
        abrController.estimateUsedBufferMillis(
            previousBufferMillis,
            previousBufferBytes,
            currentUnusedBuffer,
            currentBytesPerSecond,
            inBytes);
    Truth.assertThat(millis).isEqualTo(3333);

    // Empty Buffer.
    previousBufferMillis = 65432;
    previousBufferBytes = 54321;
    currentUnusedBuffer = 0;
    currentBytesPerSecond = 12345;
    inBytes = 23456;
    millis =
        abrController.estimateUsedBufferMillis(
            previousBufferMillis,
            previousBufferBytes,
            currentUnusedBuffer,
            currentBytesPerSecond,
            inBytes);
    Truth.assertThat(millis).isEqualTo(0);

    // Basic real use case.
    previousBufferMillis = 0;
    previousBufferBytes = 0;
    currentUnusedBuffer = 23872;
    currentBytesPerSecond = (1855000 / 8);
    inBytes = 47071;
    millis =
        abrController.estimateUsedBufferMillis(
            previousBufferMillis,
            previousBufferBytes,
            currentUnusedBuffer,
            currentBytesPerSecond,
            inBytes);
    Truth.assertThat(millis).isEqualTo(102);
  }

  /**
   * Generate a somewhat pseudorandom data set which repeats every 10 values and is slightly
   * modified to have case 2-4 increasing in value.
   */
  private double getNonRandomValue(int i) {
    switch (i % 10) {
      case 0:
        return 0.1;
      case 1:
        return 0.3;
      case 2:
        return -0.1;
      case 3:
        return 0.2;
      case 4:
        return 0.4;
      case 5:
        return -0.3;
      case 6:
        return 0.2;
      case 7:
        return -0.2;
      case 8:
        return 0.0;
      default:
        return -0.4;
    }
  }
}
