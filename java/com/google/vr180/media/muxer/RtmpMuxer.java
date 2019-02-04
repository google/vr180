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

import android.content.Context;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Pair;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.MediaConstants;
import com.google.vr180.media.MediaCreationUtils;
import com.google.vr180.media.MediaEncoder;
import com.google.vr180.media.rtmp.RealClock;
import com.google.vr180.media.rtmp.RtmpConnection;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/**
 * Implementation of the {@link MediaMux} interface that supports creating a streaming RTMP
 * container format from an AVC video track and an AAC audio track.
 */
public final class RtmpMuxer implements MediaMux, RtmpConnection.Callback {
  private static final String TAG = "RtmpMuxer";

  /** URI scheme employed by this muxer */
  public static final String SCHEME = "rtmp";

  private final RtmpConnection rtmpConnection;
  private final Uri targetUri;
  private final String streamKey;

  private boolean isPrepared;
  private boolean isStarted;
  private boolean isStopped;
  private boolean isReleased;
  private MediaEncoder videoEncoder;
  private MediaEncoder audioEncoder;
  private int videoTrack = -1;
  private int audioTrack = -1;
  private int nextTrack = 0;
  private ErrorCallback errorCallback;
  private volatile long bytesWritten;

  public RtmpMuxer(Context context, Uri targetUri, String streamKey) throws IOException {
    this(
        targetUri,
        streamKey,
        new RtmpConnection(context, targetUri.getHost(), targetUri.getPort(), new RealClock()));
  }

  // Visible for testing.
  RtmpMuxer(Uri targetUri, String streamKey, RtmpConnection rtmpConnection) throws IOException {
    Preconditions.checkArgument(SCHEME.equals(targetUri.getScheme()));
    this.targetUri = targetUri;
    this.streamKey = streamKey;
    this.rtmpConnection = rtmpConnection;
    this.rtmpConnection.setCallbackHandler(this);
  }

  @Override
  public void setErrorCallback(@Nullable ErrorCallback errorCallback) {
    this.errorCallback = errorCallback;
  }

  @Override
  public int addTrack(MediaFormat format, MediaEncoder encoder) {
    if (isStarted) {
      Log.e(TAG, "Cannot add a track once started");
      return -1;
    }
    if (isStopped) {
      Log.e(TAG, "Cannot add a track once stopped");
      return -1;
    }
    if (isReleased) {
      Log.e(TAG, "Cannot add a track after release");
      return -1;
    }

    if (MediaCreationUtils.isVideoFormat(format)) {
      if (videoTrack >= 0) {
        Log.e(TAG, "Video track already added");
        return -1;
      }
      if (!rtmpConnection.setVideoType(format)) {
        Log.e(TAG, "Video format not supported by RTMP connection");
        return -1;
      }
      videoTrack = nextTrack++;
      videoEncoder = encoder;
      return videoTrack;
    }

    if (MediaCreationUtils.isAudioFormat(format)) {
      if (audioTrack >= 0) {
        Log.e(TAG, "Audio track already added");
        return -1;
      }
      if (!rtmpConnection.setAudioType(format)) {
        Log.e(TAG, "Audio format not supported by RTMP connection");
        return -1;
      }
      audioTrack = nextTrack++;
      audioEncoder = encoder;
      return audioTrack;
    }

    Log.e(TAG, "Unknown media format type: " + format);
    return -1;
  }

  @Override
  public boolean hasAllTracks() {
    return (!isReleased && !isStopped && (audioTrack >= 0) && (videoTrack >= 0));
  }

  @Override
  public boolean release() {
    if (isReleased) {
      // Allow multiple calls without error.
      return true;
    }

    try {
      rtmpConnection.release();
      isReleased = true;
    } catch (Exception e) {
      Log.e(TAG, "Releasing the RTMP connection failed", e);
    }
    return isReleased;
  }

  @Override
  public int prepare() {
    if (isReleased) {
      Log.e(TAG, "Cannot prepare once released");
      return MediaConstants.STATUS_ERROR;
    }
    if (isStopped) {
      Log.e(TAG, "Cannot prepare once stopped");
      return MediaConstants.STATUS_ERROR;
    }
    if (isStarted) {
      Log.e(TAG, "Cannot prepare once started");
      return MediaConstants.STATUS_ERROR;
    }
    if (isPrepared) {
      // Allow multiple calls without error.
      return MediaConstants.STATUS_SUCCESS;
    }

    try {
      bytesWritten = 0;
      rtmpConnection.connect();
      isPrepared = true;
    } catch (TimeoutException e) {
      Log.e(TAG, "Connecting to remote host timed out", e);
      return MediaConstants.STATUS_TIMED_OUT;
    } catch (ProtocolException e) {
      Log.e(TAG, "RTMP protocol error during initial handshake", e);
      return MediaConstants.STATUS_COMMUNICATION_ERROR;
    } catch (IOException e) {
      Log.e(TAG, "Connecting to remote host failed due to IO error", e);
      return MediaConstants.STATUS_COMMUNICATION_ERROR;
    } catch (InterruptedException e) {
      Log.e(TAG, "Connection was interrupted", e);
      return MediaConstants.STATUS_COMMUNICATION_ERROR;
    } catch (Exception e) {
      Log.e(TAG, "Preparing the RTMP connection failed", e);
      return MediaConstants.STATUS_COMMUNICATION_ERROR;
    }
    return MediaConstants.STATUS_SUCCESS;
  }

