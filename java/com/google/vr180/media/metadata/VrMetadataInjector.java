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

import com.google.vr180.common.logging.Log;

/** An implementation of MetdataInjector that injects accoring to a ProjectionMetadata */
public class VrMetadataInjector implements MetadataInjector {
  private static final String TAG = "VrMetadataInjector";
  private final ProjectionMetadata projectionMetadata;

  static {
    System.loadLibrary("camera");
  }

  public VrMetadataInjector(ProjectionMetadata projectionMetadata) {
    this.projectionMetadata = projectionMetadata;
  }

  @Override
  public boolean injectMetadata(String filePath, int width, int height) {
    if (projectionMetadata == null || projectionMetadata.sv3d == null) {
      Log.w(TAG, "VR metadata is missing");
      return false;
    }
    StereoReprojectionConfig reprojectionConfig = projectionMetadata.stereoReprojectionConfig;
    return nativeInjectVRMetadataToVideo(
        projectionMetadata.stereoMode,
        projectionMetadata.sv3d,
        width,
        height,
        reprojectionConfig != null ? reprojectionConfig.getFov().getWidth() : 0,
        reprojectionConfig != null ? reprojectionConfig.getFov().getHeight() : 0,
        filePath);
  }

  /**
   * Injects V2 st3d, sv3d mp4 boxes and optionally V1 uuid into the video and reformats android's
   * native 'mett' track to a 'camm' track.
   */
  private static native boolean nativeInjectVRMetadataToVideo(
      int stereoMode, byte[] sv3d, int width, int height, float fovX, float fovY, String path);
}
