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
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.rtmp.RtmpInputStream.TransactionResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/**
 * Connection to an RTMP server. Should be created on a background thread, as many of the calls are
 * blocking and will disrupt UI on the main thread.
 */
public class RtmpConnection implements RtmpInputStream.Callbacks, RtmpOutputStream.Callbacks {

  private static final String TAG = "RtmpConnection";

  private static final int RTMP_PORT = 1935;
  private static final int RTMP_VIDEO_INVALID = -1;
  private static final int RTMP_AUDIO_INVALID = -1;
  private static final int CONNECT_TIMEOUT_MILLIS = 8000;
  private static final int HANDSHAKE_TIMEOUT_MILLIS = 5000;
  private static final int CREATE_CONNECTION_TIMEOUT_MILLIS = 5000;
  private static final int CREATE_STREAM_TIMEOUT_MILLIS = 5000;
  private static final int PUBLISH_STREAM_TIMEOUT_MILLIS = 5000;
  @VisibleForTesting static final int OUTGOING_CHUNK_SIZE = 8 * 1024;
  private static final int OUTGOING_WINDOW_SIZE = 10 * 1024 * 1024;

  @SuppressWarnings("unused")
  private static final int IPTOS_LOWCOST = 0x02;

  @SuppressWarnings("unused")
  private static final int IPTOS_RELIABILITY = 0x04;

  @SuppressWarnings("unused")
  private static final int IPTOS_THROUGHPUT = 0x08;

  private static final int IPTOS_LOWDELAY = 0x10;

  private final Context context;
  private final Clock mediaClock;
  private final Handler handler;
  private SocketChannel socketChannel;
  private int nextUnusedTransactionId = RtmpMessage.NETCONNECTION_FIRST_UNUSED_TRANSACTION_ID;
  private Callback callbackHandler;
  private int videoCodec = RTMP_VIDEO_INVALID;
  private int audioCodec = RTMP_AUDIO_INVALID;
  private RtmpInputStream inStream;
  private RtmpOutputStream outStream;
  private boolean isConnected;
  private boolean isPublished;
  private MediaFormat audioFormat;
  private MediaFormat videoFormat;

  /** Callbacks for asynchronous connection events. */
  public interface Callback {
    /** Called when the given connection experiences an error. */
    void onRtmpConnectionError(RtmpConnection connection);
  }

  public RtmpConnection(Context context, String host, int port, Clock mediaClock)
      throws IOException {
    this(
        context,
        host,
        port,
        mediaClock,
        // Open the socket and place in non-blocking mode.
        (SocketChannel) (SocketChannel.open().configureBlocking(false)));
  }

  RtmpConnection(
      Context context, String host, int port, Clock mediaClock, SocketChannel socketChannel)
      throws IOException {
    Preconditions.checkNotNull(socketChannel);
    Preconditions.checkNotNull(host);
    this.context = context;
    this.socketChannel = socketChannel;
    this.mediaClock = mediaClock;

    if (Looper.myLooper() != null) {
      handler = new Handler(Looper.myLooper());
    } else {
      handler = new Handler(Looper.getMainLooper());
    }

    // Configure the socket.
    Socket socket = socketChannel.socket();
    if (socket != null) {
      try {
        socket.setTcpNoDelay(true);
        socket.setTrafficClass(IPTOS_LOWDELAY);
      } catch (Exception e) {
        Log.e(TAG, "Could not set socket options", e);
      }
      Log.d(
          TAG,
          "Socket Info: tc="
              + socket.getTrafficClass()
              + ", NagleOn="
              + !socket.getTcpNoDelay()
              + ", receiveBuf="
              + socket.getReceiveBufferSize()
              + ", sendBuf="
              + socket.getSendBufferSize()
              + ", soTimeout="
              + socket.getSoTimeout());
    }

    // Initiate connect in the background
    if (port < 0) {
      port = RTMP_PORT;
    }
    this.socketChannel.connect(new InetSocketAddress(host, port));
  }

  /** Sets the callback handler for asynchronous errors. */
  public void setCallbackHandler(Callback callbackHandler) {
    this.callbackHandler = callbackHandler;
  }

  /**
   * Sets the type of video to carry in this stream.
   *
   * @return {@code true} if supported and {@code false} otherwise.
   */
  public boolean setVideoType(MediaFormat format) {
    String mimeType = format.getString(MediaFormat.KEY_MIME);
    if (MediaFormat.MIMETYPE_VIDEO_AVC.equals(mimeType)) {
      videoCodec = RtmpMessage.RTMP_VIDEO_CODEC_AVC;
      videoFormat = format;
      return true;
    }
    return false;
  }

