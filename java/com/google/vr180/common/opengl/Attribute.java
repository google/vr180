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
 * Caches a uniform's location and provides convenience functions for enabling it and binding it to
 * a vertex array.
 */
public class Attribute {
  private final int index;

  public Attribute(int index) {
    this.index = index;
  }

  /**
   * Returns the attributes index, as in glGetAttributeLocation.
   */
  public int getIndex() {
    return index;
  }

  /**
   * Enables the attribute.
   */
  public void enable() {
    GLES20.glEnableVertexAttribArray(index);
  }

  /**
   * Disables the attribute.
   */
  public void disable() {
    GLES20.glDisableVertexAttribArray(index);
  }

  /**
   * Binds the attribute to the given vertex array.
   */
  public void set(FloatBuffer vertexBuffer, int coordsPerVertex) {
    GLES20.glVertexAttribPointer(
        index, coordsPerVertex, GLES20.GL_FLOAT, false, 0, vertexBuffer);
  }
}
