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

/**
 * Stereo mode constants.
 */
public final class StereoMode {
  // https://github.com/google/spatial-media/blob/master/docs/spherical-video-v2-rfc.md
  public static final int MONO = 0;
  public static final int TOP_BOTTOM = 1;
  public static final int LEFT_RIGHT = 2;
  public static final int STEREO_CUSTOM = 3;
}
