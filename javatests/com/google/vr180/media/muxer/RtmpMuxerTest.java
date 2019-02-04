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

package com.google.vr180.media.muxer;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.net.Uri;
import com.google.common.truth.Truth;
import com.google.vr180.media.MediaConstants;
import com.google.vr180.media.MediaEncoder;
import com.google.vr180.media.rtmp.RtmpConnection;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Test for {@link RtmpMuxer} */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RtmpMuxerTest {
  @Mock RtmpConnection mockRtmpConnection;
  @Mock MediaEncoder mockVideoEncoder;
  @Mock MediaEncoder mockAudioEncoder;

  private final MediaFormat videoFormat =
      MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 854, 480);
  private final MediaFormat audioFormat =
      MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1);
  private final Uri targetUri = Uri.parse("rtmp://test.com:1935");
  private final String streamKey = "streamKey";

  private RtmpMuxer rtmpMuxer;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(mockRtmpConnection.setAudioType(any(MediaFormat.class))).thenReturn(true);
    when(mockRtmpConnection.setVideoType(any(MediaFormat.class))).thenReturn(true);
    rtmpMuxer = new RtmpMuxer(targetUri, streamKey, mockRtmpConnection);
  }

  @Test
  public void testAddTrackStarted() throws Exception {
    rtmpMuxer.setIsStarted(true);
    Truth.assertThat(
            rtmpMuxer.addTrack(
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 854, 480),
                mockVideoEncoder))
        .isLessThan(0);
  }

  @Test
  public void testAddTrackStopped() throws Exception {
    rtmpMuxer.setIsStopped(true);
    Truth.assertThat(
            rtmpMuxer.addTrack(
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 854, 480),
                mockVideoEncoder))
        .isLessThan(0);
  }

  @Test
  public void testAddTrackReleased() throws Exception {
    rtmpMuxer.setIsReleased(true);
    Truth.assertThat(
            rtmpMuxer.addTrack(
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 854, 480),
                mockVideoEncoder))
        .isLessThan(0);
  }

  @Test
  public void testAddTrackBad() throws Exception {
    Truth.assertThat(rtmpMuxer.addTrack(null, null)).isLessThan(0);
  }

  @Test
  public void testAddTrackAudioVideo() throws Exception {
    Truth.assertThat(rtmpMuxer.hasAllTracks()).isFalse();

    int audioTrack = rtmpMuxer.addTrack(audioFormat, mockAudioEncoder);
    Truth.assertThat(audioTrack).isAtLeast(0);
    Truth.assertThat(rtmpMuxer.addTrack(audioFormat, mockAudioEncoder)).isLessThan(0);
    Truth.assertThat(rtmpMuxer.hasAllTracks()).isFalse();

    int videoTrack = rtmpMuxer.addTrack(videoFormat, mockVideoEncoder);
    Truth.assertThat(videoTrack).isAtLeast(0);
    Truth.assertThat(videoTrack).isNotEqualTo(audioTrack);
    Truth.assertThat(rtmpMuxer.addTrack(videoFormat, mockVideoEncoder)).isLessThan(0);
    Truth.assertThat(rtmpMuxer.hasAllTracks()).isTrue();
  }

  @Test
  public void testAddTrackVideoAudio() throws Exception {
    Truth.assertThat(rtmpMuxer.hasAllTracks()).isFalse();

    int videoTrack = rtmpMuxer.addTrack(videoFormat, mockVideoEncoder);
    Truth.assertThat(videoTrack).isAtLeast(0);
    Truth.assertThat(rtmpMuxer.addTrack(videoFormat, mockVideoEncoder)).isLessThan(0);
    Truth.assertThat(rtmpMuxer.hasAllTracks()).isFalse();

    int audioTrack = rtmpMuxer.addTrack(audioFormat, mockAudioEncoder);
    Truth.assertThat(audioTrack).isAtLeast(0);
    Truth.assertThat(videoTrack).isNotEqualTo(audioTrack);
    Truth.assertThat(rtmpMuxer.addTrack(audioFormat, mockAudioEncoder)).isLessThan(0);
    Truth.assertThat(rtmpMuxer.hasAllTracks()).isTrue();
  }

  @Test
  public void testRelease() throws Exception {
    Truth.assertThat(rtmpMuxer.release()).isTrue();
    Truth.assertThat(rtmpMuxer.release()).isTrue();
    Truth.assertThat(rtmpMuxer.release()).isTrue();
    verify(mockRtmpConnection).release();
  }

  @Test
  public void testPrepareReleased() throws Exception {
    rtmpMuxer.setIsReleased(true);
    Truth.assertThat(rtmpMuxer.prepare()).isEqualTo(MediaConstants.STATUS_ERROR);
    verify(mockRtmpConnection, never()).connect();
  }

  @Test
  public void testPrepareStopped() throws Exception {
    rtmpMuxer.setIsStopped(true);
    Truth.assertThat(rtmpMuxer.prepare()).isEqualTo(MediaConstants.STATUS_ERROR);
    verify(mockRtmpConnection, never()).connect();
  }

  @Test
  public void testPrepareStarted() throws Exception {
    rtmpMuxer.setIsStarted(true);
    Truth.assertThat(rtmpMuxer.prepare()).isEqualTo(MediaConstants.STATUS_ERROR);
    verify(mockRtmpConnection, never()).connect();
  }

  @Test
  public void testPrepare() throws Exception {
    rtmpMuxer.prepare();
    rtmpMuxer.prepare();
    rtmpMuxer.prepare();
    verify(mockRtmpConnection).connect();
  }

  @Test
  public void testPrepareTimeout() throws Exception {
    doThrow(TimeoutException.class).when(mockRtmpConnection).connect();
    Truth.assertThat(rtmpMuxer.prepare()).isEqualTo(MediaConstants.STATUS_TIMED_OUT);
    Truth.assertThat(rtmpMuxer.prepare()).isEqualTo(MediaConstants.STATUS_TIMED_OUT);
    verify(mockRtmpConnection, times(2)).connect();
  }

  @Test
  public void testPrepareIoError() throws Exception {
    doThrow(IOException.class).when(mockRtmpConnection).connect();
    Truth.assertThat(rtmpMuxer.prepare()).isEqualTo(MediaConstants.STATUS_COMMUNICATION_ERROR);
    Truth.assertThat(rtmpMuxer.prepare()).isEqualTo(MediaConstants.STATUS_COMMUNICATION_ERROR);
    verify(mockRtmpConnection, times(2)).connect();
  }

  @Test
  public void testStartReleased() throws Exception {
    rtmpMuxer.setIsReleased(true);
    Truth.assertThat(rtmpMuxer.start()).isFalse();
    verify(mockRtmpConnection, never()).publish(any(Uri.class), any(String.class));
  }

  @Test
  public void testStartStopped() throws Exception {
    rtmpMuxer.setIsStopped(true);
    Truth.assertThat(rtmpMuxer.start()).isFalse();
    verify(mockRtmpConnection, never()).publish(any(Uri.class), any(String.class));
  }

  @Test
  public void testStartNotPrepared() throws Exception {
    rtmpMuxer.setIsPrepared(false);
    Truth.assertThat(rtmpMuxer.start()).isFalse();
    verify(mockRtmpConnection, never()).publish(any(Uri.class), any(String.class));
  }

  @Test
  public void testStartNotAllTracks() throws Exception {
    rtmpMuxer.setIsPrepared(true);
    Truth.assertThat(rtmpMuxer.start()).isFalse();
    verify(mockRtmpConnection, never()).publish(any(Uri.class), any(String.class));
  }

  @Test
  public void testStart() throws Exception {
    rtmpMuxer.setIsPrepared(true);
    rtmpMuxer.setAudioTrack(1, mockAudioEncoder);
    rtmpMuxer.setVideoTrack(2, mockVideoEncoder);
    Truth.assertThat(rtmpMuxer.start()).isTrue();
    Truth.assertThat(rtmpMuxer.start()).isTrue();
    Truth.assertThat(rtmpMuxer.start()).isTrue();
    verify(mockRtmpConnection).publish(targetUri, streamKey);
  }

  @Test
  public void testStartException() throws Exception {
    rtmpMuxer.setIsPrepared(true);
    rtmpMuxer.setAudioTrack(1, mockAudioEncoder);
    rtmpMuxer.setVideoTrack(2, mockVideoEncoder);
    doThrow(IOException.class).when(mockRtmpConnection).publish(any(Uri.class), any(String.class));
    Truth.assertThat(rtmpMuxer.start()).isFalse();
    Truth.assertThat(rtmpMuxer.start()).isFalse();
    verify(mockRtmpConnection, times(2)).publish(targetUri, streamKey);
  }

  @Test
  public void testStopReleased() throws Exception {
    rtmpMuxer.setIsReleased(true);
    Truth.assertThat(rtmpMuxer.stop()).isFalse();
    verify(mockRtmpConnection, never()).disconnect();
  }

  @Test
  public void testStopNotStarted() throws Exception {
    rtmpMuxer.setIsStarted(false);
    Truth.assertThat(rtmpMuxer.stop()).isFalse();
    verify(mockRtmpConnection, never()).disconnect();
  }

  @Test
  public void testStop() throws Exception {
    rtmpMuxer.setIsStarted(true);
    Truth.assertThat(rtmpMuxer.stop()).isTrue();
    Truth.assertThat(rtmpMuxer.stop()).isTrue();
    Truth.assertThat(rtmpMuxer.stop()).isTrue();
    verify(mockRtmpConnection).disconnect();
  }

  @Test
  public void testStopException() throws Exception {
    rtmpMuxer.setIsStarted(true);
    doThrow(IOException.class).when(mockRtmpConnection).disconnect();
    Truth.assertThat(rtmpMuxer.stop()).isFalse();
    Truth.assertThat(rtmpMuxer.stop()).isFalse();
    verify(mockRtmpConnection, times(2)).disconnect();
  }

  @Test
  public void testWriteReleased() throws Exception {
    rtmpMuxer.setIsReleased(true);
    Truth.assertThat(rtmpMuxer.writeSampleDataAsync(1, 0, null)).isFalse();
    verify(mockRtmpConnection, never())
        .sendSampleData(anyBoolean(), any(ByteBuffer.class), any(BufferInfo.class));
  }

  @Test
  public void testWriteStopped() throws Exception {
    rtmpMuxer.setIsStopped(true);
    Truth.assertThat(rtmpMuxer.writeSampleDataAsync(1, 0, null)).isFalse();
    verify(mockRtmpConnection, never())
        .sendSampleData(anyBoolean(), any(ByteBuffer.class), any(BufferInfo.class));
  }

  @Test
  public void testWriteNotStarted() throws Exception {
    rtmpMuxer.setIsStarted(false);
    Truth.assertThat(rtmpMuxer.writeSampleDataAsync(1, 0, null)).isFalse();
    verify(mockRtmpConnection, never())
        .sendSampleData(anyBoolean(), any(ByteBuffer.class), any(BufferInfo.class));
  }

  @Test
  public void testWrite() throws Exception {
    int audioTrack = 1;
    rtmpMuxer.setIsStarted(true);
    rtmpMuxer.setAudioTrack(audioTrack, mockAudioEncoder);
    ByteBuffer buffer = ByteBuffer.allocate(1);
    when(mockAudioEncoder.getOutputBuffer(eq(0))).thenReturn(buffer);
    BufferInfo bufferInfo = new BufferInfo();
    Truth.assertThat(rtmpMuxer.writeSampleDataAsync(audioTrack, 0, bufferInfo)).isTrue();
    verify(mockRtmpConnection).sendSampleData(true /* isAudio */, buffer, bufferInfo);
    verify(mockAudioEncoder).releaseOutputBuffer(0);
  }
}