  /**
   * Sets the type of audio to carry in this stream.
   *
   * @return {@code true} if supported and {@code false} otherwise.
   */
  public boolean setAudioType(MediaFormat format) {
    String mimeType = format.getString(MediaFormat.KEY_MIME);
    if (MediaFormat.MIMETYPE_AUDIO_AAC.equals(mimeType)) {
      audioCodec = RtmpMessage.RTMP_AUDIO_CODEC_AAC;
      audioFormat = format;
      return true;
    }
    return false;
  }

  /**
   * Establish the connection to the RTMP server endpoint and perform initial handshake. This is a
   * blocking call.
   */
  public synchronized void connect() throws IOException, TimeoutException, InterruptedException {
    if (isConnected) {
      Log.d(TAG, "RTMP channel already connected");
      return;
    }

    // Finish the connect
    if (!socketChannel.isConnected()) {
      Selector selector = Selector.open();
      socketChannel.register(selector, SelectionKey.OP_CONNECT);
      int readyCount = selector.select(CONNECT_TIMEOUT_MILLIS);
      selector.close();
      if (readyCount != 1) {
        throw new TimeoutException("RTMP connect timed out");
      }
      if (!socketChannel.finishConnect()) {
        throw new IOException("RTMP finish connect failed");
      }
      if (!socketChannel.isConnected()) {
        throw new IOException("RTMP connect failed");
      }
    }

    // Set up the input and output streams. Disable throughput buffer when in measurement mode.
    inStream = new RtmpInputStream(socketChannel);
    inStream.setCallbackHandler(this, handler);

    outStream = RtmpOutputStream.newInstance(context, socketChannel);
    outStream.setCallbackHandler(this, handler);

    // Perform the initial handshake.
    doBlockingHandshake();

    // Start processing incoming messages.
    inStream.startProcessing();
    // Start the buffer thread processing of outgoing messages.
    outStream.startProcessing();
    isConnected = true;
  }

