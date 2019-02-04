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

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.support.annotation.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.google.vr180.common.opengl.Texture;
import com.google.vr180.media.metadata.StereoReprojectionConfig;

/** Class for rendering a full screen texture at camera's native orientation. */
public class TextureRenderer {
  private final ShaderRenderer shader;
  int width;
  int height;

  /**
   * Returns an object that renders the given texture from texture to its native orientation
   * according to the orientation defined by {@code cameraOrientation}.
   *
   * @param texture The texture to render.
   * @param stereoReprojectionConfig The optional configuration for texture mapping to equirect.
   * @param cameraOrientation The number of degrees the camera is rotate relative to the device.
   */
  public TextureRenderer(
      Texture texture,
      @Nullable StereoReprojectionConfig stereoReprojectionConfig,
      int cameraOrientation) {
    this(texture, new ShaderRenderer(cameraOrientation), stereoReprojectionConfig);
  }

  @VisibleForTesting
  TextureRenderer(
      Texture texture,
      ShaderRenderer shaderRenderer,
      @Nullable StereoReprojectionConfig stereoReprojectionConfig) {
    shader = shaderRenderer;
    shader.setTexture(texture);
    width = texture.getWidth();
    height = texture.getHeight();
    shader.setStereoReprojectionConfig(stereoReprojectionConfig);
  }

  /** Called from the OpenGL thread. */
  public void render(float[] textureTransform) {
    shader.setTextureTransform(textureTransform);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    shader.draw();
  }

  /**
   * Warms up the shader by using it once so the shader will be ready when render is actually
   * called. This must be called from an OpenGL thread.
   */
  public void warmup() {
    float[] textureTransform = new float[16];
    Matrix.setIdentityM(textureTransform, 0);
    render(textureTransform);
  }

  /**
   * Release the opeGL shader.
   */
  public void release() {
    shader.release();
  }
}
