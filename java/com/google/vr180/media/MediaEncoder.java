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

import android.support.annotation.Nullable;
import com.google.vr180.media.muxer.MediaMux;
import java.nio.ByteBuffer;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Interface representing a media track suitable for use with a {@link MediaMux}.  Encapsulates
 * functionality of {@link android.media.MediaCodec} for an encoder.
 * <p>
 * Implementations of this interface are not expected to be thread safe.  Clients must ensure that
 * a single thread manages the interactions or otherwise handle concurrency.
 * </p>
 */
@NotThreadSafe
public interface MediaEncoder {

  /** Callback to notify when asynchronous request to end stream completes. */
  interface EndOfStreamCallback {
    /** End of stream marker has been delivered out of the given encoder. */
    void onEndOfStream(MediaEncoder mediaEncoder);
  }

  /** Callback to notify client when an error is encountered while started. */
  interface ErrorCallback {
    /** Error was encountered by the given encoder. */
    void onError(MediaEncoder mediaEncoder, int errorCode);
  }

  /** Get the name of this encoder */
  String getName();

  /**
   * Start the encoder.
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean start();

  /** Specifies a callback to be invoked on error. */
  void setErrorCallback(@Nullable ErrorCallback errorCallback);

  /**
   * Stop the encoder.
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean stop();

  /**
   * Check whether the codec is actively encoding.
   * @return {@code true} if the encoder has been started but not stopped or released,
   *   and {@code false} otherwise
   */
  boolean isActive();

  /**
   * Request that the end of stream be signaled from the encoder.  No further input data will be
   * processed after this point.
   * @param callback Handler for the completion of the request, i.e. once the EOS marker has
   *   exited the encoder.
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean signalEndOfStream(EndOfStreamCallback callback);

  /**
   * Release the encoder resources.
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean release();

  /**
   * Get the current target bitrate.
   * @return the number of bits per second requested from the encoder
   */
  int getTargetBitrate();

  /**
   * Change the target bitrate for the encoder at runtime. Not supported on all encoders.
   * @param bitrate The number of bits per seconds to be requested from the encoder
   */
  void setTargetBitrate(int bitrate);

  /**
   * Get output buffer for a given dequeued index. See more details at {@link
   * android.media.MediaCodec#getOutputBuffer}.
   */
  ByteBuffer getOutputBuffer(int index);

  /**
   * Release a dequeued output buffer after being used. See more details at {@link
   * android.media.MediaCodec#releaseOutputBuffer}.
   */
  void releaseOutputBuffer(int index);
}
