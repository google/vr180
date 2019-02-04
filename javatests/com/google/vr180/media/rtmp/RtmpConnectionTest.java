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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.net.Uri;
import com.google.common.truth.Truth;
import com.google.vr180.media.rtmp.RtmpInputStream.TransactionResult;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Test for {@link RtmpConnection} */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class RtmpConnectionTest {

  @Mock SocketChannel mockSocketChannel;
  @Mock Socket mockSocket;
  @Mock RtmpInputStream mockInStream;
  @Mock RtmpOutputStream mockOutStream;
  @Mock Clock mockMediaClock;

  private final Uri targetUri = Uri.parse("rtmp://test.com:1935");
  private final String streamKey = "streamKey";
  private final MediaFormat avc =
      MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 854, 480);
  private final MediaFormat aac =
      MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1);

  private RtmpConnection rtmpConnection;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    when(mockSocketChannel.connect(any(SocketAddress.class))).thenReturn(true);
    when(mockSocketChannel.socket()).thenReturn(mockSocket);

    rtmpConnection =
        new RtmpConnection(activity, "host", 100 /* port */, mockMediaClock, mockSocketChannel);

    rtmpConnection.setInStream(mockInStream);
    rtmpConnection.setOutStream(mockOutStream);
  }

  @Test
  public void testSetVideo() throws Exception {
    MediaFormat vp9 = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_VP9, 854, 480);
    MediaFormat mpeg4 = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_MPEG4, 854, 480);

    Truth.assertThat(rtmpConnection.setVideoType(avc)).isTrue();
    Truth.assertThat(rtmpConnection.setVideoType(vp9)).isFalse();
    Truth.assertThat(rtmpConnection.setVideoType(mpeg4)).isFalse();
    verifyZeroInteractions(mockOutStream);
    verifyZeroInteractions(mockInStream);
  }

  @Test
  public void testSetAudio() throws Exception {
    MediaFormat ac3 = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AC3, 44100, 1);
    MediaFormat mpeg = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_MPEG, 44100, 1);

    Truth.assertThat(rtmpConnection.setAudioType(aac)).isTrue();
    Truth.assertThat(rtmpConnection.setAudioType(ac3)).isFalse();
    Truth.assertThat(rtmpConnection.setAudioType(mpeg)).isFalse();
    verifyZeroInteractions(mockOutStream);
    verifyZeroInteractions(mockInStream);
  }

  @Test
  public void testConnectAlreadyConnected() throws Exception {
    rtmpConnection.setIsConnected(true);
    rtmpConnection.connect();
    verifyZeroInteractions(mockOutStream);
    verifyZeroInteractions(mockInStream);
  }

  @Test
  public void testPublishNotConnected() {
    rtmpConnection.setIsConnected(false);
    assertThrows(IllegalStateException.class, () -> rtmpConnection.publish(targetUri, streamKey));
  }

  @Test
  public void testPublishAlreadyPublished() throws Exception {
    rtmpConnection.setIsConnected(true);
    rtmpConnection.setIsPublihed(true);
    rtmpConnection.publish(targetUri, streamKey);
    verifyZeroInteractions(mockInStream);
    verifyZeroInteractions(mockOutStream);
  }

  @Test
  public void testPublishNoAudio() {
    rtmpConnection.setIsConnected(true);
    rtmpConnection.setIsPublihed(false);
    rtmpConnection.setVideoType(avc);
    assertThrows(IllegalStateException.class, () -> rtmpConnection.publish(targetUri, streamKey));
  }

  @Test
  public void testPublishNoVideo() {
    rtmpConnection.setIsConnected(true);
    rtmpConnection.setIsPublihed(false);
    rtmpConnection.setAudioType(aac);
    assertThrows(IllegalStateException.class, () -> rtmpConnection.publish(targetUri, streamKey));
  }

  @Test
  public void testPublish() throws Exception {
    // TODO: test the sendBufferSize.
    when(mockInStream.createTransaction(anyInt()))
        .thenReturn(
            new Future<TransactionResult>() {
              int iteration;

              @Override
              public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
              }

              @Override
              public boolean isCancelled() {
                return false;
              }

              @Override
              public boolean isDone() {
                return true;
              }

              @Override
              public TransactionResult get() throws InterruptedException, ExecutionException {
                return null;
              }

              @Override
              public TransactionResult get(long timeout, TimeUnit unit)
                  throws InterruptedException, ExecutionException, TimeoutException {
                TransactionResult result = new TransactionResult();
                result.setStatus(TransactionResult.STATUS_SUCCESS);
                switch (iteration) {
                  case 0:
                    result.setStatusMessage(RtmpMessage.AMF_NETCONNECTION_STATUS_SUCCESS);
                    break;
                  case 2:
                    result.setStatusMessage(RtmpMessage.AMF_PUBLISH_STATUS_SUCCESS);
                    break;
                  default:
                    break;
                }
                iteration++;
                return result;
              }
            });
    rtmpConnection.setIsConnected(true);
    rtmpConnection.setIsPublihed(false);
    rtmpConnection.setAudioType(aac);
    rtmpConnection.setVideoType(avc);
    rtmpConnection.publish(targetUri, streamKey);

    verify(mockSocket, never()).setSendBufferSize(eq(2 * RtmpConnection.OUTGOING_CHUNK_SIZE));
    verify(mockSocket, never()).setSoTimeout(anyInt());
    verify(mockOutStream).sendSetChunkSize(anyInt());
    verify(mockOutStream).setWindowSize(anyInt(), eq(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD));
    verify(mockOutStream).sendConnect(eq(targetUri), eq(streamKey), anyInt());
    verify(mockInStream, times(3)).clearTransaction(anyInt());
    verify(mockOutStream).sendReleaseStream(eq(streamKey), anyInt());
    verify(mockOutStream).sendCreateStream(anyInt());
    verify(mockOutStream).sendPublish(eq(streamKey), anyInt());
    verify(mockOutStream)
        .sendStreamMetaData(
            RtmpMessage.RTMP_AUDIO_CODEC_AAC, aac, RtmpMessage.RTMP_VIDEO_CODEC_AVC, avc);

    Truth.assertThat(rtmpConnection.isPublished()).isTrue();
  }

  @Test
  public void testSendDataNotPublished() {
    rtmpConnection.setIsConnected(true);
    rtmpConnection.setIsPublihed(false);
    assertThrows(
        IllegalStateException.class,
        () ->
            rtmpConnection.sendSampleData(
                true /* isAudio */, ByteBuffer.allocate(1), new BufferInfo()));
  }

  @Test
  public void testSendData() throws Exception {
    rtmpConnection.setIsConnected(true);
    rtmpConnection.setIsPublihed(true);
    rtmpConnection.setAudioType(aac);
    rtmpConnection.setVideoType(avc);
    boolean isAudio = true;
    ByteBuffer buffer = ByteBuffer.allocate(1);
    BufferInfo bufferInfo = new BufferInfo();
    rtmpConnection.sendSampleData(isAudio, buffer, bufferInfo);
    verify(mockOutStream)
        .sendSampleData(
            isAudio,
            RtmpMessage.RTMP_AUDIO_CODEC_AAC,
            aac,
            RtmpMessage.RTMP_VIDEO_CODEC_AVC,
            avc,
            buffer,
            bufferInfo);
    verifyZeroInteractions(mockInStream);
    verify(mockOutStream, never()).flush();
  }

  @Test
  public void testDisconnectNotConnected() throws Exception {
    rtmpConnection.setIsConnected(false);
    rtmpConnection.disconnect();
    verifyZeroInteractions(mockOutStream);
    verifyZeroInteractions(mockInStream);
  }

  @Test
  public void testOnRtmpInputStreamPeerAcknowledgement() throws Exception {
    rtmpConnection.onRtmpInputStreamPeerAcknowledgement(12);
    verify(mockOutStream).setBytesAcknowledged(12);
    verifyZeroInteractions(mockInStream);
  }

  @Test
  public void testOnRtmpInputStreamAcknowledgementNeeded() throws Exception {
    rtmpConnection.onRtmpInputStreamAcknowledgementNeeded(12);
    verify(mockOutStream).sendAcknowledgement(12);
    verify(mockInStream).setBytesAcknowledged(12);
  }

  @Test
  public void testOnRtmpInputStreamWindowSizeRequested() throws Exception {
    rtmpConnection.onRtmpInputStreamWindowSizeRequested(12, 11);
    verify(mockOutStream).setWindowSize(12, 11);
    verifyZeroInteractions(mockInStream);
  }
}
