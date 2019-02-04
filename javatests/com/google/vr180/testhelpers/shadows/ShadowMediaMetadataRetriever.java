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

package com.google.vr180.testhelpers.shadows;

import android.media.MediaMetadataRetriever;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Shadow for {@link MediaMetadataRetriever} which delegates to a mock instance.
 * This class uses robolectric shadows to glue in a supplied instance (via setMockInstance) which is
 * delegated to whenever a new MediaMetadataRetriever is instantiated.
 *
 * It currently only delgates the minumum api needed for VR180 tests.
 **/
@Implements(MediaMetadataRetriever.class)
public class ShadowMediaMetadataRetriever {

  /** Contains a shared mock instance that this shadow delegates to. */
  private static MediaMetadataRetriever mockInstance;

  public static void setMockInstance(MediaMetadataRetriever instance) {
    mockInstance = instance;
  }

  @Implementation
  public void setDataSource(String path) {
    mockInstance.setDataSource(path);
  }

  @Implementation
  public String extractMetadata(int key) {
    return mockInstance.extractMetadata(key);
  }

  @Implementation
  public void release() {
    mockInstance.release();
  }
}
