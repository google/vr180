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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.truth.Truth;
import com.google.vr180.media.rtmp.RtmpInputStream.TransactionResult;
import java.io.InputStream;
import java.net.ProtocolException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Test for {@link RtmpInputStream} */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class RtmpInputStreamTest {

  @Mock SocketChannel socketChannel;
  @Mock Socket socket;
  @Mock RtmpInputStream.Callbacks callbacks;

  private final ByteBuffer inputBuffer = ByteBuffer.allocate(RtmpMessage.HANDSHAKE_LEN);
  private RtmpInputStream rtmpInputStream;
  private ReadBufferCaptor readBufferCaptor;

  /** Helper class to wrap an {@link InputStream} around a {@link ByteBuffer}. */
  private static final class ByteBufferInputStream extends InputStream {
    private ByteBuffer buffer;

    public ByteBufferInputStream(ByteBuffer buf) {
      this.buffer = buf;
    }

    @Override
    public int read() {
      if (!buffer.hasRemaining()) {
        return -1;
      }
      return buffer.get() & 0xFF;
    }

    @Override
    public int read(byte[] bytes, int off, int len) {
      if (!buffer.hasRemaining()) {
        return -1;
      }

      len = Math.min(len, buffer.remaining());
      buffer.get(bytes, off, len);
      return len;
    }
  }

  /** Captor that fills in the ByteBuffer argument for a socket read operation. */
  private static final class ReadBufferCaptor implements Answer<Integer> {
    private int readLimit;
    private boolean useReadLimit;
    private ByteBuffer inputBuffer;

    void setInputBuffer(ByteBuffer inputBuffer) {
      this.inputBuffer = inputBuffer;
    }

    void setReadLimit(int maxBytesToWrite) {
      this.readLimit = maxBytesToWrite;
      useReadLimit = true;
    }

    @Override
    public Integer answer(InvocationOnMock invocationOnMock) throws Throwable {
      ByteBuffer arg = (ByteBuffer) invocationOnMock.getArguments()[0];
      int count = arg.remaining();
      if (useReadLimit && count > readLimit) {
        count = readLimit;
      }
      if (inputBuffer == null) {
        return -1;
      }
      int remaining = inputBuffer.remaining();
      if (count > remaining) {
        return -1;
      }
      inputBuffer.limit(inputBuffer.position() + count);
      arg.put(inputBuffer);
      inputBuffer.limit(inputBuffer.position() + remaining - count);
      return count;
    }
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    inputBuffer.clear();
    readBufferCaptor = new ReadBufferCaptor();
    readBufferCaptor.setInputBuffer(inputBuffer);

    when(socketChannel.socket()).thenReturn(socket);
    when(socketChannel.isConnected()).thenReturn(true);
    ByteBufferInputStream inputStream = new ByteBufferInputStream(inputBuffer);
    when(socket.getInputStream()).thenReturn(inputStream);
    when(socketChannel.read(any(ByteBuffer.class))).thenAnswer(readBufferCaptor);
    rtmpInputStream = new RtmpInputStream(socketChannel);
    rtmpInputStream.setCallbackHandler(callbacks);
  }

  @Test
  public void testSetShouldStopProcessing() {
    Truth.assertThat(rtmpInputStream.getShouldStopProcessing()).isFalse();
    rtmpInputStream.prepareStopProcessing();
    Truth.assertThat(rtmpInputStream.getShouldStopProcessing()).isTrue();
  }

  @Test
  public void testReceiveServerHandshake0() throws Exception {
    inputBuffer.put(RtmpMessage.RTMP_VERSION);
    inputBuffer.flip();
    byte version = rtmpInputStream.receiveServerHandshake0();

    Truth.assertThat(version).isEqualTo(RtmpMessage.RTMP_VERSION);
  }

  @Test
  public void testReceiveServerHandshake0Bad() throws Exception {
    inputBuffer.put((byte) (RtmpMessage.RTMP_VERSION - 1));
    inputBuffer.flip();
    assertThrows(ProtocolException.class, () -> rtmpInputStream.receiveServerHandshake0());
  }

  @Test
  public void testReceiveServerHandshake2() throws Exception {
    inputBuffer.putInt(0); // echo timestamp must be 0
    int inputServerTimestamp = 0x01020304;
    inputBuffer.putInt(inputServerTimestamp);
    byte[] challengBytes = new byte[RtmpMessage.HANDSHAKE_LEN - 2 * RtmpMessage.INT_SIZE];
    byte[] challengeData = new byte[] {1, 2, 3, 4, 5, 6, 7};
    System.arraycopy(challengeData, 0, challengBytes, 0, challengeData.length);
    inputBuffer.put(challengBytes);

    inputBuffer.flip();
    int serverTimeStamp = rtmpInputStream.receiveServerHandshake2(challengBytes);

    Truth.assertThat(serverTimeStamp).isEqualTo(inputServerTimestamp);
  }

  @Test
  public void testReceiveServerHandshake2BadEcho() throws Exception {
    inputBuffer.putInt(1); // echo timestamp must be 0
    int inputServerTimestamp = 0x01020304;
    inputBuffer.putInt(inputServerTimestamp);
    byte[] challengBytes = new byte[RtmpMessage.HANDSHAKE_LEN - 2 * RtmpMessage.INT_SIZE];
    byte[] challengeData = new byte[] {1, 2, 3, 4, 5, 6, 7};
    System.arraycopy(challengeData, 0, challengBytes, 0, challengeData.length);
    inputBuffer.put(challengBytes);

    inputBuffer.flip();
    assertThrows(
        ProtocolException.class, () -> rtmpInputStream.receiveServerHandshake2(challengBytes));
  }

  @Test
  public void testReceiveServerHandshake2BadChallenge() throws Exception {
    inputBuffer.putInt(0); // echo timestamp must be 0
    int inputServerTimestamp = 0x01020304;
    inputBuffer.putInt(inputServerTimestamp);
    byte[] challengBytes = new byte[RtmpMessage.HANDSHAKE_LEN - 2 * RtmpMessage.INT_SIZE];
    byte[] challengeData = new byte[] {1, 2, 3, 4, 5, 6, 7};
    System.arraycopy(challengeData, 0, challengBytes, 0, challengeData.length);
    inputBuffer.put(challengBytes);
    challengBytes[challengBytes.length - 1] = 12;
    inputBuffer.flip();
    assertThrows(
        ProtocolException.class, () -> rtmpInputStream.receiveServerHandshake2(challengBytes));
  }

  @Test
  public void testFillBuffer() throws Exception {
    inputBuffer.put(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
    inputBuffer.flip();
    readBufferCaptor.setReadLimit(3);
    ByteBuffer readBuffer = ByteBuffer.allocate(8);
    rtmpInputStream.fillBuffer(readBuffer);
    verify(socketChannel, times(3)).read(any(ByteBuffer.class));

    Truth.assertThat(readBuffer).isEqualTo(inputBuffer);
  }

  @Test
  public void testWindowAckSizeBadLength() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_WINDOW_ACK_SIZE - 1,
          RtmpMessage.MESSAGE_TYPE_WINDOW_ACK_SIZE,
          0,
          0,
          0,
          0
        });
    inputBuffer.putInt(0x01020304);
    inputBuffer.flip();
    assertThrows(ProtocolException.class, () -> rtmpInputStream.readerIteration());
  }

  @Test
  public void testWindowAckSize() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_WINDOW_ACK_SIZE,
          RtmpMessage.MESSAGE_TYPE_WINDOW_ACK_SIZE,
          0,
          0,
          0,
          0
        });
    int size = 0x01020304;
    inputBuffer.putInt(size);
    inputBuffer.flip();
    rtmpInputStream.setBytesReceived(0);
    rtmpInputStream.setAckWindowSize(size / 2);

    rtmpInputStream.readerIteration();
    Truth.assertThat(rtmpInputStream.getAckWindowSize()).isEqualTo(size);
    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(RtmpMessage.INT_SIZE);
  }

  @Test
  public void testRequestAck() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_SET_CHUNK_SIZE,
          RtmpMessage.MESSAGE_TYPE_SET_CHUNK_SIZE,
          0,
          0,
          0,
          0
        });
    inputBuffer.putInt(0x00010203);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    rtmpInputStream.setAckWindowSize(2);
    rtmpInputStream.readerIteration();
    verify(callbacks).onRtmpInputStreamAcknowledgementNeeded(RtmpMessage.INT_SIZE);
    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(RtmpMessage.INT_SIZE);
  }

  @Test
  public void testSetPeerBandwidth() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_SET_PEER_BANDWIDTH,
          RtmpMessage.MESSAGE_TYPE_SET_PEER_BANDWIDTH,
          0,
          0,
          0,
          0
        });
    int size = 0x01020304;
    inputBuffer.putInt(size);
    int limit = RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_DYNAMIC;
    inputBuffer.put((byte) limit);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    rtmpInputStream.readerIteration();
    verify(callbacks).onRtmpInputStreamWindowSizeRequested(size, limit);
    Truth.assertThat(rtmpInputStream.getBytesReceived())
        .isEqualTo(RtmpMessage.INT_SIZE + RtmpMessage.BYTE_SIZE);
  }

  @Test
  public void testSetPeerBandwidthBadLength() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_SET_PEER_BANDWIDTH - 1,
          RtmpMessage.MESSAGE_TYPE_SET_PEER_BANDWIDTH,
          0,
          0,
          0,
          0
        });
    inputBuffer.putInt(0x01020304);
    inputBuffer.put((byte) RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_DYNAMIC);
    inputBuffer.flip();
    assertThrows(ProtocolException.class, () -> rtmpInputStream.readerIteration());
  }

  @Test
  public void testSetChunkSize() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_SET_CHUNK_SIZE,
          RtmpMessage.MESSAGE_TYPE_SET_CHUNK_SIZE,
          0,
          0,
          0,
          0
        });
    int size = 0x00010203;
    inputBuffer.putInt(size);
    inputBuffer.flip();

    rtmpInputStream.setChunkSize(size / 2);
    rtmpInputStream.setBytesReceived(0);
    rtmpInputStream.readerIteration();
    Truth.assertThat(rtmpInputStream.getChunkSize()).isEqualTo(size);
    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(RtmpMessage.INT_SIZE);
  }

  @Test
  public void testSetChunkSizeBadLength() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_SET_CHUNK_SIZE - 1,
          RtmpMessage.MESSAGE_TYPE_SET_CHUNK_SIZE,
          0,
          0,
          0,
          0
        });
    inputBuffer.putInt(0x00010203);
    inputBuffer.flip();
    assertThrows(ProtocolException.class, () -> rtmpInputStream.readerIteration());
  }

  @Test
  public void testSetChunkSizeBadSize() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_SET_CHUNK_SIZE,
          RtmpMessage.MESSAGE_TYPE_SET_CHUNK_SIZE,
          0,
          0,
          0,
          0
        });
    inputBuffer.putInt(0x01020304);
    inputBuffer.flip();
    assertThrows(ProtocolException.class, () -> rtmpInputStream.readerIteration());
  }

  @Test
  public void testAbortMessage() throws Exception {
    byte chunkStreamId = 2;
    inputBuffer.put(
        new byte[] {
          chunkStreamId,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_ABORT,
          RtmpMessage.MESSAGE_TYPE_ABORT,
          0,
          0,
          0,
          0
        });
    inputBuffer.putInt(chunkStreamId);
    inputBuffer.put(
        new byte[] {
          chunkStreamId,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_SET_CHUNK_SIZE,
          RtmpMessage.MESSAGE_TYPE_SET_CHUNK_SIZE,
          0,
          0,
          0,
          0
        });
    int originalChunkSize = rtmpInputStream.getChunkSize();
    inputBuffer.putInt(2 * originalChunkSize);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    rtmpInputStream.readerIteration();
    rtmpInputStream.readerIteration();
    Truth.assertThat(rtmpInputStream.getChunkSize()).isEqualTo(originalChunkSize);
    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(2 * RtmpMessage.INT_SIZE);
  }

  @Test
  public void testAbortMessageBadLength() throws Exception {
    byte chunkStreamId = 2;
    inputBuffer.put(
        new byte[] {
          chunkStreamId,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_ABORT - 1,
          RtmpMessage.MESSAGE_TYPE_ABORT,
          0,
          0,
          0,
          0
        });
    inputBuffer.putInt(chunkStreamId);
    inputBuffer.flip();
    assertThrows(ProtocolException.class, () -> rtmpInputStream.readerIteration());
  }

  @Test
  public void testAcknowledgement() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_ACKNOWLEDGEMENT,
          RtmpMessage.MESSAGE_TYPE_ACKNOWLEDGEMENT,
          0,
          0,
          0,
          0
        });
    int size = 0x11223344;
    inputBuffer.putInt(size);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    rtmpInputStream.readerIteration();
    verify(callbacks).onRtmpInputStreamPeerAcknowledgement(size);
    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(RtmpMessage.INT_SIZE);
  }

  @Test
  public void testAcknowledgementBadLength() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_ACKNOWLEDGEMENT - 1,
          RtmpMessage.MESSAGE_TYPE_ACKNOWLEDGEMENT,
          0,
          0,
          0,
          0
        });
    inputBuffer.putInt(0x11223344);
    inputBuffer.flip();

    assertThrows(ProtocolException.class, () -> rtmpInputStream.readerIteration());
  }

  @Test
  public void testHeaderNoStreamId() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_WINDOW_ACK_SIZE,
          RtmpMessage.MESSAGE_TYPE_WINDOW_ACK_SIZE,
          0,
          0,
          0,
          0
        });
    int size = 0x01020304;
    inputBuffer.putInt(size / 2);
    inputBuffer.put(
        new byte[] {
          0x42,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_WINDOW_ACK_SIZE,
          RtmpMessage.MESSAGE_TYPE_WINDOW_ACK_SIZE
        });
    inputBuffer.putInt(size);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    rtmpInputStream.readerIteration();
    rtmpInputStream.readerIteration();
    Truth.assertThat(rtmpInputStream.getAckWindowSize()).isEqualTo(size);
    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(2 * RtmpMessage.INT_SIZE);
  }

  @Test
  public void testHeaderNoStreamIdBad() throws Exception {
    inputBuffer.put(
        new byte[] {
          0x42,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_WINDOW_ACK_SIZE,
          RtmpMessage.MESSAGE_TYPE_WINDOW_ACK_SIZE
        });
    inputBuffer.putInt(0x01020304);
    inputBuffer.flip();

    assertThrows(ProtocolException.class, () -> rtmpInputStream.readerIteration());
  }

  @Test
  public void testHeaderTimeDelta() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_WINDOW_ACK_SIZE,
          RtmpMessage.MESSAGE_TYPE_WINDOW_ACK_SIZE,
          0,
          0,
          0,
          0
        });
    int size = 0x01020304;
    inputBuffer.putInt(size / 2);
    inputBuffer.put(new byte[] {(byte) 0x82, 0, 0, 1});
    inputBuffer.putInt(size);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    rtmpInputStream.readerIteration();
    rtmpInputStream.readerIteration();
    Truth.assertThat(rtmpInputStream.getAckWindowSize()).isEqualTo(size);
    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(2 * RtmpMessage.INT_SIZE);
  }

  @Test
  public void testHeaderTimeDeltaBad() throws Exception {
    inputBuffer.put(
        new byte[] {
          (byte) 0x82,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_WINDOW_ACK_SIZE,
          RtmpMessage.MESSAGE_TYPE_WINDOW_ACK_SIZE
        });
    inputBuffer.putInt(0x01020304);
    inputBuffer.flip();

    assertThrows(ProtocolException.class, () -> rtmpInputStream.readerIteration());
  }

  @Test
  public void testNoHeader() throws Exception {
    inputBuffer.put(
        new byte[] {
          2,
          0,
          0,
          0,
          0,
          0,
          RtmpMessage.MESSAGE_LEN_WINDOW_ACK_SIZE,
          RtmpMessage.MESSAGE_TYPE_WINDOW_ACK_SIZE,
          0,
          0,
          0,
          0
        });
    int size = 0x01020304;
    inputBuffer.putInt(size / 2);
    inputBuffer.put(new byte[] {(byte) 0xc2});
    inputBuffer.putInt(size);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    rtmpInputStream.readerIteration();
    rtmpInputStream.readerIteration();
    Truth.assertThat(rtmpInputStream.getAckWindowSize()).isEqualTo(size);
    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(2 * RtmpMessage.INT_SIZE);
  }

  @Test
  public void testNoHeaderBad() throws Exception {
    inputBuffer.put(new byte[] {(byte) 0xc2});
    inputBuffer.flip();

    assertThrows(ProtocolException.class, () -> rtmpInputStream.readerIteration());
  }

  @Test
  public void testAmfResultNetconnect() throws Exception {
    inputBuffer.put(new byte[] {3, 0, 0, 0, 0, 0, -16, 20, 0, 0, 0, 0});
    byte[] amfChunk1 = {
      2, 0, 7, 95, 114, 101, 115, 117, 108, 116, 0, 63, -16, 0, 0, 0, 0, 0, 0, 3, 0, 6, 102, 109,
      115, 86, 101, 114, 2, 0, 13, 70, 77, 83, 47, 51, 44, 53, 44, 51, 44, 56, 50, 52, 0, 12, 99,
      97, 112, 97, 98, 105, 108, 105, 116, 105, 101, 115, 0, 64, 95, -64, 0, 0, 0, 0, 0, 0, 4, 109,
      111, 100, 101, 0, 63, -16, 0, 0, 0, 0, 0, 0, 0, 0, 9, 3, 0, 5, 108, 101, 118, 101, 108, 2, 0,
      6, 115, 116, 97, 116, 117, 115, 0, 4, 99, 111, 100, 101, 2, 0, 29, 78, 101, 116, 67, 111, 110,
      110, 101, 99, 116, 105, 111, 110, 46, 67, 111, 110
    };
    inputBuffer.put(amfChunk1);
    inputBuffer.put(new byte[] {(byte) 0xc3});
    byte[] amfChunk2 = {
      110, 101, 99, 116, 46, 83, 117, 99, 99, 101, 115, 115, 0, 11, 100, 101, 115, 99, 114, 105,
      112, 116, 105, 111, 110, 2, 0, 21, 67, 111, 110, 110, 101, 99, 116, 105, 111, 110, 32, 115,
      117, 99, 99, 101, 101, 100, 101, 100, 46, 0, 14, 111, 98, 106, 101, 99, 116, 69, 110, 99, 111,
      100, 105, 110, 103, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 100, 97, 116, 97, 8, 0, 0, 0, 1, 0, 7,
      118, 101, 114, 115, 105, 111, 110, 2, 0, 9, 51, 44, 53, 44, 51, 44, 56, 50, 52, 0, 0, 9, 0, 0,
      9
    };
    inputBuffer.put(amfChunk2);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    Future<TransactionResult> future = rtmpInputStream.createTransaction(1);
    rtmpInputStream.readerIteration();
    rtmpInputStream.readerIteration();

    Truth.assertThat(rtmpInputStream.getBytesReceived())
        .isEqualTo(amfChunk1.length + amfChunk2.length);
    Truth.assertThat(future.get().getStatus())
        .isEqualTo(RtmpInputStream.TransactionResult.STATUS_SUCCESS);
    Truth.assertThat(future.get().getStatusMessage()).isEqualTo("NetConnection.Connect.Success");
  }

  @Test
  public void testAmfResultStreamId() throws Exception {
    inputBuffer.put(new byte[] {3, 0, 0, 0, 0, 0, 29, 20, 0, 0, 0, 0});
    byte[] amfMessage = {
      2, 0, 7, 95, 114, 101, 115, 117, 108, 116, 0, 64, 38, 0, 0, 0, 0, 0, 0, 5, 0, 63, -16, 0, 0,
      0, 0, 0, 0
    };
    inputBuffer.put(amfMessage);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    Future<TransactionResult> future = rtmpInputStream.createTransaction(11);
    rtmpInputStream.readerIteration();

    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(amfMessage.length);
    Truth.assertThat(future.get().getStatus())
        .isEqualTo(RtmpInputStream.TransactionResult.STATUS_SUCCESS);
    Truth.assertThat(future.get().getMessageStreamId()).isEqualTo(1);
  }

  @Test
  public void testAmfOnStatus() throws Exception {
    inputBuffer.put(new byte[] {5, 0, 0, 0, 0, 0, 73, 20, 1, 0, 0, 0});
    byte[] amfMessage = {
      2, 0, 8, 111, 110, 83, 116, 97, 116, 117, 115, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 3, 0, 5, 108,
      101, 118, 101, 108, 2, 0, 6, 115, 116, 97, 116, 117, 115, 0, 4, 99, 111, 100, 101, 2, 0, 23,
      78, 101, 116, 83, 116, 114, 101, 97, 109, 46, 80, 117, 98, 108, 105, 115, 104, 46, 83, 116,
      97, 114, 116, 0, 0, 9
    };
    inputBuffer.put(amfMessage);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    Future<TransactionResult> future = rtmpInputStream.createTransaction(2);
    rtmpInputStream.readerIteration();

    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(amfMessage.length);
    Truth.assertThat(future.get().getStatus())
        .isEqualTo(RtmpInputStream.TransactionResult.STATUS_SUCCESS);
    Truth.assertThat(future.get().getStatusMessage()).isEqualTo("NetStream.Publish.Start");
  }

  @Test
  public void testAmfOnStatusTwice() throws Exception {
    byte[] header = {5, 0, 0, 0, 0, 0, 73, 20, 1, 0, 0, 0};
    byte[] amfMessage = {
      2, 0, 8, 111, 110, 83, 116, 97, 116, 117, 115, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5, 3, 0, 5, 108,
      101, 118, 101, 108, 2, 0, 6, 115, 116, 97, 116, 117, 115, 0, 4, 99, 111, 100, 101, 2, 0, 23,
      78, 101, 116, 83, 116, 114, 101, 97, 109, 46, 80, 117, 98, 108, 105, 115, 104, 46, 83, 116,
      97, 114, 116, 0, 0, 9
    };
    inputBuffer.put(header);
    inputBuffer.put(amfMessage);
    inputBuffer.put(header);
    inputBuffer.put(amfMessage);
    inputBuffer.flip();

    rtmpInputStream.setBytesReceived(0);
    Future<TransactionResult> future = rtmpInputStream.createTransaction(2);
    rtmpInputStream.readerIteration();

    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(amfMessage.length);
    Truth.assertThat(future.get().getStatus())
        .isEqualTo(RtmpInputStream.TransactionResult.STATUS_SUCCESS);
    Truth.assertThat(future.get().getStatusMessage()).isEqualTo("NetStream.Publish.Start");

    future = rtmpInputStream.createTransaction(2);
    rtmpInputStream.readerIteration();
    Truth.assertThat(rtmpInputStream.getBytesReceived()).isEqualTo(2 * amfMessage.length);
    Truth.assertThat(future.get().getStatus())
        .isEqualTo(RtmpInputStream.TransactionResult.STATUS_SUCCESS);
    Truth.assertThat(future.get().getStatusMessage()).isEqualTo("NetStream.Publish.Start");
  }

  @Test
  public void testRepeatPendingTransaction() throws Exception {
    @SuppressWarnings({"unused", "nullness"})
    Future<?> possiblyIgnoredError = rtmpInputStream.createTransaction(3);
    assertThrows(
        IllegalStateException.class,
        () -> {
          @SuppressWarnings({"unused", "nullness"})
          Future<?> possiblyIgnoredError1 = rtmpInputStream.createTransaction(3);
        });
  }

  @Test
  public void testMultiplePendingTransactions() throws Exception {
    @SuppressWarnings({"unused", "nullness"})
    Future<?> possiblyIgnoredError = rtmpInputStream.createTransaction(3);
    @SuppressWarnings({"unused", "nullness"})
    Future<?> possiblyIgnoredError1 = rtmpInputStream.createTransaction(4);
    rtmpInputStream.clearTransaction(4);
    @SuppressWarnings({"unused", "nullness"})
    Future<?> possiblyIgnoredError2 = rtmpInputStream.createTransaction(4);
  }

  @Test
  public void testStopProcessingWithoutSet_causeException() {
    assertThrows(IllegalStateException.class, () -> rtmpInputStream.stopProcessing());
  }
}
