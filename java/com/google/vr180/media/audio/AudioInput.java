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

package com.google.vr180.media.audio;

import android.support.annotation.Nullable;
import com.google.vr180.media.MediaEncoder;
import java.nio.ByteBuffer;

/**
 * Interface representing an audio input source suitable for use with {@link MediaEncoder}.
 * Encapsulates functionality of {@link android.media.MediaRecorder} executing on a separate thread.
 *
 * <p>Implementations of this interface are not expected to be thread safe. Clients must ensure that
 * a single thread manages the interactions or otherwise handle concurrency.
 */
public interface AudioInput {

  /** Callback to notify client when an error is encountered. */
  interface ErrorCallback {
    /** Error was encountered. */
    void onError(int errorCode);
  }

  /** Callback to provide filled audio buffers. */
  interface FillBufferCallback {
    /**
     * Provides filled buffers to the client.
     *
     * @param bufferId ID of the buffer from the originating request {@see #fillBufferRequest}
     * @param buffer Buffer filled with audio data.
     * @param flags Buffer flags
     * @param offset Byte offset in buffer where data begins
     * @param count Number of bytes of returned data, or < 0 on error.
     * @param timestamp Timestamp of the data, which can be used to feed to an encoder.
     */
    void onBufferFilled(
        int bufferId, ByteBuffer buffer, int flags, int offset, int count, long timestamp);
  }

  /** Asychronous request to fill the buffer with the given ID with audio data. */
  void fillBufferRequest(int bufferId, ByteBuffer buffer);

  /** Sets the callback to be invoked when a buffer request complets. */
  void setFillBufferResponseHandler(FillBufferCallback callback);

  /** Specifies a callback to be invoked on error. */
  void setErrorCallback(@Nullable ErrorCallback errorCallback);

  /** Sets whether the audio input should be enabled or just return empty data. */
  void setIsEnabled(boolean isEnabled);

  /** Check whether the audio input is enabled. */
  boolean isEnabled();

  /**
   * Start the input source.
   *
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean start();

  /**
   * Stop the input source.
   *
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean stop();

  /**
   * Release the input source resources.
   *
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean release();
}
