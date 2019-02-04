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

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.support.annotation.Nullable;
import android.util.Pair;
import com.google.vr180.media.MediaConstants;
import com.google.vr180.media.MediaEncoder;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Interface corresponding to {@link android.media.MediaMuxer}. Includes additional functionality to
 * track the started/stopped state internally, to track whether all required tracks have been added
 * so that the client can start when ready, as well as to clean up partial results when an error is
 * encountered.
 *
 * <p>Implementations of this interface are not expected to be thread safe. Clients must ensure that
 * a single thread manages the interactions or otherwise handle concurrency.
 */
@NotThreadSafe
public interface MediaMux {
  /** Value for an invalid track index. */
  int INVALID_TRACK_INDEX = -1;

  /** Callback for being notified of an error while the muxer is active. */
  interface ErrorCallback {
    void onError(int errorCode);
  }

  /** Set a callback to be invoked on asynchronous muxer error. */
  void setErrorCallback(@Nullable ErrorCallback errorCallback);

  /**
   * {@see android.media.MediaMuxer}. Only the first track of each media type (audio and video) will
   * be added. Once all required tracks (typically specified in the constructor of an implementation
   * class) have been added, {@link #hasAllTracks()} will return {@code true}
   */
  int addTrack(MediaFormat format, MediaEncoder encoder);

  /**
   * @return {@code true} when all required media tracks have been added and {@code false} otherwise
   */
  boolean hasAllTracks();

  /**
   * Perfom any steps necessary to configure or prepare the muxer prior to adding track data or
   * starting it running. For example, costly initialization operations can be performed in this
   * phase so that starting the muxer is not delayed once data becomes available.
   *
   * @return Status code indicating the result, e.g. {@link MediaConstants#STATUS_CODEC_ERROR}
   */
  int prepare();

  /**
   * {@see android.media.MediaMuxer}.
   *
   * @return {@code true} on success and {@code false} otherwise.
   */
  boolean start();

  /**
   * {@see android.media.MediaMuxer}. All methods will fail after this is invoked, and the object
   * should be discarded.
   *
   * @return {@code true} on success and {@code false} otherwise.
   */
  boolean release();

  /**
   * {@see android.media.MediaMuxer}.
   *
   * @return {@code true} on success and {@code false} otherwise.
   */
  boolean stop();

  /** @return {@code true} if the muxer has been started and {@code false} otherwise. */
  boolean isStarted();

  /**
   * {@see android.media.MediaMuxer}. Muxer is responsible for releasing the output buffer.
   *
   * @return {@code true} on success and {@code false} otherwise.
   */
  boolean writeSampleDataAsync(int trackIndex, int bufferIndex, MediaCodec.BufferInfo bufferInfo);

  /**
   * Clean up any partial results. This method is useful in situations where the final result should
   * be discarded in the case of an error.
   */
  void cleanupPartialResults();

  /** Get the number of bytes written since {@link #prepare()} was invoked. */
  long getBytesWritten();

  /** Returns the number of bytes used in the output buffer. */
  int getOutputBufferUsed();

  /** Returns the number of first: input bytes, second: output bytes since the last invocation. */
  Pair<Integer, Integer> getCurrentByteThroughput();

  /** Sets a limit to the size of the output buffer. */
  void setOutputBufferLimit(int bytes);
}
