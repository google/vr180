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

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import java.util.Arrays;
import java.util.List;

/** Common utility methods for media creation. */
public final class MediaCreationUtils {

  /** Test whether the given media format is for video. */
  public static boolean isVideoFormat(@Nullable MediaFormat format) {
    if (format == null) {
      return false;
    }
    String mimeType = format.getString(MediaFormat.KEY_MIME);
    return mimeType != null && mimeType.startsWith("video/");
  }

  /** Test whether the given media format is for audio. */
  public static boolean isAudioFormat(@Nullable MediaFormat format) {
    if (format == null) {
      return false;
    }
    String mimeType = format.getString(MediaFormat.KEY_MIME);
    return mimeType != null && mimeType.startsWith("audio/");
  }

  /** Test whether the given media format is for motion. */
  public static boolean isMotionFormat(@Nullable MediaFormat format) {
    if (format == null) {
      return false;
    }
    String mimeType = format.getString(MediaFormat.KEY_MIME);
    return mimeType != null && mimeType.startsWith("application/motion");
  }

  /**
   * Create an encoder string that is sent to the ingester and used for monitoring. This records
   * information like version and protocol which are useful when analyzing ingestions.
   */
  public static String getEncoderString(Context context, String protocol) {
    String packageName = context.getPackageName();
    String versionName = "unknown";
    try {
      versionName = context.getPackageManager().getPackageInfo(packageName, 0).versionName;
    } catch (NameNotFoundException e) {
      // Ignore.
    }
    Uri.Builder builder = new Uri.Builder();
    // Please keep alphabetized.
    builder
        .appendQueryParameter("manufacturer", Build.MANUFACTURER) // For example: LGE.
        .appendQueryParameter("model", Build.MODEL) // For example: Nexus 5X.
        .appendQueryParameter("osVersion", VERSION.RELEASE) // For example: 6.01.
        .appendQueryParameter("protocol", protocol); // For example: RTMP.

    String extraData = "extras?" + builder.build().getQuery();
    List<String> versionData = Arrays.asList(packageName, versionName, extraData);
    String versionString = TextUtils.join(":", versionData);
    return versionString;
  }
}
