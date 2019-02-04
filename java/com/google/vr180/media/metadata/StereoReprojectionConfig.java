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

import android.opengl.Matrix;
import android.util.SizeF;
import com.google.common.base.Preconditions;
import com.google.vr180.MeshProto;
import com.google.vr180.common.media.StereoMode;
import com.google.vr180.common.opengl.NIOBuffer;
import java.nio.FloatBuffer;

/**
 * This class encapsulates the details for mesh buffer decoding and stereo mesh texture and viewport
 * transformations to aid in drawing a stereo image from a single texture.
 */
public class StereoReprojectionConfig {
  private final EquirectReprojectionMesh[] meshes;
  private final float[][] textureTransforms;
  private final SizeF fov;
  private final int stereoMode;

  /** This class represents a mesh needed for reprojecting from fisheye to equirect. */
  private static class EquirectReprojectionMesh {
    public final FloatBuffer vertexBuffer;
    public final FloatBuffer textureCoordinateBuffer;
    public final int geometryType;

    /** Constructs from a Mesh corresponding to source image and a target equirect FOV. */
    EquirectReprojectionMesh(MeshProto.Mesh mesh, SizeF fov) {
      this.vertexBuffer = NIOBuffer.createFromArray(getVertexBuffer(mesh, fov));
      this.textureCoordinateBuffer = NIOBuffer.createFromArray(getTextureCoordinateBuffer(mesh));
      this.geometryType = mesh.getGeometryType().getNumber();
    }

    /** Create vertex buffer by reprojecting the mesh points to the specified equirect FOV */
    private static float[] getVertexBuffer(MeshProto.Mesh mesh, SizeF fov) {
      float[] buffer = new float[getVertexCount(mesh) * 2];
      for (int i = 0, index = 0; i < getVertexCount(mesh); i++, index += 2) {
        MeshProto.Mesh.Vertex.Position position = getVertex(mesh, i).getPosition();
        projectToEquirect(position.getX(), position.getY(), position.getZ(), fov, buffer, index);
      }
      return buffer;
    }

    /** Project a 3D point to the specified FOV and save it in buffer at the given index */
    private static void projectToEquirect(
        float x, float y, float z, SizeF fov, float[] buffer, int index) {
      // Compute the yaw angle around Y-axis, and normalize it to [-1, 1] by multiplying with
      // 2/fov.getWidth();
      //
      // Note that the Z coordinate needs to be flipped to account for OpenGL coordinate system.
      buffer[index] = (float) (2.0 * Math.toDegrees(Math.atan2(x, -z)) / fov.getWidth());
      // Computing the tilt angle around Y-axis, and normalize it to [-1, 1] by multiplying with
      // 2/fov.getHeight();
      buffer[index + 1] =
          (float) (2.0 * Math.toDegrees(Math.atan(y / Math.hypot(x, z))) / fov.getHeight());
    }

    private static float[] getTextureCoordinateBuffer(MeshProto.Mesh mesh) {
      float[] buffer = new float[getVertexCount(mesh) * 2];
      for (int i = 0, index = 0; i < getVertexCount(mesh); i++) {
        MeshProto.Mesh.Vertex vertex = getVertex(mesh, i);
        buffer[index++] = vertex.getTextureCoordinate().getU();
        buffer[index++] = vertex.getTextureCoordinate().getV();
      }
      return buffer;
    }

    private static int getVertexCount(MeshProto.Mesh mesh) {
      return mesh.getIndicesCount() > 0 ? mesh.getIndicesCount() : mesh.getVerticesCount();
    }

    private static MeshProto.Mesh.Vertex getVertex(MeshProto.Mesh mesh, int index) {
      return mesh.getIndicesCount() > 0
          ? mesh.getVertices(mesh.getIndices(index))
          : mesh.getVertices(index);
    }
  }