  /**
   * Publish an outgoing stream on an open connection.
   *
   * @param targetUri URI that identifies the server to which the stream should be delivered
   * @param streamKey Stream name/key used by the server to identify the owner.
   */
  public void publish(Uri targetUri, String streamKey)
      throws IOException, InterruptedException, ExecutionException, TimeoutException {
    if (!isConnected) {
      throw new IllegalStateException("RTMP channel is not connected");
    }
    if (isPublished) {
      Log.e(TAG, "Stream is already published");
      return;
    }
    if (audioFormat == null) {
      throw new IllegalStateException("RTMP audio format is missing");
    }
    if (videoFormat == null) {
      throw new IllegalStateException("RTMP video format is missing");
    }

    outStream.sendSetChunkSize(OUTGOING_CHUNK_SIZE);
    outStream.setWindowSize(OUTGOING_WINDOW_SIZE, RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD);

    int transactionId = RtmpMessage.NETCONNECTION_CONNECT_TRANSACTION_ID;
    Future<TransactionResult> pendingResult = inStream.createTransaction(transactionId);
    outStream.sendConnect(targetUri, streamKey, transactionId);
    TransactionResult result =
        pendingResult.get(CREATE_CONNECTION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    if (result.getStatus() != TransactionResult.STATUS_SUCCESS
        || !RtmpMessage.AMF_NETCONNECTION_STATUS_SUCCESS.equals(result.getStatusMessage())) {
      throw new ProtocolException("RTMP NetConnection failed: result=" + result);
    }
    inStream.clearTransaction(transactionId);

    outStream.sendReleaseStream(streamKey, getNextTransactionId());

    transactionId = getNextTransactionId();
    pendingResult = inStream.createTransaction(transactionId);
    outStream.sendCreateStream(transactionId);
    result = pendingResult.get(CREATE_STREAM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    if (result.getStatus() != TransactionResult.STATUS_SUCCESS) {
      throw new ProtocolException("RTMP NetConnection.createStream failed: result=" + result);
    }
    inStream.clearTransaction(transactionId);

    transactionId = RtmpMessage.NETCONNECTION_ONSTATUS_TRANSACTION_ID;
    pendingResult = inStream.createTransaction(transactionId);
    outStream.sendPublish(streamKey, transactionId);
    result = pendingResult.get(PUBLISH_STREAM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    if (result.getStatus() != TransactionResult.STATUS_SUCCESS
        || !RtmpMessage.AMF_PUBLISH_STATUS_SUCCESS.equals(result.getStatusMessage())) {
      throw new ProtocolException("RTMP publish request failed: result=" + result);
    }
    inStream.clearTransaction(transactionId);

    outStream.sendStreamMetaData(audioCodec, audioFormat, videoCodec, videoFormat);
    isPublished = true;
  }

  private int getNextTransactionId() {
    return nextUnusedTransactionId++;
  }

  public synchronized long getBytesSent() {
    return outStream.getBytesSent();
  }

  public synchronized int getOutputBufferUsed() {
    if (outStream != null) {
      return outStream.getBufferUsed();
    } else {
      return -1;
    }
  }

  /**
   * Gets the current delta throughput bytes of the output stream since last request or {@code
   *     null} if output stream is null.
   */
  public synchronized Pair<Integer, Integer> getCurrentDeltaThroughput() {
    if (outStream == null) {
      return null;
    }
    return outStream.getCurrentDeltaThroughput();
  }

  /** Sets the output buffer limit in bytes */
  public synchronized void setOutputBufferLimit(int bytes) {
    if (outStream != null) {
      outStream.setBufferLimit(bytes);
    }
  }

  /**
   * Send sample data to the RTMP server.
   *
   * @param isAudio Indicates whether this is audio data ({@code true}) or video data ({@code
   *     false}).
   * @param buffer Sample data
   * @param bufferInfo Info related to sample data
   */
  public void sendSampleData(boolean isAudio, ByteBuffer buffer, BufferInfo bufferInfo)
      throws IOException {
    if (!isPublished) {
      throw new IllegalStateException("RTMP stream must be published before sending data");
    }
    outStream.sendSampleData(
        isAudio, audioCodec, audioFormat, videoCodec, videoFormat, buffer, bufferInfo);
  }

  /** End the connection to the RTMP server endpoint. This is a blocking call. */
  public synchronized void disconnect() throws IOException {
    Log.d(TAG, "RTMP channel close");
    if (!isConnected) {
      Log.d(TAG, "RTMP channel already disconnected");
      return;
    }

    /**
     * Stopping a multi-threaded socket channel has some special behaviors to consider. 1) If the
     * input side of a socket is shut down by one thread while another thread is blocked in a read
     * operation on the socket's channel, then the read operation in the blocked thread will
     * complete without reading any bytes and will return -1. 2) If the output side of a socket is
     * shut down by one thread while another thread is blocked in a write operation on the socket's
     * channel, then the blocked thread will receive an AsynchronousCloseException. 3) A thread can
     * be closed using multiple interrupted exceptions including: InterruptedException,
     * InterruptedIOException, ClosedByInterruptException. 4) Calling read/write on a closed socket
     * channel after possibly being reused can cause unknown behaviors since the native API could
     * reuse SocketChannels. 5) Autoreconnecting may call connect() right after disconnect(), thus
     * stopping should be fully complete/safe for the next connect() call and not make any calls on
     * a socketChannel. 6) Two threads should not both perform write/flush operations, same with
     * read operations.
     */
    // Inform the in/out streams that a stop is coming before closing the socketChannel.
    inStream.prepareStopProcessing();
    outStream.prepareStopProcessing();
    // Trigger closing of the socket channel.
    socketChannel.close();
    // Fully stop the processing and threads.
    inStream.stopProcessing();
    outStream.stopProcessing();
    isConnected = false;
    isPublished = false;
  }

  /** Release any resources used by this connection. The channel is unusable after this returns. */
  public synchronized void release() throws IOException {
    if (isConnected) {
      disconnect();
    }
    socketChannel = null;
    inStream = null;
    outStream = null;
  }

  @Override
  public void onRtmpOutputStreamError(@Nullable Throwable cause) {
    Log.e(TAG, "RTMP output stream experienced an error", cause);
    if (callbackHandler != null) {
      callbackHandler.onRtmpConnectionError(this);
    }
  }

  @Override
  public void onRtmpInputStreamError(@Nullable Throwable cause) {
    Log.e(TAG, "RTMP input stream experienced an error", cause);
    if (callbackHandler != null) {
      callbackHandler.onRtmpConnectionError(this);
    }
  }

  @Override
  public void onRtmpInputStreamPeerAcknowledgement(int peerBytesReceived) {
    if (outStream != null) {
      outStream.setBytesAcknowledged(peerBytesReceived);
    }
  }

  @Override
  public void onRtmpInputStreamAcknowledgementNeeded(int localBytesReceived) {
    if (outStream == null) {
      return;
    }
    try {
      outStream.sendAcknowledgement(localBytesReceived);
      if (inStream != null) {
        inStream.setBytesAcknowledged(localBytesReceived);
      }
    } catch (Exception e) {
      Log.e(TAG, "Error sending acknowledgment", e);
      if (callbackHandler != null) {
        callbackHandler.onRtmpConnectionError(this);
      }
    }
  }

  @Override
  public void onRtmpInputStreamWindowSizeRequested(int requestedWindowSize, int limitType) {
    if (outStream == null) {
      return;
    }
    try {
      outStream.setWindowSize(requestedWindowSize, limitType);
    } catch (Exception e) {
      Log.e(TAG, "Error setting window size", e);
      if (callbackHandler != null) {
        callbackHandler.onRtmpConnectionError(this);
      }
    }
  }

  private void doBlockingHandshake() throws IOException, TimeoutException {
    Selector selector;
    int readyCount;

    // Send C0 and C1.
    socketChannel.configureBlocking(true);
    outStream.sendClientHandshake0();
    byte[] challengeBytes = new byte[RtmpMessage.HANDSHAKE_LEN - 2 * RtmpMessage.INT_SIZE];
    outStream.sendClientHandshake1(challengeBytes);
    outStream.flush();

    // Await S0.
    socketChannel.configureBlocking(false);
    selector = Selector.open();
    socketChannel.register(selector, SelectionKey.OP_READ);
    readyCount = selector.select(HANDSHAKE_TIMEOUT_MILLIS);
    if (readyCount != 1) {
      throw new TimeoutException("RTMP handshake S0/S1 timed out");
    }
    selector.close();
    socketChannel.configureBlocking(true);
    inStream.receiveServerHandshake0();

    // Receive and echo S1 as C2.
    socketChannel.configureBlocking(false);
    selector = Selector.open();
    socketChannel.register(selector, SelectionKey.OP_READ);
    readyCount = selector.select(HANDSHAKE_TIMEOUT_MILLIS);
    if (readyCount != 1) {
      throw new TimeoutException("RTMP handshake S0/S1 timed out");
    }
    selector.close();
    socketChannel.configureBlocking(true);
    int serverEpoch = inStream.readInt();
    int s2Timestamp = (int) mediaClock.getCurrentTimeMillis();
    outStream.writeInt(serverEpoch);
    outStream.writeInt(s2Timestamp);
    int serverVersion = inStream.readInt();
    if (serverVersion != 0) {
      // Proto spec says this field MUST be 0, but actual implementations includes the server
      // version instead.  So, be permissive here.
      Log.d(TAG, "Expected 0 in S1 message but got server version: " + serverVersion);
    }
    for (int byteCount = 2 * RtmpMessage.INT_SIZE;
        byteCount < RtmpMessage.HANDSHAKE_LEN;
        byteCount += RtmpMessage.INT_SIZE) {
      int echo = inStream.readInt();
      outStream.writeInt(echo);
    }
    outStream.flush();

    // Read and verify S2.
    socketChannel.configureBlocking(false);
    selector = Selector.open();
    socketChannel.register(selector, SelectionKey.OP_READ);
    readyCount = selector.select(HANDSHAKE_TIMEOUT_MILLIS);
    if (readyCount != 1) {
      throw new TimeoutException("RTMP handshake S0/S1 timed out");
    }
    selector.close();
    socketChannel.configureBlocking(true);
    inStream.receiveServerHandshake2(challengeBytes);

    // Leave the stream in blocking mode.
  }

  @VisibleForTesting
  void setIsConnected(boolean isConnected) {
    this.isConnected = isConnected;
  }

  @VisibleForTesting
  void setIsPublihed(boolean isPublihed) {
    this.isPublished = isPublihed;
  }

  @VisibleForTesting
  boolean isPublished() {
    return isPublished;
  }

  @VisibleForTesting
  void setInStream(RtmpInputStream inStream) {
    this.inStream = inStream;
  }

  @VisibleForTesting
  void setOutStream(RtmpOutputStream outStream) {
    this.outStream = outStream;
  }
}
