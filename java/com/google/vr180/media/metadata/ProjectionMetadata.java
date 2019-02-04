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

package com.google.vr180.media.metadata;

import android.support.annotation.Nullable;
import android.util.Base64;
import com.google.common.base.Preconditions;
import com.google.vr180.common.media.StereoMode;

/** A data class containing projection metadata for video and photos. */
public class ProjectionMetadata {

  // ST3D boxes
  public static final String ST3D_MONO = "0000000d737433640000000000";
  public static final String ST3D_TOP_BOTTOM = "0000000d737433640000000001";
  public static final String ST3D_LEFT_RIGHT = "0000000d737433640000000002";

  public final int stereoMode;
  public final byte[] st3d;
  @Nullable public final byte[] sv3d;
  @Nullable public final StereoReprojectionConfig stereoReprojectionConfig;

  public ProjectionMetadata(
      int stereoMode,
      @Nullable byte[] sv3d,
      @Nullable StereoReprojectionConfig stereoReprojectionConfig) {
    this.stereoMode = stereoMode;
    this.st3d = getSt3d(stereoMode);
    this.sv3d = sv3d;
    this.stereoReprojectionConfig = stereoReprojectionConfig;
    Preconditions.checkArgument(
        stereoReprojectionConfig == null || stereoReprojectionConfig.getStereoMode() == stereoMode);
  }

  private static byte[] getSt3d(int stereoMode) {
    switch (stereoMode) {
      case StereoMode.LEFT_RIGHT:
        return Base64.decode(ST3D_LEFT_RIGHT, Base64.DEFAULT);
      case StereoMode.TOP_BOTTOM:
        return Base64.decode(ST3D_TOP_BOTTOM, Base64.DEFAULT);
      case StereoMode.MONO:
        return Base64.decode(ST3D_MONO, Base64.DEFAULT);
      default:
        return null;
    }
  }
}