  @Override
  public boolean start() {
    if (isReleased) {
      Log.e(TAG, "Cannot start once released");
      return false;
    }
    if (isStopped) {
      Log.e(TAG, "Cannot restart once stopped");
      return false;
    }
    if (!isPrepared) {
      Log.e(TAG, "Muxer not prepared");
      return false;
    }
    if (isStarted) {
      // Allow multiple calls without error
      return true;
    }
    if (!hasAllTracks()) {
      Log.e(TAG, "Cannot start without all tracks");
      return false;
    }

    try {
      rtmpConnection.publish(targetUri, streamKey);
      isStarted = true;
    } catch (Exception e) {
      Log.e(TAG, "Starting the RTMP connection failed", e);
    }

    return isStarted;
  }

  @Override
  public boolean stop() {
    if (isReleased) {
      Log.e(TAG, "Cannot stop once released");
      return false;
    }
    if (!isStarted) {
      Log.e(TAG, "Muxer not started");
      return false;
    }
    if (isStopped) {
      // Allow multiple calls without error
      return true;
    }

    try {
      rtmpConnection.disconnect();
      isStopped = true;
    } catch (Exception e) {
      Log.e(TAG, "Stopping the RTMP connection failed", e);
    }

    return isStopped;
  }

  @Override
  public boolean isStarted() {
    return isStarted && !isStopped && !isReleased;
  }

  @Override
  public boolean writeSampleDataAsync(int trackIndex, int bufferIndex, BufferInfo bufferInfo) {
    if (isReleased) {
      Log.e(TAG, "Cannot write data once released");
      return false;
    }
    if (isStopped) {
      Log.e(TAG, "Cannot write data once stopped");
      return false;
    }
    if (!isStarted) {
      Log.e(TAG, "Muxer not started");
      return false;
    }

    MediaEncoder encoder = (trackIndex == videoTrack ? videoEncoder : audioEncoder);
    ByteBuffer buffer = encoder.getOutputBuffer(bufferIndex);
    boolean result = writeSampleData(trackIndex, buffer, bufferInfo);
    encoder.releaseOutputBuffer(bufferIndex);
    return result;
  }

  private boolean writeSampleData(int trackIndex, ByteBuffer buffer, BufferInfo bufferInfo) {

    try {
      rtmpConnection.sendSampleData((trackIndex == audioTrack), buffer, bufferInfo);
      long newValue = bytesWritten + (bufferInfo.size - bufferInfo.offset);
      bytesWritten = newValue;
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Sending sample data failed", e);
    }
    return false;
  }

  @Override
  public long getBytesWritten() {
    return bytesWritten;
  }

  @Override
  public int getOutputBufferUsed() {
    return rtmpConnection.getOutputBufferUsed();
  }

  @Override
  public Pair<Integer, Integer> getCurrentByteThroughput() {
    return rtmpConnection.getCurrentDeltaThroughput();
  }

  @Override
  public void setOutputBufferLimit(int bytes) {
    rtmpConnection.setOutputBufferLimit(bytes);
  }

  @Override
  public void onRtmpConnectionError(RtmpConnection connection) {
    if (errorCallback != null) {
      errorCallback.onError(MediaConstants.STATUS_STREAM_ERROR);
    }
  }

  @Override
  public void cleanupPartialResults() {
    // Nothing to do.
  }

  // Visible for testing.
  void setAudioTrack(int audioTrack, MediaEncoder audioEncoder) {
    this.audioTrack = audioTrack;
    this.audioEncoder = audioEncoder;
  }

  // Visible for testing.
  void setVideoTrack(int videoTrack, MediaEncoder videoEncoder) {
    this.videoTrack = videoTrack;
    this.videoEncoder = videoEncoder;
  }

  // Visible for testing.
  void setIsPrepared(boolean isPrepared) {
    this.isPrepared = isPrepared;
  }

  // Visible for testing.
  void setIsStarted(boolean isStarted) {
    this.isStarted = isStarted;
  }

  // Visible for testing.
  void setIsStopped(boolean isStopped) {
    this.isStopped = isStopped;
  }

  // Visible for testing.
  void setIsReleased(boolean isReleased) {
    this.isReleased = isReleased;
  }
}
