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

import android.util.SizeF;
import com.google.common.io.Files;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.FieldOfView;
import com.google.vr180.MeshProto;
import com.google.vr180.common.logging.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** A set of utility functions for projection metadata providers. */
public class ProjectionMetadataProviderUtils {
  private static final String TAG = "ProjectionMetadataProviderUtils";

  /** Returns a byte array of the contents of the file at 'path' or null. */
  public static byte[] loadMP4Box(String path) {
    try {
      Log.i(TAG, "Load mp4 box from " + path);
      return Files.toByteArray(new File(path));
    } catch (IOException e) {
      Log.e(TAG, "Failed to read mp4 box: ", e);
      return null;
    }
  }

  /** Returns a StereoMeshConfig parsed from the contents of the file at 'path' or null. */
  public static MeshProto.StereoMeshConfig loadStereoMeshConfig(String path) {
    try {
      Log.i(TAG, "Load stereo config from " + path);
      FileInputStream is = new FileInputStream(new File(path));
      MeshProto.StereoMeshConfig config = MeshProto.StereoMeshConfig.parseFrom(is);
      is.close();
      return config;
    } catch (IOException e) {
      Log.e(TAG, "Failed to read stereo config: ", e);
      return null;
    }
  }

  /** Returns the field of view for the given capture mode or null if the fov is unspecified. */
  public static SizeF getFov(CaptureMode mode) {
    switch (mode.getActiveCaptureType()) {
      case VIDEO:
        return toSizeF(mode.getConfiguredVideoMode().getFieldOfView());
      case LIVE:
        return toSizeF(mode.getConfiguredLiveMode().getVideoMode().getFieldOfView());
      case PHOTO:
        return toSizeF(mode.getConfiguredPhotoMode().getFieldOfView());
      default:
        return null;
    }
  }

  private static SizeF toSizeF(FieldOfView fov) {
    return fov == null ? null : new SizeF(fov.getHorizontalFov(), fov.getVerticalFov());
  }
}
