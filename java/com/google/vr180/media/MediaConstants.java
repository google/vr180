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

/** Common constants for media creation. */
public final class MediaConstants {
  /*
   * Status codes
   */

  /** Request completed successfully. */
  public static final int STATUS_SUCCESS = 0;
  /** Request completed with unknown error. */
  public static final int STATUS_ERROR = 1;
  /** Request could not complete because there is no active screencast capture session. */
  public static final int STATUS_NOT_ACTIVE = 2;
  /** Request could not complete because the request or its options are not supported. */
  public static final int STATUS_UNSUPPORTED = 3;
  /** Request could not complete due to permission problems. */
  public static final int STATUS_PERMISSION_ERROR = 4;
  /** Request could not complete due storage I/O error. */
  public static final int STATUS_STORAGE_ERROR = 5;
  /** Request could not complete due to an error with the display. */
  public static final int STATUS_DISPLAY_ERROR = 6;
  /** Request could not complete due to an error with the codec. */
  public static final int STATUS_CODEC_ERROR = 7;
  /** Request could not complete because the service was busy, e.g. other session still active. */
  public static final int STATUS_BUSY = 8;
  /** Request could not complete due to a streaming error. */
  public static final int STATUS_STREAM_ERROR = 9;
  /** Request could not complete because the streaming target info is missing. */
  public static final int STATUS_NO_STREAMING_TARGET = 10;
  /** Request could not complete due to a timeout. */
  public static final int STATUS_TIMED_OUT = 11;
  /** Request could not complete due a communication error. */
  public static final int STATUS_COMMUNICATION_ERROR = 12;
  /** Output video quality is low. */
  public static final int STATUS_VIDEO_QUALITY_LOW = 13;
  /** Output video quality is poor. Video is likely unusable. */
  public static final int STATUS_VIDEO_QUALITY_POOR = 14;
  /** Output video quality is ok. Video quality was inadequate but has returned to a good state. */
  public static final int STATUS_VIDEO_QUALITY_GOOD = 15;
  /** Output audio rate is low. Quality may suffer. */
  public static final int STATUS_AUDIO_RATE_LOW = 16;
  /** Output audio rate is too low. Audio is likely unusable. */
  public static final int STATUS_AUDIO_RATE_POOR = 17;
  /** Output audio rate is ok. Audio rate was inadequate but has returned to a good state. */
  public static final int STATUS_AUDIO_RATE_GOOD = 18;
  /** Request could not complete due storage or network I/O error. */
  public static final int STATUS_IO_ERROR = 19;
  /** On device camera error. */
  public static final int STATUS_DEVICE_CAMERA_ERROR = 20;
}
