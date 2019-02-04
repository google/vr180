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

package com.google.vr180.api.camerainterfaces;

import com.google.vr180.CameraApi.CameraApiRequest.ThumbnailRequest;
import com.google.vr180.CameraApi.Media;
import java.util.Date;
import java.util.List;
import javax.annotation.Nullable;

/** Interface for fetching media data from the camera. */
public interface MediaProvider {

  /** Indicates that a new media file is available. */
  String ACTION_NEW_MEDIA = "vr180.NEW_MEDIA";

  /** Gets the number of media items available. */
  long getMediaCount();

  /**
   * Gets the last time the collection changed. This can be a best effort, as long as it changes
   * when media is changed.
   */
  Date getLastModified();

  /**
   * Gets the metadata about media items.
   *
   * @param startIndex The index of the first item to return.
   * @param count The maximum number of items to return.
   * @return The metadata about media items between [startIndex,startIndex+count).
   */
  List<Media> getMedia(long startIndex, long count);

  /**
   * Gets the Media for the given media store ID, or null if it does not exist
   * in the Media Store.
   *
   * @param mediaStoreId The media store ID to get the Media of.
   * @return The metadata about the media item.
   */
  @Nullable
  Media getMedia(long mediaStoreId);

  /**
   * Gets a list of the MediaStore IDs in a certain range.
   *
   * @param startIndex The index of the first item to return.
   * @param count The maximum number of items to return.
   * @return A list of all the Media IDs.
   */
  List<Long> getMediaIds(long startIndex, long count);

  /**
   * Gets the Media Store ID for the given filename, or -1 if filename does not exist in the Media
   * Store.
   *
   * @param filename The filename to get the Media Store ID for.
   */
  long getMediaStoreId(String filename);

  /**
   * Produces a thumbnail of the requested size for the given filename.
   */
  byte[] getThumbnail(ThumbnailRequest request);

  /**
   * Notifies that the media state has changed.
   */
  void onMediaStateChanged();
}
