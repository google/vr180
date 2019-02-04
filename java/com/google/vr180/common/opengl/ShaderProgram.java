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
import android.util.Log;

/**
 * A class representing an OpenGL shader program, composed of a vertex
 * and fragment shaders.
 *
 * <p>Example use:
 * <pre>  {@code
 * private static final String FRAGMENT_CODE =
 *     "void main() {" +
 *     "  gl_FragColor = vec4(1, 0, 0, 1);" +
 *     "}";
 * private static final String VERTEX_CODE =
 *     "attribute vec2 position;" +
 *     "void main() {" +
 *     "  gl_Position = vec4(position, 0, 1);" +
 *     "}";}</pre>
 * ShaderProgram program = new ShaderProgram(VERTEX_CODE, FRAGMENT_CODE);
 * vertexBuffer = NIOBuffer.createFromArray(QUAD_COORDS);
 * program.bind();
 * program.enableVertexAttribute("position", vertexBuffer, COORDS_PER_VERTEX);
 * GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
 * program.disableVertexAttribute("position");
 * program.unbind();
 */
public class ShaderProgram {
  private static final String TAG = ShaderProgram.class.getSimpleName();

  private int vertexHandle = -1;
  private int fragmentHandle = -1;
  private int programHandle = -1;

  /**
   * Create a program from the vertex and fragment shaders source code.
   *
   * @param vertexCode The vertex shader source code
   * @param fragmentCode The fragment source code
   */
  public ShaderProgram(final String vertexCode, final String fragmentCode) {
    this.vertexHandle = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode);
    this.fragmentHandle = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode);
    this.programHandle = GLES20.glCreateProgram();
    GLES20.glAttachShader(programHandle, vertexHandle);
    GLES20.glAttachShader(programHandle, fragmentHandle);
    GLES20.glLinkProgram(programHandle);
  }

  public void delete() {
    GLES20.glDeleteShader(vertexHandle);
    GLES20.glDeleteShader(fragmentHandle);
    GLES20.glDeleteProgram(programHandle);
  }

  /**
   * Binds this program.
   */
  public void bind() {
    GLES20.glUseProgram(programHandle);
  }

  /**
   * Unbinds this program.
   */
  public void unbind() {
    GLES20.glUseProgram(0);
  }

  /**
   * Creates a vertex shader (GLES20.GL_VERTEX_SHADER) or
   * a fragment shader (GLES20.GL_FRAGMENT_SHADER) from shader source code.
   *
   * @param type The type of shader: GL_VERTEX_SHADER or GL_FRAGMENT_SHADER
   * @param shaderCode The shader source code
   * @return The shader handle
   */
  private static int loadShader(int type, String shaderCode) {
    int shader = GLES20.glCreateShader(type);

    // Add the source code to the shader and compile it.
    GLES20.glShaderSource(shader, shaderCode);
    GLES20.glCompileShader(shader);

    // Get the compilation status.
    final int[] compileStatus = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

    // If the compilation failed, delete the shader.
    if (compileStatus[0] == 0) {
      String infoLog = GLES20.glGetShaderInfoLog(shader);
      Log.e(TAG, infoLog);
      GLES20.glDeleteShader(shader);
      shader = 0;
      throw new IllegalArgumentException("Shader compilation failed: " + infoLog);
    }

    return shader;
  }

  /**
   * Returns the program handle of the shader program or -1 if no handle exists.
   */
  public int getProgramHandle() {
    return programHandle;
  }

  /**
   * Construct a new Uniform object that can be used to set the named uniform.
   * Returns null if there is no such uniform.
   */
  public Uniform getUniform(String name) {
    int location = GLES20.glGetUniformLocation(programHandle, name);
    if (location < 0) {
      Log.e(TAG, "Could not find uniform named " + name);
      return null;
    }
    return new Uniform(location);
  }

  /**
   * Constructs a new Attribute object that can be used to set the named attribute.
   * Returns null if there is no such attribute.
   */
  public Attribute getAttribute(String name) {
    int location = GLES20.glGetAttribLocation(programHandle, name);
    if (location < 0) {
      Log.e(TAG, "Could not find attribute named " + name);
      return null;
    }
    return new Attribute(location);
  }
}
