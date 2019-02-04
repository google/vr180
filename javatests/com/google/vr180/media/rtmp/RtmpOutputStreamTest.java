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
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import com.google.common.truth.Truth;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/** Test for {@link RtmpOutputStream} */
@RunWith(RobolectricTestRunner.class)
public class RtmpOutputStreamTest {
  @Mock SocketChannel mockSocketChannel;
  @Mock Socket mockSocket;
  @Mock Clock mockMediaClock;
  @Mock TimestampContinuityManager mockContinuityManager;

  private final ByteArrayOutputStream socketOutput = new ByteArrayOutputStream();
  private RtmpOutputStream rtmpOutputStream;
  private ByteBufferCaptor byteBufferCaptor;
  private String versionString;

  /** Captor that clones the ByteBuffer argument to a socket write operation. */
  private static class ByteBufferCaptor implements Answer<Integer> {
    private ArrayList<ByteBuffer> capturedArgs = new ArrayList<>();
    private int writeLimit;
    private boolean useWriteLimit;

    ByteBuffer getNextCapturedBuffer() {
      if (capturedArgs.isEmpty()) {
        return null;
      }
      return capturedArgs.remove(0);
    }

    void setWriteLimit(int maxBytesToWrite) {
      this.writeLimit = maxBytesToWrite;
      useWriteLimit = true;
    }

    void clearWriteLimit() {
      useWriteLimit = false;
    }

    @Override
    public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
      ByteBuffer arg = (ByteBuffer) invocationOnMock.getArguments()[0];
      byte[] argBytes = arg.array();
      ByteBuffer clone = ByteBuffer.wrap(Arrays.copyOf(argBytes, argBytes.length));
      int written = arg.limit() - arg.position();
      if (useWriteLimit && written > writeLimit) {
        written = writeLimit;
      }
      clone.position(arg.position());
      clone.limit(arg.position() + written);
      capturedArgs.add(clone);
      arg.position(arg.position() + written);
      return written;
    }
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    when(mockSocketChannel.socket()).thenReturn(mockSocket);
    when(mockSocketChannel.isConnected()).thenReturn(true);
    when(mockSocket.getOutputStream()).thenReturn(socketOutput);
    byteBufferCaptor = new ByteBufferCaptor();
    when(mockSocketChannel.write(any(ByteBuffer.class))).thenAnswer(byteBufferCaptor);

    when(mockMediaClock.getCurrentTimeMillis())
        .thenAnswer(
            new Answer<Long>() {
              long timestamp;

              @Override
              public Long answer(InvocationOnMock invocationOnMock) throws Throwable {
                timestamp += 11;
                return timestamp;
              }
            });
    when(mockContinuityManager.adjustTimestamp(anyLong()))
        .thenAnswer(
            new Answer<Long>() {
              long timestamp;

              @Override
              public Long answer(InvocationOnMock invocationOnMock) throws Throwable {
                long now = timestamp;
                timestamp += 13;
                return now;
              }
            });
    rtmpOutputStream =
        new RtmpOutputStream(
            activity,
            mockSocketChannel,
            mockContinuityManager,
            null /* pipedOutput */,
            null /* pipedInput */);