  /**
   * Creates a StereoReprojectionConfig for reprojecting a MONO camera to equirect.
   *
   * @param mesh - the mono mesh describing the camera projection.
   * @param fov - the equirect output fov to reproject to.
   */
  public StereoReprojectionConfig(MeshProto.Mesh mesh, SizeF fov) {
    this.stereoMode = StereoMode.MONO;
    this.fov = fov;
    meshes = new EquirectReprojectionMesh[] {new EquirectReprojectionMesh(mesh, fov)};
    textureTransforms = new float[1][16];
    Matrix.setIdentityM(textureTransforms[0], 0);
  }

  /**
   * Creates a StereoReprojectionConfig for reprojecting a STEREO camera to equirect.
   *
   * @param config - the StereoMeshConfig containing left and right eye meshes.\
   * @param stereoMode - the stereo layout of the camera source (left-right or top-bottom).
   * @param fov - the equirect output fov to reproject to.
   */
  public StereoReprojectionConfig(MeshProto.StereoMeshConfig config, int stereoMode, SizeF fov) {
    Preconditions.checkArgument(
        stereoMode == StereoMode.LEFT_RIGHT || stereoMode == StereoMode.TOP_BOTTOM);
    this.stereoMode = stereoMode;
    this.fov = fov;
    meshes =
        new EquirectReprojectionMesh[] {
          new EquirectReprojectionMesh(config.getLeftEye(), fov),
          new EquirectReprojectionMesh(config.getRightEye(), fov)
        };
    textureTransforms = new float[2][16];
    Matrix.setIdentityM(textureTransforms[0], 0);
    Matrix.setIdentityM(textureTransforms[1], 0);
    if (stereoMode == StereoMode.LEFT_RIGHT) {
      Matrix.scaleM(textureTransforms[0], 0, 0.5f, 1.0f, 1.0f);
      Matrix.translateM(textureTransforms[1], 0, 0.5f, 0.0f, 0.0f);
      Matrix.scaleM(textureTransforms[1], 0, 0.5f, 1.0f, 1.0f);
    } else if (stereoMode == StereoMode.TOP_BOTTOM) {
      Matrix.scaleM(textureTransforms[0], 0, 1.0f, 0.5f, 1.0f);
      Matrix.translateM(textureTransforms[1], 0, 0.0f, 0.5f, 0.0f);
      Matrix.scaleM(textureTransforms[1], 0, 1.0f, 0.5f, 1.0f);
    }
  }

  public int getNumEyes() {
    return stereoMode == StereoMode.MONO ? 1 : 2;
  }

  public void getViewport(int index, int[] viewport, int[] adjustedViewport) {
    Preconditions.checkArgument(index >= 0 && index < getNumEyes());
    if (stereoMode == StereoMode.LEFT_RIGHT) {
      int halfWidth = viewport[2] / 2;
      adjustedViewport[0] = viewport[0] + (index * halfWidth);
      adjustedViewport[1] = viewport[1];
      adjustedViewport[2] = halfWidth;
      adjustedViewport[3] = viewport[3];
    } else if (stereoMode == StereoMode.TOP_BOTTOM) {
      int halfHeight = viewport[3] / 2;
      adjustedViewport[0] = viewport[0];
      adjustedViewport[1] = viewport[1] + (index * halfHeight);
      adjustedViewport[2] = viewport[2];
      adjustedViewport[3] = halfHeight;
    } else if (stereoMode == StereoMode.MONO) {
      System.arraycopy(viewport, 0, adjustedViewport, 0, 4);
    }
  }

  public float[] getTextureTransform(int index) {
    Preconditions.checkArgument(index >= 0 && index < getNumEyes());
    return textureTransforms[index];
  }

  public FloatBuffer getVertexBuffer(int index) {
    Preconditions.checkArgument(index >= 0 && index < getNumEyes());
    return meshes[index].vertexBuffer;
  }

  public FloatBuffer getTextureCoordinateBuffer(int index) {
    Preconditions.checkArgument(index >= 0 && index < getNumEyes());
    return meshes[index].textureCoordinateBuffer;
  }

  public SizeF getFov() {
    return fov;
  }

  public int getGeometryType(int index) {
    Preconditions.checkArgument(index >= 0 && index < getNumEyes());
    return meshes[index].geometryType;
  }

  public int getStereoMode() {
    return stereoMode;
  }
}
