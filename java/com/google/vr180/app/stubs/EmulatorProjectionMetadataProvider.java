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

package com.google.vr180.app.stubs;

import android.content.Context;
import android.util.SizeF;
import com.google.common.io.ByteStreams;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.ProjectionType;
import com.google.vr180.MeshProto;
import com.google.vr180.common.logging.Log;
import com.google.vr180.common.media.StereoMode;
import com.google.vr180.device.DebugConfig;
import com.google.vr180.media.metadata.ProjectionMetadata;
import com.google.vr180.media.metadata.ProjectionMetadataProvider;
import com.google.vr180.media.metadata.ProjectionMetadataProviderUtils;
import com.google.vr180.media.metadata.StereoReprojectionConfig;
import java.io.IOException;
import java.io.InputStream;

/**
 * A simple implementation of ProjectionMetadataProvider which loads default metadata from Android
 * assets.
 *
 * In production, the per-device metadata files are recommended to be stored in /persist/ partition
 * so that the data persists even after re-flashing the camera firmware.
 */
public class EmulatorProjectionMetadataProvider implements ProjectionMetadataProvider {
  private static final String TAG = "EmulatorProjectionMetadataProvider";

  /**
   * The sv3d box containing the mesh for standard projection. Every sensor crop requires a separate
   * sv3d box with the correct crop factors.
   *
   * Example: create_mp4_boxes_cli --out_box_base mp4_box --in_focal_length 4.67 \
   * --in_sensor_width 6.27 --in_sensor_height 4.72 --horizontal_crop_factor 1.012 \
   * --vertical_crop_factor 1.011
   */
  private static final String SV3D_ASSET = "mp4_box_sv3d.bin";

  /**
   * The sv3d box for equirect projection containing the equi atom describing the pano. Every
   * supported equirect output field of view requires a separate equirect_sv3d box.
   *
   * Example: create_mp4_boxes_cli --out_box_base mp4_box --horizontal_fov_degrees 67 \
   * --vertical_fov_degrees 53
   */
  private static final String SV3D_EQUIRECT_ASSET = "mp4_box_equirect_sv3d.bin";

  /**
   * The StereoMeshConfig containing the MeshProto describing the camera projection to be used for
   * equirect reprojection. Every sensor crop requires a separate StereoMeshConfig with the correct
   * crop factors.
   *
   * Example: create_mp4_boxes_cli --out_box_base mp4_box --in_focal_length 4.67 \
   * --in_sensor_width 6.27 --in_sensor_height 4.72 --horizontal_crop_factor 1.012 \
   * --vertical_crop_factor 1.011
   */
  private static final String MESH_ASSET = "mp4_box_stereo_mesh_config.bin";

  private final Context context;

  public EmulatorProjectionMetadataProvider(Context context) {
    this.context = context;
  }

  @Override
  public ProjectionMetadata getProjectionMetadata(CaptureMode mode) {
    /**
     * This simple implementation assumes all photo, video, and live modes use the same sensor crop
     * and fov. It supports a single sensor crop with standard or equirect projection for a single
     * fov. Additional sensor crops and output fov can be supported by generating additional sv3d,
     * stereo_mesh_config, and equirect_sv3d described above.
     */
    boolean isEquirect = getProjectionType(mode) == ProjectionType.EQUIRECT;
    StereoReprojectionConfig stereoConfig = null;
    if (isEquirect) {
      MeshProto.StereoMeshConfig mesh = loadStereoMeshConfigFromAsset(MESH_ASSET);
      SizeF outputFov = ProjectionMetadataProviderUtils.getFov(mode);
      if (mesh != null && outputFov != null) {
        stereoConfig = new StereoReprojectionConfig(mesh.getLeftEye(), outputFov);
      }
    }
    return new ProjectionMetadata(
        StereoMode.MONO,
        loadMP4BoxFromAsset(isEquirect ? SV3D_EQUIRECT_ASSET : SV3D_ASSET),
        stereoConfig);
  }

  private ProjectionType getProjectionType(CaptureMode mode) {
    switch (mode.getActiveCaptureType()) {
      case VIDEO:
        return mode.getConfiguredVideoMode().getProjectionType();
      case LIVE:
        return mode.getConfiguredLiveMode().getVideoMode().getProjectionType();
      case PHOTO:
        return DebugConfig.getPhotoCaptureImageFormat() != 0
            ? ProjectionType.DEFAULT_FISHEYE
            : ProjectionType.EQUIRECT;
      default:
        return ProjectionType.DEFAULT_FISHEYE;
    }
  }

  private byte[] loadMP4BoxFromAsset(String asset) {
    try (InputStream inputStream = context.getAssets().open(asset)) {
      Log.w(TAG, "Load sample mp4 box from asset" + asset);
      return ByteStreams.toByteArray(inputStream);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read mp4 box: ", e);
      return null;
    }
  }

  private MeshProto.StereoMeshConfig loadStereoMeshConfigFromAsset(String asset) {
    try (InputStream inputStream = context.getAssets().open(asset)) {
      Log.w(TAG, "Load sample stereo config from asset " + asset);
      return MeshProto.StereoMeshConfig.parseFrom(inputStream);
    } catch (IOException e) {
      Log.e(TAG, "Failed to read stereo config: ", e);
      return null;
    }
  }
}
