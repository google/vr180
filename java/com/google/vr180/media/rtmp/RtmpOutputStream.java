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

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.MediaCreationUtils;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/** Output stream for an RTMP client connection. */
/* package */ class RtmpOutputStream {

  private static final String TAG = "RtmpOutputStream";
  private static final String THREAD_NAME = "rtmpOutput";
  private static final int JOIN_WAIT_TIME_MS = 200;

  private static final String CODEC_CONFIG_BUFFER0 = "csd-0";
  private static final String CODEC_CONFIG_BUFFER1 = "csd-1";
  private static final boolean USE_AVCC = true;
  private static final int PIPE_SIZE = 10 * 1024 * 1024; // 10 MB buffer
  private static final int NETWORK_THREAD_CHUNK_SIZE = 8 * 1024;
  // Ack is needed if unacknowledged bytes exceed this portion of ackWindowSize.
  private static final float ACK_NEEDED_RATIO = 0.75f;

  private final ByteBuffer outputBuffer;
  private final ActionMessageFormat.Writer amfWriter = new ActionMessageFormat.Writer();
  private final SocketChannel socketChannel;
  private final FasterPipedInputStream pipedInput;
  private final FasterPipedOutputStream pipedOutput;
  private final TimestampContinuityManager timestampContinuityManager;

  private int chunkSize = RtmpMessage.DEFAULT_CHUNK_SIZE;
  private long bytesSent;
  private long lastBytesSent;
  private long bytesAcknowledged;
  private ByteBuffer chunkDataByteBuffer = ByteBuffer.allocate(chunkSize);
  private int ackWindowSize = RtmpMessage.MIN_WINDOW_SIZE;
  private int lastLimitType = RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD;

  @SuppressWarnings("unused")
  private boolean ackNeeded;

  @SuppressWarnings("unused")
  private boolean discardNeeded;

  private final String versionString;
  private boolean needFirstFrame = true;
  private Thread networkThread;
  private volatile boolean shouldStopProcessing;
  private Callbacks callback;
  private Handler callbackHandler;
  private boolean useThroughputBuffer;
  private boolean throughputBufferStarted;
  private int currentBytesOut;

  private final Object threadLock = new Object();

  /** Constructs a new {@code RtmpOutputStream} for the specified socket. */
  public static RtmpOutputStream newInstance(Context context, SocketChannel socketChannel)
      throws IOException {
    RtmpContinuityManager continuityManager = RtmpContinuityManager.newInstance(context);
    FasterPipedInputStream pipedInput = new FasterPipedInputStream(PIPE_SIZE);
    FasterPipedOutputStream pipedOutput = new FasterPipedOutputStream(pipedInput);
    return new RtmpOutputStream(context, socketChannel, continuityManager, pipedOutput, pipedInput);
  }

  /**
   * Constructs a new {@code RtmpOutputStream} on the {@code SocketChannel} {@code output}. If
   * pipedInput and pipedOutput are non-null, the output stream should be piped through it, and this
   * object will create a thread to send data from it to the socket.
   */
  @VisibleForTesting
  RtmpOutputStream(
      Context context,
      SocketChannel socketChannel,
      TimestampContinuityManager timestampContinuityManager,
      FasterPipedOutputStream pipedOutput,
      FasterPipedInputStream pipedInput)
      throws IOException {
    this.pipedOutput = pipedOutput;
    this.pipedInput = pipedInput;
    this.socketChannel = Preconditions.checkNotNull(socketChannel);
    this.timestampContinuityManager = Preconditions.checkNotNull(timestampContinuityManager);
    outputBuffer = ByteBuffer.allocate(2 * RtmpMessage.MAX_HEADER_SIZE);
    outputBuffer.order(ByteOrder.BIG_ENDIAN);
    throughputBufferStarted = false;
    useThroughputBuffer = (pipedInput != null && pipedOutput != null);

    versionString = MediaCreationUtils.getEncoderString(context, "RTMP");
  }

  private void runNetworkThread() {
    byte[] networkData = new byte[NETWORK_THREAD_CHUNK_SIZE];
    ByteBuffer networkDataBuffer = ByteBuffer.wrap(networkData);
    int bytesRead;
    try {
      while (!shouldStopProcessing
          && (bytesRead = pipedInput.read(networkData, 0, NETWORK_THREAD_CHUNK_SIZE)) > -1) {
        networkDataBuffer.position(0);
        networkDataBuffer.limit(bytesRead);
        currentBytesOut += bytesRead;
        while (socketChannel.isConnected()
            && !shouldStopProcessing
            && networkDataBuffer.remaining() > 0) {
          socketChannel.write(networkDataBuffer);
        }
      }
    } catch (ClosedByInterruptException | InterruptedIOException e) {
      if (!shouldStopProcessing) {
        Log.e(TAG, "IO exception in network thread: ", e);
      }
      Thread.interrupted();
    } catch (Throwable e) {
      if (!shouldStopProcessing) {
        Log.e(TAG, "Unexpected throwable in writer loop: ", e);
        notifyError(e);
      }
    } finally {
      try {
        pipedInput.close();
      } catch (Exception e) {
        Log.w(TAG, "Exception closing piped input: ", e);
      }
    }
  }

  /** Begin the buffer thread for processing outgoing messages. */
  public void startProcessing() {
    synchronized (threadLock) {
      if (networkThread != null) {
        return;
      }
      if (useThroughputBuffer) {
        networkThread =
            new Thread(THREAD_NAME) {
              @Override
              public void run() {
                runNetworkThread();
              }
            };
        networkThread.start();
        throughputBufferStarted = true;
      }

      shouldStopProcessing = false;
    }
  }

  /** Prepare for expected socket closure for exception handling. */
  public void prepareStopProcessing() {
    synchronized (threadLock) {
      shouldStopProcessing = true;
    }
  }

  /** Stop processing on the internal thread. */
  public void stopProcessing() {
    Preconditions.checkState(shouldStopProcessing);
    synchronized (threadLock) {
      if (networkThread == null) {
        return;
      }

      while (true) {
        try {
          networkThread.join(JOIN_WAIT_TIME_MS);
          break;
        } catch (InterruptedException e) {
          // Ignore
        }
      }

      if (networkThread != null && networkThread.isAlive()) {
        networkThread.interrupt();
        while (true) {
          try {
            networkThread.join(JOIN_WAIT_TIME_MS);
            break;
          } catch (InterruptedException e) {
            // Ignore
          }
        }
        if (networkThread != null && !networkThread.isAlive()) {
          networkThread = null;
        }
      }
    }
  }

  /** Returns pair inBytes and outBytes summed since the last request. */
  public Pair<Integer, Integer> getCurrentDeltaThroughput() {
    Pair<Integer, Integer> outPair;
    if (useThroughputBuffer) {
      outPair = new Pair<>((int) (bytesSent - lastBytesSent), currentBytesOut);
      currentBytesOut = 0;
    } else {
      currentBytesOut = (int) (bytesSent - lastBytesSent);
      // TODO: consider using the RTMP window scheme bytes acknowledged.
      outPair = new Pair<>(currentBytesOut, currentBytesOut);
    }
    lastBytesSent = bytesSent;
    return outPair;
  }

  /** Returns the number of bytes sent on this output stream. */
  public long getBytesSent() {
    return bytesSent;
  }

  /** Returns the number of bytes used in the network buffer. */
  public int getBufferUsed() {
    if (!useThroughputBuffer) {
      return 0;
    }

    int bytesAvailable = 0;
    try {
      bytesAvailable = pipedInput.available();
    } catch (IOException e) {
      Log.e(TAG, "Could not determine bytes available in buffer: " + e.getMessage());
    }

    return bytesAvailable;
  }

  /** Sets a limit to the size of the output buffer. */
  public void setBufferLimit(int bytes) {
    if (useThroughputBuffer) {
      pipedInput.setBufferLimit(bytes);
    }
  }

  /** Send the C0 RTMP message. */
  public void sendClientHandshake0() throws IOException {
    outputBuffer.clear();
    outputBuffer.put(RtmpMessage.RTMP_VERSION);
    outputBuffer.flip();
    write(outputBuffer);
  }

  /** Send the C1 RTMP message. */
  public void sendClientHandshake1(byte[] challengeBytes) throws IOException {
    Preconditions.checkNotNull(challengeBytes);
    Preconditions.checkArgument(
        challengeBytes.length == RtmpMessage.HANDSHAKE_LEN - 2 * RtmpMessage.INT_SIZE);

    outputBuffer.clear();

    // Most implementations prefer the epoch to start at 0
    outputBuffer.putInt(0);

    // The spec calls for this value to be 0, but apparently that is "outdated."  Real
    // implementations place a non-zero client version here.  Otherwise, support is (perhaps)
    // restricted to flv video.
    // TODO: Consider using RtmpMessage.CLIENT_VERSION and managing the SHA digest handshake
    outputBuffer.putInt(0);

    outputBuffer.flip();
    write(outputBuffer);

    // Fill in the SHA HMAC challenge
    Random random = new Random();
    random.nextBytes(challengeBytes);

    // Send the challenge bytes
    write(ByteBuffer.wrap(challengeBytes));
  }

  /** Sets the chunk size for this outbound stream. */
  public void sendSetChunkSize(int newChunkSize) throws IOException {
    if (!RtmpMessage.isValidLength(newChunkSize) || (newChunkSize < RtmpMessage.MIN_CHUNK_SIZE)) {
      throw new ProtocolException("Invalid chunk size to set: " + newChunkSize);
    }

    // This message can be delivered in a single chunk because the minimum chunk size is
    // guaranteed to exceed the data size
    // Ignore possibility of exceeding the ack window, as it is important to deliver control
    // messages rather than discard or wait.  According to spec, this should be safe.
    Preconditions.checkArgument(RtmpMessage.MIN_CHUNK_SIZE >= RtmpMessage.INT_SIZE);

    outputBuffer.clear();
    assembleFullHeader(
        outputBuffer,
        RtmpMessage.CHUNK_STREAM_ID_CONTROL,
        0 /* timestamp */,
        RtmpMessage.MESSAGE_LEN_SET_CHUNK_SIZE,
        RtmpMessage.MESSAGE_TYPE_SET_CHUNK_SIZE,
        RtmpMessage.MESSAGE_STREAM_CONTROL);
    outputBuffer.putInt(newChunkSize);

    outputBuffer.flip();
    write(outputBuffer);

    chunkSize = newChunkSize;
    chunkDataByteBuffer = ByteBuffer.allocate(chunkSize);
    updateBytesSent(RtmpMessage.INT_SIZE);
  }

  /** Sends an acknowledgement for bytes received from the remote peer. */
  public void sendAcknowledgement(int bytesToAcknowledge) throws IOException {
    // This message can be delivered in a single chunk because the minimum chunk size is
    // guaranteed to exceed the data size
    // Ignore possibility of exceeding the ack window, as it is important to deliver control
    // messages rather than discard or wait.  According to spec, this should be safe.
    Preconditions.checkArgument(RtmpMessage.MIN_CHUNK_SIZE >= RtmpMessage.INT_SIZE);
    outputBuffer.clear();
    assembleFullHeader(
        outputBuffer,
        RtmpMessage.CHUNK_STREAM_ID_CONTROL,
        0 /* timestamp */,
        RtmpMessage.MESSAGE_LEN_ACKNOWLEDGEMENT,
        RtmpMessage.MESSAGE_TYPE_ACKNOWLEDGEMENT,
        RtmpMessage.MESSAGE_STREAM_CONTROL);
    outputBuffer.putInt(bytesToAcknowledge);

    outputBuffer.flip();
    write(outputBuffer);
    updateBytesSent(RtmpMessage.INT_SIZE);
  }

  /** Sets the window size according to a request from the remote peer. */
  public void setWindowSize(int requestedWindowSize, int limitType) throws IOException {
    if (requestedWindowSize < RtmpMessage.MIN_WINDOW_SIZE) {
      Log.e(TAG, "Ignoring small window size: " + requestedWindowSize);
      return;
    }
    if (limitType == RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_DYNAMIC) {
      if (lastLimitType == RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD) {
        limitType = RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD;
      } else {
        // Ignore dynamic change when the last request was SOFT
        Log.d(TAG, "Ignoring dynamic window size limit");
        return;
      }
    }
    if (limitType == RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_HARD) {
      ackWindowSize = requestedWindowSize;
    } else if (limitType == RtmpMessage.WINDOW_SIZE_LIMIT_TYPE_SOFT) {
      ackWindowSize = Math.min(ackWindowSize, requestedWindowSize);
    } else {
      Log.e(TAG, "Ignoring unrecognized window size limit type");
      return;
    }
    lastLimitType = limitType;

    // This message can be delivered in a single chunk because the minimum chunk size is
    // guaranteed to exceed the data size
    // Ignore possibility of exceeding the ack window, as it is important to deliver control
    // messages rather than discard or wait.  According to spec, this should be safe.
    Preconditions.checkArgument(RtmpMessage.MIN_CHUNK_SIZE >= RtmpMessage.INT_SIZE);
    outputBuffer.clear();
    assembleFullHeader(
        outputBuffer,
        RtmpMessage.CHUNK_STREAM_ID_CONTROL,
        0 /* timestamp */,
        RtmpMessage.MESSAGE_LEN_WINDOW_ACK_SIZE,
        RtmpMessage.MESSAGE_TYPE_WINDOW_ACK_SIZE,
        RtmpMessage.MESSAGE_STREAM_CONTROL);
    outputBuffer.putInt(requestedWindowSize);

    outputBuffer.flip();
    write(outputBuffer);

    updateBytesSent(RtmpMessage.INT_SIZE);
  }

  /** Send an RTMP connect command for the given stream name. */
  public void sendConnect(Uri targetUri, String streamKey, int transactionId) throws IOException {
    if (targetUri == null) {
      throw new ProtocolException("Target URI cannot be null");
    }
    String path = targetUri.getPath();
    if (TextUtils.isEmpty(path)) {
      throw new ProtocolException("Target path cannot be empty");
    }
    while (path.startsWith("/")) {
      path = path.substring(1);
    }
    if (TextUtils.isEmpty(path)) {
      throw new ProtocolException("Target path cannot be empty");
    }
    if (TextUtils.isEmpty(streamKey)) {
      throw new ProtocolException("Stream key cannot be empty");
    }

    // Ignore possibility of exceeding the ack window, as it is important to deliver control
    // messages rather than discard or wait.  According to spec, this should be safe.
    amfWriter.reset();
    amfWriter.writeString(RtmpMessage.NETCONNECTION_CONNECT_NAME);
    amfWriter.writeNumber(transactionId);
    amfWriter.writeObjectBegin();
    amfWriter.writePropertyName(RtmpMessage.NETCONNECTION_PROPERTY_APP);
    amfWriter.writeString(path);
    amfWriter.writePropertyName(RtmpMessage.NETCONNECTION_PROPERTY_FLASH_VERSION);
    amfWriter.writeString(versionString);
    amfWriter.writePropertyName(RtmpMessage.NETCONNECTION_PROPERTY_FLASH_VERSION_ALT);
    amfWriter.writeString(versionString);
    amfWriter.writePropertyName(RtmpMessage.NETCONNECTION_PROPERTY_TC_URL);
    amfWriter.writeString(targetUri.toString());
    amfWriter.writePropertyName(RtmpMessage.NETCONNECTION_PROPERTY_TYPE);
    amfWriter.writeString(RtmpMessage.NETCONNECTION_TYPE_NONPRIVATE);
    amfWriter.writeObjectEnd();

    ByteBuffer amfBuffer = amfWriter.toByteBuffer();
    int size = amfBuffer.limit();

    outputBuffer.clear();
    assembleFullHeader(
        outputBuffer,
        RtmpMessage.CHUNK_STREAM_ID_AMF,
        0 /* timestamp */,
        size,
        RtmpMessage.RTMP_MESSAGE_COMMAND_AMF0,
        RtmpMessage.MESSAGE_STREAM_AUDIO_VIDEO);
    outputBuffer.flip();
    write(outputBuffer);
    write(amfBuffer);

    updateBytesSent(size);
  }

  /** Sets the number of received bytes that have been acknowledged by the peer. */
  public void setBytesAcknowledged(int bytesAcknowledged) {
    this.bytesAcknowledged = bytesAcknowledged;
    ackNeeded = false;
    discardNeeded = false;

    // Make sure an ack isn't needed already.
    updateBytesSent(0);
  }

  /** Send an RTMP release stream command for the given stream name. */
  public void sendReleaseStream(String streamKey, int transactionId) throws IOException {
    if (TextUtils.isEmpty(streamKey)) {
      throw new ProtocolException("Stream key cannot be empty");
    }

    // Ignore possibility of exceeding the ack window, as it is important to deliver control
    // messages rather than discard or wait.  According to spec, this should be safe.
    amfWriter.reset();
    amfWriter.writeString(RtmpMessage.NETCONNECTION_RELEASE_STREAM_NAME);
    amfWriter.writeNumber(transactionId);
    amfWriter.writeNull();
    amfWriter.writeString(streamKey);

    ByteBuffer amfBuffer = amfWriter.toByteBuffer();
    int size = amfBuffer.limit();

    outputBuffer.clear();
    assembleFullHeader(
        outputBuffer,
        RtmpMessage.CHUNK_STREAM_ID_AMF,
        0 /* timestamp */,
        size,
        RtmpMessage.RTMP_MESSAGE_COMMAND_AMF0,
        RtmpMessage.MESSAGE_STREAM_AUDIO_VIDEO);
    outputBuffer.flip();
    write(outputBuffer);
    write(amfBuffer);

    updateBytesSent(size);
  }

  /** Send an RTMP NetConnect create stream command. */
  public void sendCreateStream(int transactionId) throws IOException {
    // Ignore possibility of exceeding the ack window, as it is important to deliver control
    // messages rather than discard or wait.  According to spec, this should be safe.
    amfWriter.reset();
    amfWriter.writeString(RtmpMessage.NETCONNECTION_CREATE_STREAM_NAME);
    amfWriter.writeNumber(transactionId);
    amfWriter.writeNull();

    ByteBuffer amfBuffer = amfWriter.toByteBuffer();
    int size = amfBuffer.limit();

    outputBuffer.clear();
    assembleFullHeader(
        outputBuffer,
        RtmpMessage.CHUNK_STREAM_ID_AMF,
        0 /* timestamp */,
        size,
        RtmpMessage.RTMP_MESSAGE_COMMAND_AMF0,
        RtmpMessage.MESSAGE_STREAM_AUDIO_VIDEO);
    outputBuffer.flip();
    write(outputBuffer);
    write(amfBuffer);

    updateBytesSent(size);
  }

  /** Send an RTMP publish command to the remote server according to the given stream key. */
  public void sendPublish(String streamKey, int transactionId) throws IOException {
    if (TextUtils.isEmpty(streamKey)) {
      throw new ProtocolException("Stream key cannot be empty");
    }

    amfWriter.reset();
    amfWriter.writeString(RtmpMessage.NETCONNECTION_PUBLISH_STREAM_NAME);
    amfWriter.writeNumber(transactionId);
    amfWriter.writeNull();
    amfWriter.writeString(streamKey);
    amfWriter.writeString(RtmpMessage.NETCONNECTION_PUBLISH_TYPE);

    assembleHeadersAndWriteBuffer(
        outputBuffer,
        RtmpMessage.CHUNK_STREAM_ID_AMF,
        /*timestamp=*/ 0,
        RtmpMessage.RTMP_MESSAGE_COMMAND_AMF0,
        RtmpMessage.MESSAGE_STREAM_AUDIO_VIDEO,
        amfWriter.toByteBuffer());
  }

  /** Send the audio and video meta data for the RTMP stream. */
  public void sendStreamMetaData(
      int audioCodecId, MediaFormat audioFormat, int videoCodecId, MediaFormat videoFormat)
      throws IOException {
    if (!MediaCreationUtils.isAudioFormat(audioFormat)
        || !audioFormat.containsKey(MediaFormat.KEY_BIT_RATE)
        || !audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
      throw new ProtocolException("Invalid audio format: " + audioFormat);
    }
    if (!MediaCreationUtils.isVideoFormat(videoFormat)
        || !videoFormat.containsKey(MediaFormat.KEY_WIDTH)
        || !videoFormat.containsKey(MediaFormat.KEY_HEIGHT)
        || !videoFormat.containsKey(MediaFormat.KEY_BIT_RATE)
        || !videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
      throw new ProtocolException("Invalid video format: " + videoFormat);
    }

    amfWriter.reset();
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
    amfWriter.writeNumber(videoCodecId);
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_AUDIO_DATA_RATE);
    amfWriter.writeNumber(audioFormat.getInteger(MediaFormat.KEY_BIT_RATE));
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_AUDIO_SAMPLE_RATE);
    int audioSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    amfWriter.writeNumber(audioSampleRate);
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_AUDIO_SAMPLE_SIZE);
    amfWriter.writeNumber(RtmpMessage.getAudioSampleSize(audioCodecId));
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_IS_STEREO);
    amfWriter.writeBoolean(RtmpMessage.getAudioIsStereo(audioCodecId));
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_AUDIO_CODEC_ID);
    amfWriter.writeNumber(audioCodecId);
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_ENCODER);
    amfWriter.writeString(versionString);
    amfWriter.writePropertyName(RtmpMessage.META_DATA_PROPERTY_FILE_SIZE);
    amfWriter.writeNumber(0);
    amfWriter.writeObjectEnd();

    assembleHeadersAndWriteBuffer(
        outputBuffer,
        RtmpMessage.CHUNK_STREAM_ID_AMF,
        /*timestamp=*/ 0,
        RtmpMessage.RTMP_MESSAGE_DATA_AMF0,
        RtmpMessage.MESSAGE_STREAM_AUDIO_VIDEO,
        amfWriter.toByteBuffer());
  }

  /** Send sample data to remote RTMP server. */
  @TargetApi(21)
  public void sendSampleData(
      boolean isAudio,
      int audioCodec,
      MediaFormat audioFormat,
      int videoCodec,
      MediaFormat videoFormat,
      ByteBuffer buffer,
      BufferInfo bufferInfo)
      throws IOException {

    if (((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0)) {
      // Skip the codec config
      return;
    }

    // Wait for a video key frame before starting
    if (needFirstFrame) {
      if (isAudio) {
        Log.d(TAG, "Skipping audio while waiting for key frame");
        return;
      }
      if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) == 0) {
        Log.d(TAG, "Skipping non key frame video while waiting for key frame");
        return;
      }
      if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
        Log.d(TAG, "Skipping EOS on key frame video while waiting for key frame");
        return;
      }

      // Write out the video config data
      sendVideoConfig(videoCodec, videoFormat);

      // Write out the audio config data
      sendAudioConfig(audioCodec, audioFormat);

      long startTimeMs = TimeUnit.MICROSECONDS.toMillis(bufferInfo.presentationTimeUs);
      timestampContinuityManager.startNewStream(startTimeMs);
      needFirstFrame = false;
    }

    int chunkStreamId;
    int messageType;
    byte[] controlTag;
    if (isAudio) {
      chunkStreamId = RtmpMessage.CHUNK_STREAM_ID_AUDIO;
      messageType = RtmpMessage.RTMP_MESSAGE_AUDIO;
      controlTag = RtmpMessage.getAudioControlTag(audioCodec, false /* isConfig */);
    } else {
      chunkStreamId = RtmpMessage.CHUNK_STREAM_ID_VIDEO;
      messageType = RtmpMessage.RTMP_MESSAGE_VIDEO;
      boolean isKeyFrame = ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);
      controlTag = RtmpMessage.getVideoControlTag(videoCodec, false /* isConfig */, isKeyFrame);
    }
    long timestamp = TimeUnit.MICROSECONDS.toMillis(bufferInfo.presentationTimeUs);
    int adjustedTimestamp = timestampContinuityManager.adjustTimestamp(timestamp);
    if (adjustedTimestamp < 0) {
      Log.e(
          TAG,
          "Skipping media data with early timestamp:"
              + " type="
              + (isAudio ? "AUDIO" : "VIDEO")
              + ", timestamp="
              + timestamp
              + ", startTime="
              + timestampContinuityManager.getStartTimeMs());
      return;
    }

    if (USE_AVCC) {
      skipStartCode(buffer);
    }
    sendMediaData(buffer, controlTag, chunkStreamId, messageType, adjustedTimestamp);
  }

  /** Flush the socket output stream. */
  public void flush() throws IOException {
    Socket socket = socketChannel.socket();
    if (socket == null) {
      return;
    }
    OutputStream outputStream = socketChannel.socket().getOutputStream();
    if (outputStream != null) {
      outputStream.flush();
    }
  }

  /** Write a single integer to the socket output in network byte order. */
  public void writeInt(int value) throws IOException {
    outputBuffer.clear();
    outputBuffer.putInt(value);
    outputBuffer.flip();
    write(outputBuffer);
  }

  /**
   * Sets the callback handler and the Handler to process them.
   *
   * @param cb The callback handler, or {@code null} to remove
   * @param handler The Handler on which to process the callback, or {@code null} to process on the
   *     calling Handler
   */
  public void setCallbackHandler(
      @Nullable RtmpOutputStream.Callbacks cb, @Nullable Handler handler) {
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

  // Post an error on the callback handler
  private synchronized void notifyError(final Throwable t) {
    if (callbackHandler != null) {
      callbackHandler.post(
          () -> {
            if (callback != null) {
              callback.onRtmpOutputStreamError(t);
            }
          });
    }
  }

  // If present, skip over the Annex B start code at the beginning of this buffer.
  private byte[] startCodeBuf = new byte[3];

  private void skipStartCode(ByteBuffer buffer) throws ProtocolException {
    // Annex B NALUs start with 0x00, 0x00, 0x01 OR 0x00, 0x00, 0x00, 0x01
    buffer.position(0);
    buffer.get(startCodeBuf, 0, 3);
    if (startCodeBuf[0] != 0 || startCodeBuf[1] != 0) {
      buffer.position(0);
      return;
    }
    if (startCodeBuf[2] == 1) {
      // 3-byte version
      return;
    }
    if (startCodeBuf[2] != 0) {
      buffer.position(0);
      return;
    }
    if (buffer.get() != 1) {
      throw new ProtocolException("Unexpected value in NALU header");
    }
  }

  private void sendMediaData(
      ByteBuffer buffer, byte[] controlTag, int chunkStreamId, int messageType, int timestamp)
      throws IOException {
    // Create initial chunk header
    int size = buffer.remaining() + controlTag.length;
    if (buffer.position() != 0) {
      size += RtmpMessage.INT_SIZE;
    }

    outputBuffer.clear();
    assembleFullHeader(
        outputBuffer,
        chunkStreamId,
        timestamp,
        size,
        messageType,
        RtmpMessage.MESSAGE_STREAM_AUDIO_VIDEO);

    // Fill in the control byte
    if (chunkSize <= (controlTag.length + RtmpMessage.INT_SIZE)) {
      throw new ProtocolException("Chunk size is too small to hold FLV control tag and size");
    }
    outputBuffer.put(controlTag);

    int bytesRemaining = buffer.remaining();
    int offset = controlTag.length;
    if (buffer.position() != 0) {
      // Note that technically there could be multiple NALUs in this message, and a scan would
      // be needed to find them all and convert to length-prefixed NALUs.  However, this is
      // costly and unlikely in practice.  So, allow the decoder to be confused and hopefully
      // recover at the next key frame.
      outputBuffer.putInt(bytesRemaining);
      offset += RtmpMessage.INT_SIZE;
    }
    outputBuffer.flip();
    write(outputBuffer);

    // Create header for subsequent chunks
    outputBuffer.clear();
    assembleChunkHeader(outputBuffer, RtmpMessage.CHUNK_FORMAT_NO_HEADER, chunkStreamId);
    if (RtmpMessage.isTimestampExtended(timestamp)) {
      buffer.putInt(timestamp);
    }

    // Send data in chunks
    while (bytesRemaining > 0) {
      int byteCount = Math.min(bytesRemaining, chunkSize - offset);
      buffer.limit(buffer.position() + byteCount);
      write(buffer);
      bytesRemaining -= byteCount;
      offset = 0;
      if (bytesRemaining > 0) {
        outputBuffer.flip();
        write(outputBuffer);
      }
    }

    updateBytesSent(size);
  }

  private void sendVideoConfig(int videoCodec, MediaFormat videoFormat) throws IOException {
    if (!videoFormat.containsKey(CODEC_CONFIG_BUFFER0)
        || !videoFormat.containsKey(CODEC_CONFIG_BUFFER1)) {
      throw new ProtocolException("Video format missing codec config data");
    }
    ByteBuffer videoConfigSpsBuffer = videoFormat.getByteBuffer(CODEC_CONFIG_BUFFER0);
    ByteBuffer videoConfigPpsBuffer = videoFormat.getByteBuffer(CODEC_CONFIG_BUFFER1);
    byte[] videoControlTag =
        RtmpMessage.getVideoControlTag(videoCodec, true /* isConfig */, true /* isKeyFrame */);
    if (USE_AVCC) {
      skipStartCode(videoConfigSpsBuffer);
      skipStartCode(videoConfigPpsBuffer);
      ByteBuffer avccBuffer = RtmpMessage.createAvccBox(videoConfigSpsBuffer, videoConfigPpsBuffer);
      sendMediaData(
          avccBuffer,
          videoControlTag,
          RtmpMessage.CHUNK_STREAM_ID_VIDEO,
          RtmpMessage.RTMP_MESSAGE_VIDEO,
          /*timestamp=*/ 0);
    } else {
      int messageSize = videoConfigSpsBuffer.limit() + videoConfigPpsBuffer.limit();
      ByteBuffer videoConfigBuffer = ByteBuffer.allocate(messageSize);
      videoConfigBuffer.order(ByteOrder.BIG_ENDIAN);
      videoConfigBuffer.position(0);
      videoConfigBuffer.limit(messageSize);
      videoConfigSpsBuffer.position(0);
      videoConfigBuffer.put(videoConfigSpsBuffer);
      videoConfigPpsBuffer.position(0);
      videoConfigBuffer.put(videoConfigPpsBuffer);
      videoConfigBuffer.position(0);
      sendMediaData(
          videoConfigBuffer,
          videoControlTag,
          RtmpMessage.CHUNK_STREAM_ID_VIDEO,
          RtmpMessage.RTMP_MESSAGE_VIDEO,
          /*timestamp=*/ 0);
    }
  }

  private void sendAudioConfig(int audioCodec, MediaFormat audioFormat) throws IOException {
    if (!audioFormat.containsKey(CODEC_CONFIG_BUFFER0)) {
      throw new ProtocolException("Audio format missing codec config data");
    }
    ByteBuffer audioConfigBuffer = audioFormat.getByteBuffer(CODEC_CONFIG_BUFFER0);
    BufferInfo audioConfigInfo = new BufferInfo();
    audioConfigInfo.size = audioConfigBuffer.limit();
    audioConfigBuffer.position(0);
    sendMediaData(
        audioConfigBuffer,
        RtmpMessage.getAudioControlTag(audioCodec, true /* isConfig */),
        RtmpMessage.CHUNK_STREAM_ID_AUDIO,
        RtmpMessage.RTMP_MESSAGE_AUDIO,
        /*timestamp=*/ 0);
  }

  private void updateBytesSent(int messageBytesSent) {
    bytesSent += messageBytesSent;
    int unacknowledged = (int) (bytesSent - bytesAcknowledged);
    if (unacknowledged >= ackWindowSize * ACK_NEEDED_RATIO) {
      // TODO: Handle overflow situations
      ackNeeded = true;
      if (unacknowledged >= (3 * ackWindowSize / 2)) {
        // Once the window is exceeded by an additional 50%, stop sending data altogether
        discardNeeded = true;
      }
    }
  }

  private final byte[] abrChunk = new byte[8 * 1024];

  @VisibleForTesting
  void write(ByteBuffer buffer) throws IOException {
    if (throughputBufferStarted && useThroughputBuffer) {
      while (!shouldStopProcessing && buffer.remaining() > 0) {
        int count = Math.min(buffer.remaining(), abrChunk.length);
        buffer.get(abrChunk, 0, count);
        pipedOutput.write(abrChunk, 0, count);
      }
      return;
    }

    while (!shouldStopProcessing && buffer.remaining() > 0) {
      if (socketChannel.isConnected()) {
        socketChannel.write(buffer);
      } else {
        throw new IOException("socket closed");
      }
    }
  }

  /**
   * Assbmble headers and write the specified buffer. The buffer will be split in to chunks
   * automatically.
   */
  private void assembleHeadersAndWriteBuffer(
      ByteBuffer bufferForHeader,
      int chunkStreamId,
      int timestamp,
      int messageType,
      int messageStreamId,
      ByteBuffer bufferToWrite)
      throws IOException {
    int bufferSize = bufferToWrite.limit();
    int size = Math.min(bufferSize, chunkSize);
    bufferForHeader.clear();
    assembleFullHeader(
        bufferForHeader, chunkStreamId, timestamp, size, messageType, messageStreamId);
    bufferForHeader.flip();
    write(bufferForHeader);
    // Write up to the chunkSize.
    bufferToWrite.limit(size);
    write(bufferToWrite);
    // Write the remaning data if the data is larger than the chunkSize.
    while (bufferToWrite.position() < bufferSize) {
      // Use chunk header for the remaining chunks.
      bufferForHeader.clear();
      assembleChunkHeader(bufferForHeader, RtmpMessage.CHUNK_FORMAT_NO_HEADER, messageStreamId);
      bufferForHeader.flip();
      write(bufferForHeader);
      // Limit the output by chunkSize.
      bufferToWrite.limit(Math.min(bufferToWrite.limit() + chunkSize, bufferSize));
      write(bufferToWrite);
    }
    updateBytesSent(bufferSize);
  }

  @VisibleForTesting
  void assembleFullHeader(
      ByteBuffer buffer,
      int chunkStreamId,
      int timestamp,
      int length,
      int messageType,
      int messageStreamId)
      throws IOException {
    assembleChunkHeader(buffer, RtmpMessage.CHUNK_FORMAT_FULL, chunkStreamId);
    boolean needExtendedTimestamp = RtmpMessage.isTimestampExtended(timestamp);
    if (needExtendedTimestamp) {
      buffer.put((byte) 0xff);
      buffer.put((byte) 0xff);
      buffer.put((byte) 0xff);
    } else {
      buffer.put((byte) ((timestamp >> 16) & 0xff));
      buffer.put((byte) ((timestamp >> 8) & 0xff));
      buffer.put((byte) (timestamp & 0xff));
    }
    if (!RtmpMessage.isValidLength(length)) {
      throw new ProtocolException("Invalid length for RTMP message: " + length);
    }
    buffer.put((byte) ((length >> 16) & 0xff));
    buffer.put((byte) ((length >> 8) & 0xff));
    buffer.put((byte) (length & 0xff));
    if (!RtmpMessage.isValidMessageType(messageType)) {
      throw new ProtocolException("Invalid message type for RTMP message: " + messageType);
    }
    buffer.put((byte) messageType);
    if (!RtmpMessage.isValidMessageStreamId(messageStreamId)) {
      throw new ProtocolException("Invalid message stream ID for RTMP message: " + messageStreamId);
    }

    // Message stream ID is written in little endian format, as opposed to big endian format that
    // is used for every other field.
    buffer.put((byte) (messageStreamId & 0xff));
    buffer.put((byte) ((messageStreamId >> 8) & 0xff));
    buffer.put((byte) ((messageStreamId >> 16) & 0xff));
    buffer.put((byte) ((messageStreamId >> 24) & 0xff));

    if (needExtendedTimestamp) {
      buffer.putInt(timestamp);
    }
  }

  private void assembleChunkHeader(ByteBuffer buffer, int chunkFormat, int chunkStreamId)
      throws IOException {
    if (RtmpMessage.chunkStreamIdRequiresFullHeader(chunkStreamId)) {
      // 3-byte format
      buffer.put(RtmpMessage.createChunkBasicHeader(chunkFormat, RtmpMessage.CHUNK_STREAM_ID_FULL));
      int encodedId = RtmpMessage.createExtendedChunkStreamId(chunkStreamId);
      if ((encodedId & ~0xffff) != 0) {
        throw new ProtocolException("Attempt to create chunk stream ID out of full range");
      }
      buffer.putShort((short) encodedId);
    } else if (RtmpMessage.chunkStreamIdRequiresExtendedHeader(chunkStreamId)) {
      // 2-byte format
      buffer.put(
          RtmpMessage.createChunkBasicHeader(chunkFormat, RtmpMessage.CHUNK_STREAM_ID_EXTENDED));
      int encodedId = RtmpMessage.createExtendedChunkStreamId(chunkStreamId);
      if ((encodedId & ~0xff) != 0) {
        throw new ProtocolException("Attempt to create chunk stream ID out of extended range");
      }
      buffer.put((byte) encodedId);
    } else {
      // Single byte
      buffer.put(RtmpMessage.createChunkBasicHeader(chunkFormat, chunkStreamId));
    }
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
  ByteBuffer getChunkDataByteBuffer() {
    return chunkDataByteBuffer;
  }

  @VisibleForTesting
  int getLastLimitType() {
    return lastLimitType;
  }

  @VisibleForTesting
  void setLastLimitType(int lastLimitType) {
    this.lastLimitType = lastLimitType;
  }

  @VisibleForTesting
  int getAckWindowSize() {
    return ackWindowSize;
  }

  @VisibleForTesting
  void setAckWindowSize(int ackWindowSize) {
    this.ackWindowSize = ackWindowSize;
  }

  @VisibleForTesting
  boolean getNeedFirstFrame() {
    return needFirstFrame;
  }

  @VisibleForTesting
  void setNeedFirstFrame(boolean needFirstFrame) {
    this.needFirstFrame = needFirstFrame;
  }

  @VisibleForTesting
  void setBytesSent(int bytesSent) {
    this.bytesSent = bytesSent;
  }

  @VisibleForTesting
  boolean getShouldStopProcessing() {
    return shouldStopProcessing;
  }

  /** Callbacks for output stream events. */
  public interface Callbacks {

    /**
     * Stream encountered an error and is no longer viable. Appropriate action is to close and
     * release.
     *
     * @param cause When not null, the cause of the error.
     */
    void onRtmpOutputStreamError(@Nullable Throwable cause);
  }
}
