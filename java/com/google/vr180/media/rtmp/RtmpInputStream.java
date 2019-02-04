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

import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/**
 * Input stream for an RTMP client connection.
 */
/* package */ class RtmpInputStream {

  private static final String TAG = "RtmpInputStream";
  private static final String THREAD_NAME = "rtmpInput";

  private static final int JOIN_WAIT_TIME_MS = 200;
  private static final int INPUT_BUFFER_SIZE = 8;

  private final ExecutorService executorService = Executors.newCachedThreadPool();
  private final SocketChannel socketChannel;
  private final ByteBuffer inputBuffer;
  private final ByteBuffer chunkHeaderByteBuffer
      = ByteBuffer.allocate(RtmpMessage.CHUNK_FORMAT_FULL_SIZE);

  private Thread readerThread;
  private volatile boolean shouldStopProcessing;
  private SparseArray<ChunkInfo> chunkHeaderMap = new SparseArray<>();
  private SparseArray<PendingTransaction> transactionMap = new SparseArray<>();
  private int chunkSize = RtmpMessage.DEFAULT_CHUNK_SIZE;
  private ByteBuffer chunkByteBuffer = ByteBuffer.allocate(chunkSize);
  private Callbacks callback;
  private volatile Handler callbackHandler;
  private int bytesReceived;
  private volatile int bytesAcknowledged;
  private int ackWindowSize;
  private boolean ackRequested;

  // Chunk info for a chunk stream ID
  private static class ChunkInfo {
    int chunkStreamId;
    int length;
    int messageType;
    int messageStreamId;
    int timestampDelta;
    boolean isAborting;
    int messageBytesPending;
    long timestamp;
    byte[] messageData;
    ByteBuffer messageBuffer;
    ByteArrayInputStream messageInputStream;
    DataInputStream messageDataStream;

    @Override
    public String toString() {
      return "[ chunkStreamId=" + chunkStreamId
          + ", length=" + length
          + ", messageType=" + messageType
          + ", messageStreamId=" + messageStreamId
          + ", timestampDelta=" + timestampDelta
          + ", timestamp=" + timestamp
          + ", isAborting=" + isAborting
          + ", mesgBytesPending=" + messageBytesPending
          + ", dataSize=" + (messageData == null ? 0 : messageData.length)
          + " ]";
    }
  }

  /** Result of a request/response transaction. */
  static class TransactionResult {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_ERROR = 1;

    private int status = -1;
    private String statusMessage;
    private int messageStreamId = -1;

    public int getStatus() {
      return status;
    }

    String getStatusMessage() {
      return statusMessage;
    }

    int getMessageStreamId() {
      return messageStreamId;
    }

    @VisibleForTesting
    void setStatus(int status) {
      this.status = status;
    }

    @VisibleForTesting
    void setStatusMessage(String statusMessage) {
      this.statusMessage = statusMessage;
    }

    private String getStatusString() {
      switch (status) {
        case STATUS_SUCCESS:
          return "SUCCESS";
        case STATUS_ERROR:
          return "ERROR";
        default:
          return "UNKNOWN";
      }
    }

    @Override
    public String toString() {
      return "[ status=" + getStatusString()
          + ", statusMesg=" + statusMessage + ", mesgStreamId=" + messageStreamId  + " ]";
    }
  }

  // State related to a pending transaction
  private static class PendingTransaction {
    int transactionId;
    TransactionResult result;
    CountDownLatch latch;
  }

  /** Callbacks for input stream events. */
  public interface Callbacks {
    /**
     * Stream encountered an error and is no longer viable.  Appropriate action is to
     * close and release.
     * @param cause When not null, the cause of the error.
     */
    void onRtmpInputStreamError(@Nullable Throwable cause);

    /**
     * Peer sent an acknowledgement that it has received the given number of bytes in total.
     */
    void onRtmpInputStreamPeerAcknowledgement(int peerBytesReceived);

    /**
     * Acknowledgement that the given number of bytes has been received should be sent to the remote
     * peer.
     */
    void onRtmpInputStreamAcknowledgementNeeded(int localBytesReceived);

    /**
     * Peer requests the given window size for unacknowledged bytes sent to it.
     */
    void onRtmpInputStreamWindowSizeRequested(int requestedWindowSize, int limitType);
  }

  /**
   * Constructs a new RtmpInputStream on the InputStream {@code in}. All reads are then filtered
   * through this stream.
   */
  RtmpInputStream(SocketChannel socketChannel) throws IOException {
    this.socketChannel = socketChannel;
    inputBuffer = ByteBuffer.allocate(INPUT_BUFFER_SIZE);
    inputBuffer.order(ByteOrder.BIG_ENDIAN);
  }

  /** See {@link RtmpInputStream#setCallbackHandler(Callbacks, Handler)}  */
  public void setCallbackHandler(@Nullable Callbacks cb) {
    setCallbackHandler(cb, null);
  }

  /**
   * Sets the callback handler and the Handler to process them.
   *
   * @param cb The callback handler, or {@code null} to remove
   * @param handler The Handler on which to process the callback, or {@code null} to process
   *   on the calling Handler
   */
  public void setCallbackHandler(@Nullable Callbacks cb, @Nullable Handler handler) {
    callback = cb;
    if (handler == null) {
      Looper looper = Looper.myLooper();
      if (looper == null) {
        looper = Looper.getMainLooper();
      }
      handler = new Handler(looper);
    }
    callbackHandler = handler;
  }

  /**
   * Read and validate the RTMP S0 handshake message.
   */
  public byte receiveServerHandshake0() throws IOException {
    byte version = readByte();
    if (version != RtmpMessage.RTMP_VERSION) {
      throw new ProtocolException("Unknown RTMP version: " + version);
    }
    return version;
  }

  /** Read a single byte from the socket input. */
  private byte readByte() throws IOException {
    inputBuffer.clear();
    inputBuffer.limit(1);
    fillBuffer(inputBuffer);
    inputBuffer.flip();
    return inputBuffer.get();
  }

  /** Read from the socket until the buffer is filled. */
  @VisibleForTesting
  void fillBuffer(ByteBuffer byteBuffer) throws IOException {
    while (byteBuffer.remaining() > 0) {
      if (!socketChannel.isConnected()
          || socketChannel.read(byteBuffer) < 0) {
        throw new IOException("socket closed");
      }
    }
  }

  /** Read a single integer from the socket input in network byte order. */
  public int readInt() throws IOException {
    inputBuffer.clear();
    inputBuffer.limit(4);
    fillBuffer(inputBuffer);
    inputBuffer.flip();
    return inputBuffer.getInt();
  }

  /** Read exactly length bytes into the given buffer from the socket input. */
  private void readFully(ByteBuffer buffer, int offset, int length) throws IOException {
    buffer.position(offset);
    buffer.limit(offset + length);
    fillBuffer(buffer);
  }

  /**
   * Read and validate the RTMP S1 handshake message.
   * @param challengeBytes Challenge data.
   * @return Server timestamp when C1 was received.
   */
  public int receiveServerHandshake2(byte[] challengeBytes) throws IOException {
    Preconditions.checkNotNull(challengeBytes);
    Preconditions.checkArgument(challengeBytes.length
        == RtmpMessage.HANDSHAKE_LEN - 2 * RtmpMessage.INT_SIZE);

    int echoTimestamp = readInt();
    if (echoTimestamp != 0) {
      throw new ProtocolException("Timestamp mismatch in S2: " + echoTimestamp + " != 0");
    }
    int serverTimestamp = readInt();
    for (int i = 0; i < challengeBytes.length; i++) {
      byte echoValue = readByte();
      if (echoValue != challengeBytes[i]) {
        throw new ProtocolException(
            "Data mismatch in S2: " + echoValue + " != " + challengeBytes[i]);
      }
    }
    return serverTimestamp;
  }

  private final Object threadLock = new Object();
  private final Runnable readerThreadRunnable = new Runnable() {
    @Override
    public void run() {
      try {
        readerLoop();
      } catch (Throwable t) {
        if (!shouldStopProcessing) {
          Log.e(TAG, "Unexpected throwable in reader loop", t);
          notifyError(t);
        }
      } finally {
        synchronized (threadLock) {
          readerThread = null;
        }
      }
    }
  };

  /**
   * Begin processing incoming messages.
   */
  public void startProcessing() {
    synchronized (threadLock) {
      if (readerThread != null) {
        return;
      }

      shouldStopProcessing = false;

      // Inherit thread prio from calling context of capture pipeline.
      readerThread = new Thread(readerThreadRunnable, THREAD_NAME);
      readerThread.start();
    }
  }

  /**
   * Prepare for expected socket closure for exception handling.
   */
  public void prepareStopProcessing() {
    synchronized (threadLock) {
      shouldStopProcessing = true;
    }
  }

  /**
   * Stop processing incoming messages.
   */
  public boolean stopProcessing() {
    Preconditions.checkState(shouldStopProcessing);
    synchronized (threadLock) {
      if (readerThread == null) {
        return true;
      }

      while (true) {
        try {
          readerThread.join(JOIN_WAIT_TIME_MS);
          break;
        } catch (InterruptedException e) {
          // Ignore
        }
      }

      if (readerThread != null && readerThread.isAlive()) {
        readerThread.interrupt();
        while (true) {
          try {
            readerThread.join(JOIN_WAIT_TIME_MS);
            break;
          } catch (InterruptedException e) {
            // Ignore
          }
        }
        if (readerThread != null && !readerThread.isAlive()) {
          readerThread = null;
        }
      }

      return (readerThread == null);
    }
  }

  /**
   * Sets the number of received bytes that have been acknowledged to the peer.  In response
   * to receiving {@link RtmpInputStream.Callbacks#onRtmpInputStreamAcknowledgementNeeded(int)},
   * the client should typically send an ack message to the peer and then call this routine to
   * indicate a successful ack was sent.
   */
  public synchronized void setBytesAcknowledged(int bytesAcknowledged) {
    this.bytesAcknowledged = bytesAcknowledged;
    ackRequested = false;

    // Make sure an ack isn't needed already.
    updateBytesReceived(0);
  }

  /**
   * Creates a pending transaction with the given ID.  A Future is returned on which the caller
   * can await the result, once the transaction is initiated, e.g. by transmitting a request on
   * an {@link RtmpInputStream}
   */
  public Future<TransactionResult> createTransaction(int transactionId) {
    // Check for an active transaction
    PendingTransaction existingTransaction = transactionMap.get(transactionId);
    if (existingTransaction != null && existingTransaction.result == null) {
      throw new IllegalStateException("Transaction already in progress: " + transactionId);
    }
    final PendingTransaction pendingTransaction = new PendingTransaction();
    pendingTransaction.transactionId = transactionId;
    pendingTransaction.latch = new CountDownLatch(1);
    transactionMap.put(transactionId, pendingTransaction);

    return executorService.submit(
        () -> {
          pendingTransaction.latch.await();
          return pendingTransaction.result;
        });
  }

  /**
   * Clears a pending transaction.  MUST be called once a transaction has completed in order
   * to free resources and re-use the transaction ID.
   */
  public void clearTransaction(int transactionId) {
    transactionMap.remove(transactionId);
  }

  // Processing loop to read and assemble incoming messages.
  private void readerLoop() throws IOException {
    while (!shouldStopProcessing) {
      readerIteration();
    }
  }

  // Handle one iteration of the reader loop.
  @VisibleForTesting
  void readerIteration() throws IOException {
    // Read chunk header
    ChunkInfo chunkInfo = readChunkHeader();

    int mesgBytesRead;
    if (chunkInfo.isAborting) {
      // Skip messages that are being aborted
      mesgBytesRead = drainBytes(chunkInfo);
    } else if (chunkInfo.chunkStreamId == RtmpMessage.CHUNK_STREAM_ID_CONTROL
        && chunkInfo.messageStreamId == RtmpMessage.MESSAGE_STREAM_CONTROL) {
      // Process control messages
      mesgBytesRead = processControlMessage(chunkInfo);
    } else if (chunkInfo.messageType == RtmpMessage.RTMP_MESSAGE_COMMAND_AMF0) {
      // Process AMF0 command messages
      mesgBytesRead = processCommandMessage(chunkInfo);
    } else {
      Log.e(TAG, "Skipping unknown message: type= " + chunkInfo.messageType);
      mesgBytesRead = drainBytes(chunkInfo);
    }

    updateBytesReceived(mesgBytesRead);
  }

  private int processCommandMessage(ChunkInfo chunkInfo) throws IOException {
    // Read the next chunk of the message
    int readCount = readChunk(chunkInfo);
    if (chunkInfo.messageBytesPending <= 0) {
      // Prepare an AMF reader
      chunkInfo.messageBytesPending = 0;
      chunkInfo.messageInputStream.reset();
      ActionMessageFormat.Reader amfReader =
          new ActionMessageFormat.Reader(chunkInfo.messageDataStream);

      // Get the command name
      String command = null;
      try {
        command = amfReader.readString();
      } catch (ProtocolException e) {
        Log.e(TAG, "Skipping AMF message without a command");
      }

      if (RtmpMessage.AMF_COMMAND_RESPONSE_RESULT.equals(command)) {
        // Handle result message
        int transactionId = (int) amfReader.readNumber();
        PendingTransaction pendingTransaction = transactionMap.get(transactionId);
        if (pendingTransaction == null) {
          Log.e(TAG, "No pending transaction: " + transactionId);
        } else {
          pendingTransaction.result = new TransactionResult();
          pendingTransaction.result.status = TransactionResult.STATUS_SUCCESS;

          Object properties = amfReader.readValue();
          Object info = amfReader.readValue();

          if (properties == null && info instanceof Double) {
            // Result is a null set of properties and a message stream ID
            Double messageStreamId = (Double) info;
            pendingTransaction.result.messageStreamId = messageStreamId.intValue();
          } else if (properties instanceof Map && info instanceof Map) {
            // Result is a pair of AMF objects, which map string keys to other objects.
            Map<String, Object> infoMap = (Map<String, Object>) info;
            Object level = infoMap.get(RtmpMessage.AMF_RESPONSE_LEVEL_KEY);
            Object code = infoMap.get(RtmpMessage.AMF_RESPONSE_CODE_KEY);
            if ((level instanceof String)
                && RtmpMessage.AMF_RESPONSE_LEVEL_VALUE_STATUS.equals(level)
                && (code instanceof String)) {
              pendingTransaction.result.statusMessage = (String) code;
            }
          }
          pendingTransaction.latch.countDown();
        }
      } else if (RtmpMessage.AMF_COMMAND_RESPONSE_ONSTATUS.equals(command)) {
        // Handle onStatus message as a result
        int transactionId = RtmpMessage.NETCONNECTION_ONSTATUS_TRANSACTION_ID;
        PendingTransaction pendingTransaction = transactionMap.get(transactionId);
        if (pendingTransaction == null) {
          Log.e(TAG, "No pending transaction: " + transactionId);
        } else {
          pendingTransaction.result = new TransactionResult();
          pendingTransaction.result.status = TransactionResult.STATUS_SUCCESS;

          // Transaction ID is unused
          amfReader.readNumber();

          // No properties
          amfReader.readNull();
          Map<String, Object> infoMap = amfReader.readObject();

          Object level = infoMap.get(RtmpMessage.AMF_RESPONSE_LEVEL_KEY);
          Object code = infoMap.get(RtmpMessage.AMF_RESPONSE_CODE_KEY);
          if ((level instanceof String)
              && RtmpMessage.AMF_RESPONSE_LEVEL_VALUE_STATUS.equals(level)
              && (code instanceof String)) {
            pendingTransaction.result.statusMessage = (String) code;
          }
          pendingTransaction.latch.countDown();
        }
      } else if (RtmpMessage.AMF_COMMAND_RESPONSE_ERROR.equals(command)) {
        // Handle result message
        int transactionId = (int) amfReader.readNumber();
        PendingTransaction pendingTransaction = transactionMap.get(transactionId);
        if (pendingTransaction != null) {
          pendingTransaction.result = new TransactionResult();
          pendingTransaction.result.status = TransactionResult.STATUS_ERROR;
          pendingTransaction.latch.countDown();
        }
      } else {
        Log.e(TAG, "Ignoring unrecognized AMF command: " + command);
      }
    }

    return readCount;
  }

  private int readChunk(ChunkInfo chunkInfo) throws IOException {
    if (chunkInfo.messageBytesPending == 0) {
      chunkInfo.messageBytesPending = chunkInfo.length;
    }
    if (chunkInfo.messageData == null || chunkInfo.messageData.length < chunkInfo.length) {
      chunkInfo.messageData = new byte[chunkInfo.length];
      chunkInfo.messageBuffer = ByteBuffer.wrap(chunkInfo.messageData);
      chunkInfo.messageInputStream = new ByteArrayInputStream(chunkInfo.messageData);
      chunkInfo.messageDataStream = new DataInputStream(chunkInfo.messageInputStream);
    }
    int readCount = Math.min(chunkInfo.messageBytesPending, chunkSize);
    if (readCount > 0) {
      readFully(chunkInfo.messageBuffer,
          chunkInfo.length - chunkInfo.messageBytesPending,
          readCount);
    }
    chunkInfo.messageBytesPending -= readCount;
    return readCount;
  }

  private int processControlMessage(ChunkInfo chunkInfo) throws IOException {
    switch (chunkInfo.messageType) {
      case RtmpMessage.MESSAGE_TYPE_SET_CHUNK_SIZE:
        if (chunkInfo.length != RtmpMessage.MESSAGE_LEN_SET_CHUNK_SIZE) {
          throw new ProtocolException(
              "Invalid message length for set chunk size: " + chunkInfo.length);
        }
        chunkSize = readInt();

        if (!RtmpMessage.isValidLength(chunkSize)) {
          throw new ProtocolException("Invalid chunk size: " + chunkSize);
        }
        chunkByteBuffer = ByteBuffer.allocate(chunkSize);
        return RtmpMessage.INT_SIZE;

      case RtmpMessage.MESSAGE_TYPE_ABORT:
        if (chunkInfo.length != RtmpMessage.MESSAGE_LEN_ABORT) {
          throw new ProtocolException(
              "Invalid message length for abort: " + chunkInfo.length);
        }
        int abortChunk = readInt();

        ChunkInfo abortInfo = chunkHeaderMap.get(abortChunk);
        if (abortInfo == null) {
          Log.e(TAG, "Ignoring request to abort unrecognized message");
        } else {
          abortInfo.isAborting = true;
        }
        return RtmpMessage.INT_SIZE;

      case RtmpMessage.MESSAGE_TYPE_ACKNOWLEDGEMENT:
        if (chunkInfo.length != RtmpMessage.MESSAGE_LEN_ACKNOWLEDGEMENT) {
          throw new ProtocolException("Invalid message length for ack: " + chunkInfo.length);
        }
        notifyPeerAck(readInt());
        return RtmpMessage.INT_SIZE;

      case RtmpMessage.MESSAGE_TYPE_WINDOW_ACK_SIZE:
        if (chunkInfo.length != RtmpMessage.MESSAGE_LEN_WINDOW_ACK_SIZE) {
          throw new ProtocolException(
              "Invalid message length for window ack size: " + chunkInfo.length);
        }
        ackWindowSize = readInt();
        return RtmpMessage.INT_SIZE;

      case RtmpMessage.MESSAGE_TYPE_SET_PEER_BANDWIDTH:
        if (chunkInfo.length != RtmpMessage.MESSAGE_LEN_SET_PEER_BANDWIDTH) {
          throw new ProtocolException(
              "Invalid message length for set peer bandwidth: " + chunkInfo.length);
        }
        int requestedWindowSize = readInt();
        int limitType = readByte();
        notifyRequestedWindowSize(requestedWindowSize, limitType);
        return RtmpMessage.INT_SIZE + RtmpMessage.BYTE_SIZE;

      default:
        Log.e(TAG, "Skipping unrecognized message type: " + chunkInfo.messageType);
        return drainBytes(chunkInfo);
    }
  }

  private int drainBytes(ChunkInfo chunkInfo) throws IOException {
    Preconditions.checkState(chunkByteBuffer.capacity() == chunkSize);
    if (chunkInfo.messageBytesPending == 0) {
      chunkInfo.messageBytesPending = chunkInfo.length;
      chunkInfo.isAborting = true;
    }
    int readCount = Math.min(chunkInfo.messageBytesPending, chunkSize);
    if (readCount > 0) {
      readFully(chunkByteBuffer, 0, readCount);
    }
    chunkInfo.messageBytesPending -= readCount;
    if (chunkInfo.messageBytesPending <= 0) {
      chunkInfo.messageBytesPending = 0;
      chunkInfo.isAborting = false;
    }

    return readCount;
  }

  // Post an error on the callback handler
  private synchronized void notifyError(final Throwable t) {
    if (callbackHandler != null) {
      callbackHandler.post(new Runnable() {
        @Override
        public void run() {
          if (callback != null) {
            callback.onRtmpInputStreamError(t);
          }
        }
      });
    }
  }

  // Update the number of received bytes and request an ack be sent if needed.
  private synchronized void updateBytesReceived(int mesgBytesRead) {
    bytesReceived += mesgBytesRead;
    if (((bytesReceived - bytesAcknowledged) >= ackWindowSize) && !ackRequested) {
      ackRequested = true;
      if (callbackHandler != null) {
        callbackHandler.post(new Runnable() {
          @Override
          public void run() {
            if (callback != null) {
              callback.onRtmpInputStreamAcknowledgementNeeded(bytesReceived);
            }
          }
        });
      }
    }
  }

  // Post a peer acknowledgement event on the callback handler
  private synchronized void notifyPeerAck(final int sequenceNumber) {
    if (callbackHandler != null) {
      callbackHandler.post(new Runnable() {
        @Override
        public void run() {
          if (callback != null) {
            callback.onRtmpInputStreamPeerAcknowledgement(sequenceNumber);
          }
        }
      });
    }
  }

  // Post a window size request event on the callback handler
  private synchronized void notifyRequestedWindowSize(final int reqWinSize, final int limitType) {
    if (callbackHandler != null) {
      callbackHandler.post(new Runnable() {
        @Override
        public void run() {
          if (callback != null) {
            callback.onRtmpInputStreamWindowSizeRequested(reqWinSize, limitType);
          }
        }
      });
    }
  }

  private ChunkInfo readChunkHeader() throws IOException {
    byte basicHeader = readByte();
    int chunkFormat = RtmpMessage.getChunkMessageHeaderFormat(basicHeader);
    int chunkStreamId = RtmpMessage.getChunkBasicHeaderStreamId(basicHeader);
    if (chunkStreamId == RtmpMessage.CHUNK_STREAM_ID_EXTENDED) {
      chunkStreamId = RtmpMessage.getExtendedChunkStreamId(readByte());
    } else if (chunkStreamId == RtmpMessage.CHUNK_STREAM_ID_FULL) {
      chunkStreamId = RtmpMessage.getFullChunkStreamId(readByte(), readByte());
    }

    ChunkInfo header = chunkHeaderMap.get(chunkStreamId);
    if (header == null) {
      header = new ChunkInfo();
      header.chunkStreamId = chunkStreamId;
      header.messageStreamId = -1;
      header.messageType = -1;
      header.timestamp = -1;
      header.timestampDelta = -1;
      header.length = -1;
      chunkHeaderMap.put(chunkStreamId, header);
    }

    switch (chunkFormat) {
      case RtmpMessage.CHUNK_FORMAT_FULL:
        readFully(chunkHeaderByteBuffer, 0, RtmpMessage.CHUNK_FORMAT_FULL_SIZE);
        int timestamp = RtmpMessage.getThreeByteInt(chunkHeaderByteBuffer, 0);
        if (RtmpMessage.isTimestampExtended(timestamp)) {
          timestamp = readInt();
        }
        header.timestamp = timestamp;
        header.timestampDelta = 0;
        header.length = RtmpMessage.getThreeByteInt(chunkHeaderByteBuffer, 3);
        header.messageType = (chunkHeaderByteBuffer.get(6) & 0xff);

        // Message stream ID is in little endian format
        header.messageStreamId = (chunkHeaderByteBuffer.get(7) & 0xff)
            | ((chunkHeaderByteBuffer.get(8) & 0xff) << 8)
            | ((chunkHeaderByteBuffer.get(9) & 0xff) << 16)
            | (chunkHeaderByteBuffer.get(10) << 24);
        break;

      case RtmpMessage.CHUNK_FORMAT_NO_STREAM_ID:
        readFully(chunkHeaderByteBuffer, 0, RtmpMessage.CHUNK_FORMAT_NO_STREAM_ID_SIZE);
        if (header.messageStreamId < 0) {
          throw new ProtocolException("Missing message stream ID from earlier chunk");
        }
        if (header.timestamp < 0) {
          throw new ProtocolException("Missing timestamp from earlier chunk");
        }
        int timestampDelta = RtmpMessage.getThreeByteInt(chunkHeaderByteBuffer, 0);
        if (RtmpMessage.isTimestampExtended(timestampDelta)) {
          timestampDelta = readInt();
        }
        header.timestampDelta = timestampDelta;
        header.timestamp += timestampDelta;
        header.length = RtmpMessage.getThreeByteInt(chunkHeaderByteBuffer, 3);
        header.messageType = chunkHeaderByteBuffer.get(6) & 0xff;
        break;

      case RtmpMessage.CHUNK_FORMAT_TIME_DELTA:
        readFully(chunkHeaderByteBuffer, 0, RtmpMessage.CHUNK_FORMAT_TIME_DELTA_SIZE);
        if (header.messageStreamId < 0) {
          throw new ProtocolException("Missing message stream ID from earlier chunk");
        }
        if (header.messageType < 0) {
          throw new ProtocolException("Missing message type ID from earlier chunk");
        }
        if (header.timestamp < 0) {
          throw new ProtocolException("Missing timestamp from earlier chunk");
        }
        if (header.length < 0) {
          throw new ProtocolException("Missing length from earlier chunk");
        }
        header.timestampDelta = RtmpMessage.getThreeByteInt(chunkHeaderByteBuffer, 0);
        if (RtmpMessage.isTimestampExtended(header.timestampDelta)) {
          header.timestampDelta = readInt();
        }
        header.timestamp += header.timestampDelta;
        break;

      case RtmpMessage.CHUNK_FORMAT_NO_HEADER:
        if (header.messageStreamId < 0) {
          throw new ProtocolException("Missing message stream ID from earlier chunk");
        }
        if (header.messageType < 0) {
          throw new ProtocolException("Missing message type ID from earlier chunk");
        }
        if (header.timestamp < 0) {
          throw new ProtocolException("Missing timestamp from earlier chunk");
        }
        if (header.timestampDelta < 0) {
          throw new ProtocolException("Missing timestamp delta from earlier chunk");
        }
        if (header.length < 0) {
          throw new ProtocolException("Missing length from earlier chunk");
        }
        if (RtmpMessage.isTimestampExtended(header.timestampDelta)) {
          readInt(); // Skip
        }
        break;

      default:
        throw new ProtocolException("Unrecognized chunk format: " + chunkFormat);
    }

    return header;
  }

  @VisibleForTesting
  void setChunkSize(int chunkSize) {
    this.chunkSize = chunkSize;
  }

  @VisibleForTesting
  int getChunkSize() {
    return chunkSize;
  }

  @VisibleForTesting
  void setAckWindowSize(int ackWindowSize) {
    this.ackWindowSize = ackWindowSize;
  }

  @VisibleForTesting
  int getAckWindowSize() {
    return ackWindowSize;
  }

  @VisibleForTesting
  void setBytesReceived(int bytesReceived) {
    this.bytesReceived = bytesReceived;
  }

  @VisibleForTesting
  int getBytesReceived() {
    return bytesReceived;
  }

  @VisibleForTesting
  boolean getShouldStopProcessing() {
    return shouldStopProcessing;
  }

}
