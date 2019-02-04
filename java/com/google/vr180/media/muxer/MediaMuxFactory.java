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
import android.net.Uri;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.metadata.MetadataInjector;

/** Factory for {@link MediaMux} implementations. */
public class MediaMuxFactory {
  private static final String TAG = "MediaMuxFactory";
  private static MediaMuxFactory factory;

  public static MediaMuxFactory getInstance() {
    if (factory == null) {
      factory = new MediaMuxFactory();
    }
    return factory;
  }

  private MediaMuxFactory() {}

  /**
   * Create a new media mux based on the format of the given target URI.
   *
   * @return The new muxer or {@code null} on error.
   */
  public MediaMux createMediaMux(
      Context context, String targetUri, String targetKey, MetadataInjector metadataInjector) {
    try {
      if (targetUri.startsWith(RtmpMuxer.SCHEME)) {
        Uri uri = Uri.parse(targetUri);
        return new AutoReconnectRtmpMuxer(context, uri, targetKey);
      } else {
        return new ChapteredFileMuxer(
            context, targetUri, true /* need motion */, metadataInjector);
      }
    } catch (Exception e) {
      Log.e(TAG, "Could not create media mux", e);
      return null;
    }
  }
}
