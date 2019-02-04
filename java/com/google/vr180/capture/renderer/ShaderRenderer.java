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

package com.google.vr180.capture.renderer;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import com.google.vr180.common.opengl.Attribute;
import com.google.vr180.common.opengl.MeshGrid;
import com.google.vr180.common.opengl.NIOBuffer;
import com.google.vr180.common.opengl.ShaderProgram;
import com.google.vr180.common.opengl.Texture;
import com.google.vr180.common.opengl.Uniform;
import com.google.vr180.media.metadata.StereoReprojectionConfig;
import java.nio.FloatBuffer;

/**
 * A simple class to draw video textures into an OpenGL framebuffer. Optionally supports
 * reprojection of the texture via a StereoReprojectionConfig containing left/right or top/bottom
 * vertex and texture buffers.
 */
public class ShaderRenderer {
  private static final int COORDS_PER_VERTEX = 2;
  private static final FloatBuffer vertexBuffer =
      NIOBuffer.createFromArray(MeshGrid.createStrip(-1f, 1f, 1f, -1f, 1, 1));
  private static final FloatBuffer texCoordBuffer =
      NIOBuffer.createFromArray(MeshGrid.createStrip(0f, 1f, 1f, 0f, 1, 1));

  private static final String FRAGMENT_CODE_EXTERNAL =
      "#extension GL_OES_EGL_image_external : require \n"
          + "precision mediump float;"
          + "uniform samplerExternalOES texture;"
          + "varying vec2 texCoord;"
          + "void main() {"
          + "  gl_FragColor = texture2D(texture, texCoord);"
          + "}";

  private static final String FRAGMENT_CODE_TEXTURE_2D =
      "precision mediump float;"
          + "uniform sampler2D texture;"
          + "varying vec2 texCoord;"
          + "void main() {"
          + "  gl_FragColor = texture2D(texture, texCoord);"
          + "}";

  private static final String VERTEX_CODE =
      "attribute vec2 vertexAttrib;"
          + "attribute vec2 texCoordAttrib;"
          + "varying vec2 texCoord;"
          + "uniform mat4 vertexTransform;"
          + "uniform mat4 textureTransform;"
          + "void main() {"
          + "  texCoord = (textureTransform * vec4(texCoordAttrib, 0., 1.)).xy;"
          + "  gl_Position = vertexTransform * vec4(vertexAttrib, 0., 1.);"
          + "}";

  private Texture texture = null;
  private final float[] vertexTransform = new float[16];
  private final float[] textureRotation = new float[16];
  private final float[] textureTransform = new float[16];
  private final float[] adjustedTextureTransform = new float[16];
  private final int[] viewport = new int[4];
  private final int[] adjustedViewport = new int[4];
  private ShaderProgram program;
  private Uniform textureUniform;
  private Uniform vertexTransformUniform;
  private Uniform textureTransformUniform;
  private Attribute vertexAttrib;
  private Attribute texCoordAttrib;
  private StereoReprojectionConfig stereoReprojectionConfig;

  public ShaderRenderer(int cameraOrientation) {
    Matrix.setIdentityM(vertexTransform, 0);

    // Construct the matrix for rotating the texture around (0.5, 0.5);
    Matrix.setIdentityM(textureRotation, 0);
    Matrix.translateM(textureRotation, 0, 0.5f, 0.5f, 0.0f);
    Matrix.rotateM(textureRotation, 0, -cameraOrientation, 0f, 0f, 1f);
    Matrix.translateM(textureRotation, 0, -0.5f, -0.5f, 0.0f);
    System.arraycopy(textureRotation, 0, textureTransform, 0, 16);
  }

  /** Release openGL resources. */
  public void release() {
    if (program != null) {
      program.delete();
      program = null;
    }
  }

  public void setTexture(Texture texture) {
    this.texture = texture;
  }

  /**
   * Sets the vertex transform.
   *
   * @param matrix The 4x4 matrix as a single 16-float vector
   */
  public void setVertexTransform(float[] matrix) {
    System.arraycopy(matrix, 0, vertexTransform, 0, 16);
  }

  /**
   * Sets the texture transform.
   *
   * @param matrix The 4x4 matrix as a single 16-float vector
   */
  public void setTextureTransform(float[] matrix) {
    Matrix.multiplyMM(textureTransform, 0, matrix, 0, textureRotation, 0);
  }

  /** Sets the stereo config to enable mesh based reprojection. */
  public void setStereoReprojectionConfig(StereoReprojectionConfig config) {
    this.stereoReprojectionConfig = config;
  }

  public void draw() {
    if (texture == null) {
      return;
    }
    if (program == null) {
      init();
    }
    program.bind();

    textureUniform.setTexture(texture, 0);
    vertexTransformUniform.setMatrix4(vertexTransform);

    vertexAttrib.enable();
    texCoordAttrib.enable();

    final StereoReprojectionConfig config = stereoReprojectionConfig;
    if (config != null) {
      GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0);
      for (int eye = 0; eye < config.getNumEyes(); eye++) {
        drawEye(config, eye);
      }
      GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    } else {
      drawTriangleStrip(textureTransform, vertexBuffer, texCoordBuffer);
    }

    texCoordAttrib.disable();
    vertexAttrib.disable();
    program.unbind();

    // Unbind the texture to reset the state. When a texture is shared by multiple contexts, it has
    // to be unbound in order to pick up the changes by SurfaceTexture.updateTexImage.
    texture.unbind();
  }

  private void drawTriangleStrip(
      final float[] textureTransform,
      final FloatBuffer vertexBuffer,
      final FloatBuffer textureCoordinateBuffer) {
    textureTransformUniform.setMatrix4(textureTransform);
    vertexAttrib.set(vertexBuffer, COORDS_PER_VERTEX);
    texCoordAttrib.set(textureCoordinateBuffer, COORDS_PER_VERTEX);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexBuffer.capacity() / COORDS_PER_VERTEX);
  }

  private void drawEye(final StereoReprojectionConfig config, int eye) {
    // Adjust the viewport and the texture transform before drawing the single eye triangle mesh.
    config.getViewport(eye, viewport, adjustedViewport);
    GLES20.glViewport(
        adjustedViewport[0], adjustedViewport[1], adjustedViewport[2], adjustedViewport[3]);
    Matrix.multiplyMM(
        adjustedTextureTransform, 0, config.getTextureTransform(eye), 0, textureTransform, 0);
    drawTriangleStrip(
        adjustedTextureTransform,
        config.getVertexBuffer(eye),
        config.getTextureCoordinateBuffer(eye));
  }

  private void init() {
    String fragmentCode =
        (texture.getType() == GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            ? FRAGMENT_CODE_EXTERNAL
            : FRAGMENT_CODE_TEXTURE_2D;
    program = new ShaderProgram(VERTEX_CODE, fragmentCode);
    textureUniform = program.getUniform("texture");
    vertexTransformUniform = program.getUniform("vertexTransform");
    textureTransformUniform = program.getUniform("textureTransform");
    vertexAttrib = program.getAttribute("vertexAttrib");
    texCoordAttrib = program.getAttribute("texCoordAttrib");
  }
}
