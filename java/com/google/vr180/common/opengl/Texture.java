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

package com.google.vr180.common.opengl;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

/**
 * A class wrapping OpenGL texture objects.
 * Note that this class can only be called from a valid OpenGL thread.
 */
public class Texture {
  private int name = -1;
  private int type = -1;
  private int width = -1;
  private int height = -1;
  private boolean generated = false;

  /**
   * Generates an OpenGL texture of specified dimensions with the default type
   * GL_TEXTURE_2D. Note that width and height are used only for bookkeeping,
   * not for allocating texture memory.
   */
  public Texture(int width, int height) {
    this(width, height, GLES20.GL_TEXTURE_2D);
  }

  /**
   * Generates an OpenGL texture of specified dimensions with a given type. Note
   * that width and height are used only for bookkeeping, not for allocating
   * texture memory.
   * Example use:
   * <pre>  {@code
   *   Texture texture = new Texture(w, h, GLES20.GL_TEXTURE_2D);
   * Or
   *   Texture texture = new Texture(w, h, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
   * }</pre>
   */
  public Texture(int width, int height, int textureType) {
    this.name = createTexture(textureType);
    this.type = textureType;
    this.width = width;
    this.height = height;
    this.generated = true;
  }

  /**
   * Creates a Texture object by attaching it to existing OpenGL resources. The
   * Texture object does not own those resources and will not delete them.
   */
  public Texture(int width, int height, int textureType, int textureName) {
    this.name = textureName;
    this.type = textureType;
    this.width = width;
    this.height = height;
    this.generated = false;
  }

  /**
   * Generates an OpenGL texture and sets its contents using the Bitmap image.
   */
  public Texture(Bitmap bitmap) {
    type = GLES20.GL_TEXTURE_2D;
    width = bitmap.getWidth();
    height = bitmap.getHeight();
    name = createTexture(type);
    generated = true;
    GLES20.glBindTexture(type, name);
    GLUtils.texImage2D(type, 0, bitmap, 0);
  }

  /**
   * Allocate this texture to an RGBA pixel type.
   * This function only works for type GLES20.GL_TEXTURE_2D.
   */
  public void allocate() {
    GLES20.glTexImage2D(
        type, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
  }

  /**
   * @return The type of texture
   */
  public int getType() {
    return type;
  }

  /**
   * @return The OpenGL name
   */
  public int getName() {
    return name;
  }

  /**
   * @return The width of the texture
   */
  public int getWidth() {
    return width;
  }

  /**
   * @return The height of the texture
   */
  public int getHeight() {
    return height;
  }


  /**
   * Bind the texture to the active texture target.
   */
  public void bind() {
    if (name < 0) {
      return;
    }
    GLES20.glBindTexture(type, name);
  }

  /**
   * Unbind the texture from the active texture target.
   */
  public void unbind() {
    GLES20.glBindTexture(type, 0);
  }

  /**
   * Free the texture resources if owned by this Texture object. The handle will
   * become invalid.
   */
  public void delete() {
    if (name < 0 || !generated) {
      return;
    }

    int[] temp = new int[1];
    temp[0] = name;
    GLES20.glDeleteTextures(1, temp, 0);
    name = -1;
    width = -1;
    height = -1;
  }

  /**
   * Creates an OpenGL texture handler.
   *
   * @return The OpenGL texture handler
   */
  private static int createTexture(int textureType) {
    int[] handle = new int[1];
    GLES20.glGenTextures(1, handle, 0);
    GLES20.glBindTexture(textureType, handle[0]);
    GLES20.glTexParameteri(textureType,
        GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(textureType,
        GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(textureType,
        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(textureType,
        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    return handle[0];
  }
}
