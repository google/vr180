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

package com.google.vr180.common.media;

import com.google.vr180.CameraApi.Media;

/** Provides helper methods to determine if a file is a video or photo. */
public class MediaTypeHelper {
  private static final String VIDEO_MIME_PREFIX = "video/";

  private static final String PHOTO_MIME_PREFIX = "image/";

  /** Returns whether the provided media is a video type. */
  public static boolean isVideo(Media media) {
    String mimeType = MediaStoreUtil.getMimeType(media.getFilename());
    return isVideo(mimeType);
  }

  /** Returns whether the provided mimetype is a video. */
  public static boolean isVideo(String mimeType) {
    return mimeType != null && mimeType.startsWith(VIDEO_MIME_PREFIX);
  }

  /** Returns whether the provided media is a photo type. */
  public static boolean isPhoto(Media media) {
    String mimeType = MediaStoreUtil.getMimeType(media.getFilename());
    return isPhoto(mimeType);
  }

  /** Returns whether the provided mimetype is a photo. */
  public static boolean isPhoto(String mimeType) {
    return mimeType != null && mimeType.startsWith(PHOTO_MIME_PREFIX);
  }
}
