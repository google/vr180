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

import android.opengl.GLES20;

import java.nio.FloatBuffer;

/**
 * Caches a uniform's location and provides convenience functions for setting the value.
 */
public class Uniform {
  private int location = -1;

  public Uniform(int location) {
    this.location = location;
  }

  /**
   * Returns the uniform location, as in glGetUniformLocation.
   */
  public int getLocation() {
    return location;
  }

  /**
   * Sets an int value.
   */
  public void set(int value) {
    GLES20.glUniform1i(location, value);
  }

  /**
   * Sets a float value.
   */
  public void set(float value) {
    GLES20.glUniform1f(location, value);
  }

  /**
   * Sets a vec2 float value.
   */
  public void set(float x, float y) {
    GLES20.glUniform2f(location, x, y);
  }

  /**
   * Sets a vec3 float value.
   */
  public void set(float x, float y, float z) {
    GLES20.glUniform3f(location, x, y, z);
  }

  /**
   * Sets a vec4 float value.
   */
  public void set(float x, float y, float z, float w) {
    GLES20.glUniform4f(location, x, y, z, w);
  }

  /**
   * Sets a 4 dimensional vector value.
   */
  public void setVector4(float[] value) {
    GLES20.glUniform4fv(location, 1, value, 0);
  }

  /**
   * Sets a 4 dimensional vector value.
   */
  public void setVector4(FloatBuffer value) {
    GLES20.glUniform4fv(location, 1, value);
  }

  /**
   * Sets a 4x4 matrix value.
   */
  public void setMatrix4(float[] value) {
    GLES20.glUniformMatrix4fv(location, 1, false, value, 0);
  }

  /**
   * Sets a 4x4 matrix value.
   */
  public void setMatrix4(FloatBuffer value) {
    GLES20.glUniformMatrix4fv(location, 1, false, value);
  }

  /**
   * Binds the given texture to the given textureUnit and sets the sampler uniform to that texture
   * unit.
   */
  public void setTexture(Texture texture, int textureUnit) {
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + textureUnit);
    texture.bind();
    GLES20.glUniform1i(location, textureUnit);
  }
}