    Uri.Builder builder = new Uri.Builder();
    builder
        .appendQueryParameter("manufacturer", Build.MANUFACTURER) // For example: LGE
        .appendQueryParameter("model", Build.MODEL) // For example: Nexus 5X
        .appendQueryParameter("osVersion", VERSION.RELEASE) // For example: 6.01
        .appendQueryParameter("protocol", "RTMP"); // For example: RTMP
    String extraData = "extras?" + builder.build().getQuery();
    versionString = activity.getPackageName() + ":1.0.0:" + extraData;
  }

  @Test
  public void testHandshake0() throws Exception {
    rtmpOutputStream.sendClientHandshake0();

    verify(mockSocketChannel).write(any(ByteBuffer.class));
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();

    ByteBuffer expected = ByteBuffer.wrap(new byte[] {RtmpMessage.RTMP_VERSION});
    ByteBuffer unexpected = ByteBuffer.wrap(new byte[] {RtmpMessage.RTMP_VERSION + 1});
    Truth.assertThat(capturedBuffer).isEqualTo(expected);
    Truth.assertThat(capturedBuffer).isNotEqualTo(unexpected);
  }

  @Test
  public void testHandshake1BadChallenge() {
    byte[] challengeBytes = new byte[4];
    assertThrows(
        IllegalArgumentException.class,
        () -> rtmpOutputStream.sendClientHandshake1(challengeBytes));
  }

  @Test
  public void testHandshake1NullChallenge() {
    assertThrows(NullPointerException.class, () -> rtmpOutputStream.sendClientHandshake1(null));
  }

  @Test
  public void testHandshake1() throws Exception {
    byte[] challengeBytes = new byte[RtmpMessage.HANDSHAKE_LEN - 2 * RtmpMessage.INT_SIZE];
    rtmpOutputStream.sendClientHandshake1(challengeBytes);

    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();

    ByteBuffer expected = ByteBuffer.wrap(new byte[] {0, 0, 0, 0, 0, 0, 0, 0});
    ByteBuffer unexpected = ByteBuffer.wrap(new byte[] {0, 0, 0, 0, 0, 0, 0, 1});
    Truth.assertThat(capturedBuffer).isEqualTo(expected);
    Truth.assertThat(capturedBuffer).isNotEqualTo(unexpected);

    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    Truth.assertThat(capturedBuffer.limit() - capturedBuffer.position())
        .isEqualTo(challengeBytes.length);
  }

  @Test
  public void testSetChunkSizeTooSmall() {
    assertThrows(ProtocolException.class, () -> rtmpOutputStream.sendSetChunkSize(4));
  }

  @Test
  public void testSetChunkSizeTooBig() {
    assertThrows(ProtocolException.class, () -> rtmpOutputStream.sendSetChunkSize(0x01000000));
  }

  @Test
  public void testSetChunkSize() throws Exception {
    int expectedChunkSize = 0x00010203;
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.sendSetChunkSize(expectedChunkSize);

    Truth.assertThat(rtmpOutputStream.getChunkSize()).isEqualTo(expectedChunkSize);
    Truth.assertThat(rtmpOutputStream.getChunkDataByteBuffer()).isNotNull();
    Truth.assertThat(rtmpOutputStream.getChunkDataByteBuffer().capacity())
        .isEqualTo(expectedChunkSize);

    verify(mockSocketChannel, times(1)).write(any(ByteBuffer.class));
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected = new byte[] {2, 0, 0, 0, 0, 0, 4, 1, 0, 0, 0, 0, 0, 1, 2, 3};
    byte[] unexpected = new byte[] {2, 0, 0, 0, 0, 0, 4, 1, 0, 0, 0, 0, 3, 2, 1, 0};
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(4);
  }

  @Test
  public void testSendAck() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.sendAcknowledgement(0x01020304);

    verify(mockSocketChannel, times(1)).write(any(ByteBuffer.class));
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();

    byte[] expected = new byte[] {2, 0, 0, 0, 0, 0, 4, 3, 0, 0, 0, 0, 1, 2, 3, 4};
    byte[] unexpected = new byte[] {2, 0, 0, 0, 0, 0, 4, 3, 0, 0, 0, 0, 4, 3, 2, 1};
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(4);
  }

  @Test
  public void testSetWindowSizeTooSmall() throws Exception {
    rtmpOutputStream.setLastLimitType(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_SOFT);
    rtmpOutputStream.setAckWindowSize(0);
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setWindowSize(4, RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD);
    verify(mockSocketChannel, never()).write(any(ByteBuffer.class));
    Truth.assertThat(rtmpOutputStream.getLastLimitType())
        .isEqualTo(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_SOFT);
    Truth.assertThat(rtmpOutputStream.getAckWindowSize()).isEqualTo(0);
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(0);
  }

  @Test
  public void testSetWindowSizeSoftToDynamic() throws Exception {
    rtmpOutputStream.setLastLimitType(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_SOFT);
    rtmpOutputStream.setAckWindowSize(0);
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setWindowSize(
        RtmpMessage.MIN_WINDOW_SIZE, RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_DYNAMIC);
    verify(mockSocketChannel, never()).write(any(ByteBuffer.class));
    Truth.assertThat(rtmpOutputStream.getLastLimitType())
        .isEqualTo(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_SOFT);
    Truth.assertThat(rtmpOutputStream.getAckWindowSize()).isEqualTo(0);
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(0);
  }

  @Test
  public void testSetWindowSizeDynamicToHard() throws Exception {
    rtmpOutputStream.setLastLimitType(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD);
    rtmpOutputStream.setAckWindowSize(0);
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setWindowSize(
        RtmpMessage.MIN_WINDOW_SIZE, RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_DYNAMIC);
    Truth.assertThat(rtmpOutputStream.getLastLimitType())
        .isEqualTo(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD);
    Truth.assertThat(rtmpOutputStream.getAckWindowSize()).isEqualTo(RtmpMessage.MIN_WINDOW_SIZE);
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(4);
  }

  @Test
  public void testSetWindowSizeHardToSoft() throws Exception {
    rtmpOutputStream.setLastLimitType(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD);
    rtmpOutputStream.setAckWindowSize(0);
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setWindowSize(
        RtmpMessage.MIN_WINDOW_SIZE, RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_SOFT);
    Truth.assertThat(rtmpOutputStream.getLastLimitType())
        .isEqualTo(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_SOFT);
    Truth.assertThat(rtmpOutputStream.getAckWindowSize()).isEqualTo(0);
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(4);
  }

  @Test
  public void testSetWindowSizeSoft() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setLastLimitType(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD);
    int newSize = 0x01020304;
    rtmpOutputStream.setAckWindowSize(2 * newSize);
    rtmpOutputStream.setWindowSize(newSize, RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_SOFT);
    Truth.assertThat(rtmpOutputStream.getLastLimitType())
        .isEqualTo(RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_SOFT);
    Truth.assertThat(rtmpOutputStream.getAckWindowSize()).isEqualTo(newSize);

    verify(mockSocketChannel, times(1)).write(any(ByteBuffer.class));
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected = new byte[] {2, 0, 0, 0, 0, 0, 4, 5, 0, 0, 0, 0, 1, 2, 3, 4};
    byte[] unexpected = new byte[] {2, 0, 0, 0, 0, 0, 4, 5, 0, 0, 0, 0, 4, 3, 2, 1};
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(4);
  }

  @Test
  public void testSendConnectNullUri() {
    assertThrows(
        ProtocolException.class,
        () -> rtmpOutputStream.sendConnect(null /* uri */, "key", 0 /* transacationId */));
  }

  @Test
  public void testSendConnectUriEmptyPath() {
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendConnect(Uri.parse("http://host"), "key", 0 /* transacationId */));
  }

  @Test
  public void testSendConnectUriNoPath() {
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendConnect(
                Uri.parse("http://host///"), "key", 0 /* transacationId */));
  }

  @Test
  public void testSendConnectNoKey() {
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendConnect(
                Uri.parse("http://host/path"), "", 0 /* transacationId */));
  }

  /**
   * Verifies that the header is created correctly with a known expected header for {@link
   * RtmpOutputStream#sendConnect(Uri, String, int)}.
   */
  @Test
  public void testSendConnect_assembleHeader() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(2 * RtmpMessage.MAX_HEADER_SIZE);
    // This is a previously known size.
    int size = 224;
    rtmpOutputStream.assembleFullHeader(
        buffer,
        RtmpMessage.CHUNK_STREAM_ID_AMF,
        0 /* timestamp */,
        size,
        RtmpMessage.RTMP_MESSAGE_COMMAND_AMF0,
        RtmpMessage.MESSAGE_STREAM_AUDIO_VIDEO);
    buffer.flip();

    // Verify header
    byte[] expected = new byte[] {3, 0, 0, 0, 0, 0, -32, 20, 1, 0, 0, 0};
    byte[] unexpected = Arrays.copyOf(expected, expected.length - 1);
    Truth.assertThat(buffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(buffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
  }

  @Test
  public void testSendConnect() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    int transactionId = 0x01020304;
    rtmpOutputStream.sendConnect(Uri.parse("http://host/path"), "key", transactionId);
    int bytesSent = (int) rtmpOutputStream.getBytesSent();
    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));

    // Verify header
    ByteBuffer capturedHeaderBuffer = byteBufferCaptor.getNextCapturedBuffer();
    ByteBuffer expectedHeaderBuffer = ByteBuffer.allocate(2 * RtmpMessage.MAX_HEADER_SIZE);
    rtmpOutputStream.assembleFullHeader(
        expectedHeaderBuffer,
        RtmpMessage.CHUNK_STREAM_ID_AMF,
        0 /* timestamp */,
        bytesSent,
        RtmpMessage.RTMP_MESSAGE_COMMAND_AMF0,
        RtmpMessage.MESSAGE_STREAM_AUDIO_VIDEO);
    expectedHeaderBuffer.flip();
    Truth.assertThat(capturedHeaderBuffer).isEqualTo(expectedHeaderBuffer);

    // Verify message. Construct the expected message.
    ByteBuffer capturedMessageBuffer = byteBufferCaptor.getNextCapturedBuffer();
    ActionMessageFormat.Writer amfWriter = new ActionMessageFormat.Writer();
    amfWriter.writeString(RtmpMessage.NETCONNECTION_CONNECT_NAME);
    amfWriter.writeNumber(transactionId);
    amfWriter.writeObjectBegin();
    amfWriter.writePropertyName(RtmpMessage.NETCONNECTION_PROPERTY_APP);
    amfWriter.writeString("path");
    amfWriter.writePropertyName(RtmpMessage.NETCONNECTION_PROPERTY_FLASH_VERSION);
    amfWriter.writeString(versionString);
    amfWriter.writePropertyName(RtmpMessage.NETCONNECTION_PROPERTY_FLASH_VERSION_ALT);
    amfWriter.writeString(versionString);
    amfWriter.writePropertyName(RtmpMessage.NETCONNECTION_PROPERTY_TC_URL);
    amfWriter.writeString("http://host/path");
    amfWriter.writePropertyName(RtmpMessage.NETCONNECTION_PROPERTY_TYPE);
    amfWriter.writeString(RtmpMessage.NETCONNECTION_TYPE_NONPRIVATE);
    amfWriter.writeObjectEnd();
    ByteBuffer expectedMessageBuffer = amfWriter.toByteBuffer();

    // Do a string comparison, so that the error message can be minimally legible.
    Truth.assertThat(new String(capturedMessageBuffer.array(), "UTF-8"))
        .isEqualTo(new String(expectedMessageBuffer.array(), "UTF-8"));
    Truth.assertThat(capturedMessageBuffer).isEqualTo(expectedMessageBuffer);
  }

  @Test
  public void testReleaseStreamNoKey() {
    assertThrows(
        ProtocolException.class,
        () -> rtmpOutputStream.sendReleaseStream("" /* key */, 0 /* transacationId */));
  }

  @Test
  public void testReleaseStream() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.sendReleaseStream("key", 0x01020304 /* transacationId */);
    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));

    // Verify header
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected = new byte[] {3, 0, 0, 0, 0, 0, 32, 20, 1, 0, 0, 0};
    byte[] unexpected = Arrays.copyOf(expected, expected.length - 1);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify message
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected =
        new byte[] {
          2, 0, 13, 114, 101, 108, 101, 97, 115, 101, 83, 116, 114, 101, 97, 109, 0, 65, 112, 32,
          48, 64, 0, 0, 0, 5, 2, 0, 3, 107, 101, 121
        };
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;

    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(expected.length);
  }

  @Test
  public void testCreateStream() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.sendCreateStream(0x01020304 /* transacationId */);
    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));

    // Verify header
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected = new byte[] {3, 0, 0, 0, 0, 0, 25, 20, 1, 0, 0, 0};
    byte[] unexpected = Arrays.copyOf(expected, expected.length - 1);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify message
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected =
        new byte[] {
          2, 0, 12, 99, 114, 101, 97, 116, 101, 83, 116, 114, 101, 97, 109, 0, 65, 112, 32, 48, 64,
          0, 0, 0, 5
        };
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;

    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(expected.length);
  }

  @Test
  public void testPublishNoKey() {
    assertThrows(
        ProtocolException.class,
        () -> rtmpOutputStream.sendPublish("" /* key */, 0 /* transacationId */));
  }

  @Test
  public void testPublish() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.sendPublish("key", 0x01020304 /* transacationId */);
    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));

    // Verify header
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected = new byte[] {3, 0, 0, 0, 0, 0, 33, 20, 1, 0, 0, 0};
    byte[] unexpected = Arrays.copyOf(expected, expected.length - 1);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify message
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected =
        new byte[] {
          2, 0, 7, 112, 117, 98, 108, 105, 115, 104, 0, 65, 112, 32, 48, 64, 0, 0, 0, 5, 2, 0, 3,
          107, 101, 121, 2, 0, 4, 108, 105, 118, 101
        };
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;

    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(expected.length);
  }

  @Test
  public void testPublishCreateStream() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.sendPublish("key", 0x01020304 /* transacationId */);
    rtmpOutputStream.sendCreateStream(0x01020304 /* transacationId */);
    verify(mockSocketChannel, times(4)).write(any(ByteBuffer.class));

    // Verify publish header
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected = new byte[] {3, 0, 0, 0, 0, 0, 33, 20, 1, 0, 0, 0};
    byte[] unexpected = Arrays.copyOf(expected, expected.length - 1);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify publish message
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected =
        new byte[] {
          2, 0, 7, 112, 117, 98, 108, 105, 115, 104, 0, 65, 112, 32, 48, 64, 0, 0, 0, 5, 2, 0, 3,
          107, 101, 121, 2, 0, 4, 108, 105, 118, 101
        };
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    int bytesSent = expected.length;

    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify create stream header
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {3, 0, 0, 0, 0, 0, 25, 20, 1, 0, 0, 0};
    unexpected = Arrays.copyOf(expected, expected.length - 1);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify create stream  message
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected =
        new byte[] {
          2, 0, 12, 99, 114, 101, 97, 116, 101, 83, 116, 114, 101, 97, 109, 0, 65, 112, 32, 48, 64,
          0, 0, 0, 5
        };
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;

    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(bytesSent);
  }

  @Test
  public void testStreamMetaDataNoAudio() {
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendStreamMetaData(
                RtmpMessage.RTMP_AUDIO_CODEC_AAC,
                null /* audioFormat */,
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                createValidVideoFormat()));
  }

  @Test
  public void testStreamMetaDataNoVideo() {
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendStreamMetaData(
                RtmpMessage.RTMP_AUDIO_CODEC_AAC,
                createValidAudioFormat(),
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                null /* videoFormat */));
  }

  @Test
  public void testStreamMetaDataAudioForVideo() {
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendStreamMetaData(
                RtmpMessage.RTMP_AUDIO_CODEC_AAC,
                createValidAudioFormat(),
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                createValidAudioFormat()));
  }

  @Test
  public void testStreamMetaDataVideoForAudio() {
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendStreamMetaData(
                RtmpMessage.RTMP_AUDIO_CODEC_AAC,
                createValidVideoFormat(),
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                createValidVideoFormat()));
  }

  @Test
  public void testStreamMetaDataBadAudioCodec() {
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendStreamMetaData(
                RtmpMessage.RTMP_AUDIO_CODEC_AAC - 1,
                createValidAudioFormat(),
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                createValidVideoFormat()));
  }

  @Test
  public void testStreamMetaDataAudioMissingData() {
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendStreamMetaData(
                RtmpMessage.RTMP_AUDIO_CODEC_AAC,
                MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1),
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                createValidVideoFormat()));
  }

  @Test
  public void testStreamMetaDataVideoMissingData() {
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendStreamMetaData(
                RtmpMessage.RTMP_AUDIO_CODEC_AAC,
                createValidAudioFormat(),
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 854, 480)));
  }

  @Test
  public void testStreamMetaData() throws Exception {
    rtmpOutputStream.setChunkSize(8 * 1024);
    rtmpOutputStream.setBytesSent(0);
    MediaFormat audioFormat = createValidAudioFormat();
    MediaFormat videoFormat = createValidVideoFormat();
    rtmpOutputStream.sendStreamMetaData(
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        audioFormat,
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        videoFormat);
    int bytesSent = (int) rtmpOutputStream.getBytesSent();
    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));

    // Verify header
    ByteBuffer capturedHeaderBuffer = byteBufferCaptor.getNextCapturedBuffer();
    ByteBuffer expectedHeaderBuffer = ByteBuffer.allocate(2 * RtmpMessage.MAX_HEADER_SIZE);
    rtmpOutputStream.assembleFullHeader(
        expectedHeaderBuffer,
        RtmpMessage.CHUNK_STREAM_ID_AMF,
        0 /* timestamp */,
        bytesSent,
        RtmpMessage.RTMP_MESSAGE_DATA_AMF0,
        RtmpMessage.MESSAGE_STREAM_AUDIO_VIDEO);
    expectedHeaderBuffer.flip();
    Truth.assertThat(capturedHeaderBuffer).isEqualTo(expectedHeaderBuffer);

    // Verify message
    ByteBuffer capturedMessageBuffer = byteBufferCaptor.getNextCapturedBuffer();
    ActionMessageFormat.Writer amfWriter = new ActionMessageFormat.Writer();
    amfWriter.writeString(RtmpMessage.NETCONNECTION_STREAM_DATA_NAME);
    amfWriter.writeString(RtmpMessage.NETCONNECTION_STREAM_DATA_METADATA);
    amfWriter.writeArrayBegin(13);
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_DURATION);
    amfWriter.writeNumber(0);
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_WIDTH);
    amfWriter.writeNumber(videoFormat.getInteger(MediaFormat.KEY_WIDTH));
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_HEIGHT);
    amfWriter.writeNumber(videoFormat.getInteger(MediaFormat.KEY_HEIGHT));
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_VIDEO_DATA_RATE);
    amfWriter.writeNumber(videoFormat.getInteger(MediaFormat.KEY_BIT_RATE));
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_FRAME_RATE);
    amfWriter.writeNumber(videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_VIDEO_CODEC_ID);
    amfWriter.writeNumber(RtmpMessage.RTMP_VIDEO_CODEC_AVC);
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_AUDIO_DATA_RATE);
    amfWriter.writeNumber(audioFormat.getInteger(MediaFormat.KEY_BIT_RATE));
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_AUDIO_SAMPLE_RATE);
    int audioSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    amfWriter.writeNumber(audioSampleRate);
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_AUDIO_SAMPLE_SIZE);
    amfWriter.writeNumber(RtmpMessage.getAudioSampleSize(RtmpMessage.RTMP_AUDIO_CODEC_AAC));
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_IS_STEREO);
    amfWriter.writeBoolean(RtmpMessage.getAudioIsStereo(RtmpMessage.RTMP_AUDIO_CODEC_AAC));
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_AUDIO_CODEC_ID);
    amfWriter.writeNumber(RtmpMessage.RTMP_AUDIO_CODEC_AAC);
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_ENCODER);
    amfWriter.writeString(versionString);
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_FILE_SIZE);
    amfWriter.writeNumber(0);
    amfWriter.writeObjectEnd();
    ByteBuffer expectedMessageBuffer = amfWriter.toByteBuffer();

    // Do a string comparison, so that the error message can be minimally legible.
    Truth.assertThat(new String(capturedMessageBuffer.array(), "UTF-8"))
        .isEqualTo(new String(expectedMessageBuffer.array(), "UTF-8"));
    Truth.assertThat(capturedMessageBuffer).isEqualTo(expectedMessageBuffer);
  }

  @Test
  public void testSendSampleDataConfig() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    BufferInfo bufferInfo = new BufferInfo();
    bufferInfo.flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
    rtmpOutputStream.setNeedFirstFrame(false);
    rtmpOutputStream.sendSampleData(
        true /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.allocate(8),
        bufferInfo);

    verify(mockSocketChannel, never()).write(any(ByteBuffer.class));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(0);
    Truth.assertThat(rtmpOutputStream.getNeedFirstFrame()).isEqualTo(false);
  }

  @Test
  public void testSendSampleDataSkipAudioAtStart() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(true);
    BufferInfo bufferInfo = new BufferInfo();
    rtmpOutputStream.sendSampleData(
        true /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.allocate(8),
        bufferInfo);

    verify(mockSocketChannel, never()).write(any(ByteBuffer.class));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(0);
    Truth.assertThat(rtmpOutputStream.getNeedFirstFrame()).isEqualTo(true);
  }

  @Test
  public void testSendSampleDataSkipNonKeyFrameAtStart() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(true);
    BufferInfo bufferInfo = new BufferInfo();
    bufferInfo.flags = 0;
    rtmpOutputStream.sendSampleData(
        false /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.allocate(8),
        bufferInfo);

    verify(mockSocketChannel, never()).write(any(ByteBuffer.class));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(0);
    Truth.assertThat(rtmpOutputStream.getNeedFirstFrame()).isEqualTo(true);
  }

  @Test
  public void testSendSampleDataSkipEosFrameAtStart() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(true);
    BufferInfo bufferInfo = new BufferInfo();
    bufferInfo.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
    rtmpOutputStream.sendSampleData(
        false /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.allocate(8),
        bufferInfo);

    verify(mockSocketChannel, never()).write(any(ByteBuffer.class));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(0);
    Truth.assertThat(rtmpOutputStream.getNeedFirstFrame()).isEqualTo(true);
  }

  @Test
  public void testSendSampleDataSendFirstFrameMissingVideoConfig() {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(true);
    BufferInfo bufferInfo = new BufferInfo();
    bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendSampleData(
                false /* isAudio */,
                RtmpMessage.RTMP_AUDIO_CODEC_AAC,
                createValidAudioFormat(),
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                createValidVideoFormat(),
                ByteBuffer.allocate(8),
                bufferInfo));
  }

  @Test
  public void testSendSampleDataBadStartCode() {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(false);
    rtmpOutputStream.setChunkSize(8);
    BufferInfo bufferInfo = new BufferInfo();
    int dataSize = 8 * 1024;
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendSampleData(
                false /* isAudio */,
                RtmpMessage.RTMP_AUDIO_CODEC_AAC,
                createValidAudioFormat(),
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                createValidVideoFormat(),
                ByteBuffer.allocate(dataSize),
                bufferInfo));
  }

  @Test
  public void testSendSampleDataBadChunk() {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(false);
    rtmpOutputStream.setChunkSize(8);
    BufferInfo bufferInfo = new BufferInfo();
    int dataSize = 8 * 1024;
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendSampleData(
                false /* isAudio */,
                RtmpMessage.RTMP_AUDIO_CODEC_AAC,
                createValidAudioFormat(),
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                createValidVideoFormat(),
                ByteBuffer.allocate(dataSize).putInt(0x04030201),
                bufferInfo));
  }

  @Test
  public void testSendSampleDataSendFirstFrame() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(true);
    BufferInfo bufferInfo = new BufferInfo();
    bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
    MediaFormat videoFormat = createValidVideoFormat();
    videoFormat.setByteBuffer("csd-0", ByteBuffer.allocate(4).putInt(0x04030201));
    videoFormat.setByteBuffer("csd-1", ByteBuffer.allocate(4).putInt(0x08070605));
    MediaFormat audioFormat = createValidAudioFormat();
    audioFormat.setByteBuffer("csd-0", ByteBuffer.allocate(4).putInt(0x0a0b0c0d));
    rtmpOutputStream.sendSampleData(
        false /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        audioFormat,
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        videoFormat,
        ByteBuffer.allocate(8).putInt(0x10111213),
        bufferInfo);

    Truth.assertThat(rtmpOutputStream.getNeedFirstFrame()).isEqualTo(false);
    verify(mockSocketChannel, times(6)).write(any(ByteBuffer.class));

    // Verify header and control tag for video format.
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected =
        new byte[] {
          // message header
          6,
          0,
          0,
          0,
          0,
          0,
          24,
          9,
          1,
          0,
          0,
          0,
          // control tag
          23,
          0,
          0,
          0,
          0
        };
    byte[] unexpected = Arrays.copyOf(expected, expected.length - 1);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    int bytesSent = expected.length - 12;

    // Verify video format.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {1, 100, 0, 13, -1, -31, 0, 4, 4, 3, 2, 1, 1, 0, 4, 8, 7, 6, 5};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    bytesSent += expected.length;

    // Verify header and control tag for audio format.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected =
        new byte[] {
          // message header
          4,
          0,
          0,
          0,
          0,
          0,
          6,
          8,
          1,
          0,
          0,
          0,
          // control tag
          -81,
          0
        };
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    bytesSent += (expected.length - 12);

    // Verify audio format.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {10, 11, 12, 13};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    bytesSent += expected.length;

    // Verify header and control tag for video data.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected =
        new byte[] {
          // message header
          6,
          0,
          0,
          0,
          0,
          0,
          13,
          9,
          1,
          0,
          0,
          0,
          // control tag
          23,
          1,
          0,
          0,
          0
        };
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    bytesSent += (expected.length - 12);

    // Verify video data.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {16, 17, 18, 19, 0, 0, 0, 0};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));
    bytesSent += expected.length;

    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(bytesSent);
  }

  @Test
  public void testSendSampleDataSendAudioNoAnnexB() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(false);
    rtmpOutputStream.sendSampleData(
        true /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7}),
        new BufferInfo());
    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));

    // Verify header and control tag.
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected =
        new byte[] {
          // message header
          4,
          0,
          0,
          0,
          0,
          0,
          9,
          8,
          1,
          0,
          0,
          0,
          // control tag
          -81,
          1
        };
    byte[] unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    int bytesSent = (expected.length - 12);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify audio data.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {1, 2, 3, 4, 5, 6, 7};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(bytesSent);
  }

  @Test
  public void testSendSampleDataSendAudioAnnexB3() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(false);
    rtmpOutputStream.sendSampleData(
        true /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.wrap(new byte[] {0, 0, 1, 2, 3, 4, 5, 6, 7}),
        new BufferInfo());
    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));

    // Verify header and control tag.
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected =
        new byte[] {
          // message header
          4,
          0,
          0,
          0,
          0,
          0,
          12,
          8,
          1,
          0,
          0,
          0,
          // control tag
          -81,
          1,
          // bytes remaining
          0,
          0,
          0,
          6
        };
    byte[] unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    int bytesSent = (expected.length - 12);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify audio data.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {2, 3, 4, 5, 6, 7};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(bytesSent);
  }

  @Test
  public void testSendSampleDataSendAudioAnnexB3Prefix() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(false);
    rtmpOutputStream.sendSampleData(
        true /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.wrap(new byte[] {0, 0, 5, 1, 2, 3, 4, 5, 6, 7}),
        new BufferInfo());
    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));

    // Verify header and control tag.
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected =
        new byte[] {
          // message header
          4,
          0,
          0,
          0,
          0,
          0,
          12,
          8,
          1,
          0,
          0,
          0,
          // control tag
          -81,
          1
        };
    byte[] unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    int bytesSent = (expected.length - 12);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify audio data.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {0, 0, 5, 1, 2, 3, 4, 5, 6, 7};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(bytesSent);
  }

  @Test
  public void testSendSampleDataSendAudioAnnexB4() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(false);
    rtmpOutputStream.sendSampleData(
        true /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.wrap(new byte[] {0, 0, 0, 1, 2, 3, 4, 5, 6, 7}),
        new BufferInfo());
    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));

    // Verify header and control tag.
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected =
        new byte[] {
          // message header
          4,
          0,
          0,
          0,
          0,
          0,
          12,
          8,
          1,
          0,
          0,
          0,
          // control tag
          -81,
          1,
          // bytes remaining
          0,
          0,
          0,
          6
        };
    byte[] unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    int bytesSent = (expected.length - 12);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify audio data.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {2, 3, 4, 5, 6, 7};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(bytesSent);
  }

  @Test
  public void testSendSampleDataSendAudioAnnexB4Bad() {
    rtmpOutputStream.setNeedFirstFrame(false);
    assertThrows(
        ProtocolException.class,
        () ->
            rtmpOutputStream.sendSampleData(
                true /* isAudio */,
                RtmpMessage.RTMP_AUDIO_CODEC_AAC,
                createValidAudioFormat(),
                RtmpMessage.RTMP_VIDEO_CODEC_AVC,
                createValidVideoFormat(),
                ByteBuffer.wrap(new byte[] {0, 0, 0, 2, 3, 4, 5, 6, 7}),
                new BufferInfo()));
  }

  @Test
  public void testSendSampleDataSendVideoSingleChunk() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(false);
    rtmpOutputStream.sendSampleData(
        false /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7}),
        new BufferInfo());
    verify(mockSocketChannel, times(2)).write(any(ByteBuffer.class));

    // Verify header and control tag.
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected =
        new byte[] {
          // message header
          6,
          0,
          0,
          0,
          0,
          0,
          12,
          9,
          1,
          0,
          0,
          0,
          // control tag
          39,
          1,
          0,
          0,
          0
        };
    byte[] unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    int bytesSent = (expected.length - 12);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify video data.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {1, 2, 3, 4, 5, 6, 7};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(bytesSent);
  }

  @Test
  public void testSendSampleDataSendVideoSingleChunkTwice() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(false);
    rtmpOutputStream.sendSampleData(
        false /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7}),
        new BufferInfo());
    rtmpOutputStream.sendSampleData(
        false /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.wrap(new byte[] {8, 9, 10, 11, 12, 13, 14}),
        new BufferInfo());
    verify(mockSocketChannel, times(4)).write(any(ByteBuffer.class));

    // Verify header and control tag.
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected =
        new byte[] {
          // message header
          6,
          0,
          0,
          0,
          0,
          0,
          12,
          9,
          1,
          0,
          0,
          0,
          // control tag
          39,
          1,
          0,
          0,
          0
        };
    byte[] unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    int bytesSent = (expected.length - 12);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify video data.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {1, 2, 3, 4, 5, 6, 7};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify 2nd header and control tag.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected =
        new byte[] {
          // message header
          6,
          0,
          0,
          13,
          0,
          0,
          12,
          9,
          1,
          0,
          0,
          0,
          // control tag
          39,
          1,
          0,
          0,
          0
        };
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += (expected.length - 12);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify 2nd video data.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {8, 9, 10, 11, 12, 13, 14};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(bytesSent);
  }

  @Test
  public void testSendSampleDataSendVideoMultiChunk() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setChunkSize(11);
    rtmpOutputStream.setNeedFirstFrame(false);
    ByteBuffer videoData =
        ByteBuffer.wrap(
            new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20});
    rtmpOutputStream.sendSampleData(
        false /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        videoData,
        new BufferInfo());
    verify(mockSocketChannel, times(6)).write(any(ByteBuffer.class));

    // Verify header and control tag.
    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected =
        new byte[] {
          // message header
          6,
          0,
          0,
          0,
          0,
          0,
          25,
          9,
          1,
          0,
          0,
          0,
          // control tag
          39,
          1,
          0,
          0,
          0
        };
    byte[] unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    int bytesSent = (expected.length - 12);
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify video data part 1.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {1, 2, 3, 4, 5, 6};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify continuation header.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(new byte[] {-58}));

    // Verify video data part 2.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    // Verify continuation header.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(new byte[] {-58}));

    // Verify video data part 2.
    capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    expected = new byte[] {18, 19, 20};
    unexpected = Arrays.copyOf(expected, expected.length);
    unexpected[unexpected.length - 1]++;
    bytesSent += expected.length;
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
    Truth.assertThat(capturedBuffer).isNotEqualTo(ByteBuffer.wrap(unexpected));

    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(bytesSent);
  }

  @Test
  public void testSendSampleDataBadTimestamp() throws Exception {
    rtmpOutputStream.setBytesSent(0);
    rtmpOutputStream.setNeedFirstFrame(false);
    BufferInfo bufferInfo = new BufferInfo();
    bufferInfo.flags = 0;
    bufferInfo.presentationTimeUs = 9 * 1000;
    when(mockContinuityManager.adjustTimestamp(anyLong())).thenReturn(-1);
    rtmpOutputStream.sendSampleData(
        false /* isAudio */,
        RtmpMessage.RTMP_AUDIO_CODEC_AAC,
        createValidAudioFormat(),
        RtmpMessage.RTMP_VIDEO_CODEC_AVC,
        createValidVideoFormat(),
        ByteBuffer.allocate(8),
        bufferInfo);

    verify(mockSocketChannel, never()).write(any(ByteBuffer.class));
    Truth.assertThat(rtmpOutputStream.getBytesSent()).isEqualTo(0);
  }

  @Test
  public void testWriteInt() throws Exception {
    rtmpOutputStream.writeInt(0x01020304);
    verify(mockSocketChannel, times(1)).write(any(ByteBuffer.class));

    ByteBuffer capturedBuffer = byteBufferCaptor.getNextCapturedBuffer();
    byte[] expected = new byte[] {1, 2, 3, 4};
    Truth.assertThat(capturedBuffer).isEqualTo(ByteBuffer.wrap(expected));
  }

  @Test
  public void testWriteByteBuffer() throws Exception {
    ByteBuffer data = ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5});
    byteBufferCaptor.setWriteLimit(2);
    rtmpOutputStream.write(data);
    verify(mockSocketChannel, times(3)).write(any(ByteBuffer.class));
    Truth.assertThat(byteBufferCaptor.getNextCapturedBuffer())
        .isEqualTo(ByteBuffer.wrap(new byte[] {1, 2}));
    Truth.assertThat(byteBufferCaptor.getNextCapturedBuffer())
        .isEqualTo(ByteBuffer.wrap(new byte[] {3, 4}));
    Truth.assertThat(byteBufferCaptor.getNextCapturedBuffer())
        .isEqualTo(ByteBuffer.wrap(new byte[] {5}));
    byteBufferCaptor.clearWriteLimit();
  }

  @Test
  public void testSetShouldStopProcessing() {
    Truth.assertThat(rtmpOutputStream.getShouldStopProcessing()).isFalse();
    rtmpOutputStream.prepareStopProcessing();
    Truth.assertThat(rtmpOutputStream.getShouldStopProcessing()).isTrue();
  }

  @Test
  public void testStopProcessingWithoutSet_causeException() {
    Truth.assertThat(rtmpOutputStream.getShouldStopProcessing()).isFalse();

    assertThrows(IllegalStateException.class, () -> rtmpOutputStream.stopProcessing());
  }

  @Test
  public void testWriteNotConnected() throws IOException {
    when(mockSocketChannel.isConnected()).thenReturn(false);
    assertThrows(IOException.class, () -> rtmpOutputStream.writeInt(0x01020304));
    verify(mockSocketChannel, never()).write(any(ByteBuffer.class));
  }

  private MediaFormat createValidVideoFormat() {
    MediaFormat videoFormat =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 854, 480);
    videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 120000);
    videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
    return videoFormat;
  }

  private MediaFormat createValidAudioFormat() {
    MediaFormat audioFormat =
        MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1);
    audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 120000);
    return audioFormat;
  }
}
