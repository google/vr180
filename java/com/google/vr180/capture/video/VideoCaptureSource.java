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

package com.google.vr180.capture.video;

import android.hardware.camera2.CaptureResult;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.Surface;

/**
 * Interface representing a source of video frames suitable for use by a capture pipeline. The
 * pipeline manager controls the state via the {@link #prepare()}}, {@link #start()}, etc. methods.
 */
public interface VideoCaptureSource {
  /** Callback to notify client when a new video frame capture result is available. */
  interface CaptureResultCallback {
    /** Capture result is available from the given video source. */
    void onCaptureResult(CaptureResult result);
  }

  /** Callback to notify client when an error is encountered while started. */
  interface ErrorCallback {
    /** Error was encountered by the given video source. */
    void onError(VideoCaptureSource videoCaptureSource, int errorCode);
  }

  /** Set the surface on which to render frames from the video source. */
  void setTargetSurface(Surface targetSurface);

  /** Set the client to be notified when a new frame capture result is available. */
  void setCaptureResultCallback(
      @Nullable CaptureResultCallback callback, @Nullable Handler handler);

  /**
   * Prepare the video sources, allocating and initializing resources as needed.
   *
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean prepare();

  /**
   * Start the video source, enabling notifications to frame availability client.
   *
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean start();

  /**
   * Stop the video source, disabling notifications on frame updates.
   *
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean stop();

  /**
   * Release the video source resources.
   *
   * @return {@code true} on success and {@code false} otherwise
   */
  boolean release();

  /** Specifies a callback to be invoked on async error. */
  void setErrorCallback(@Nullable ErrorCallback errorCallback, @Nullable Handler handler);

  /**
   * Copy the most recent frame from the video source to given target Surface, assigning the given
   * timestamp.
   */
  void copyFrame(long timestampNanos);

  /** Returns the frame number. */
  int getFrameNumber();
}
