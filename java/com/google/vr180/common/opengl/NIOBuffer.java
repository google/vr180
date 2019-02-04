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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * A helper class to create NIO buffers.
 *
 * 
 */
public class NIOBuffer {
  /**
   * Creates an IntBuffer of the specified size.
   *
   * @param size The number of elements of the buffer
   * @return the new IntBuffer
   */
  public static IntBuffer createInt(int size) {
    ByteBuffer bb = ByteBuffer.allocateDirect(size * 4);
    bb.order(ByteOrder.nativeOrder());
    return bb.asIntBuffer();
  }

  /**
   * Creates a ShortBuffer of the specified size.
   *
   * @param size The number of elements of the buffer
   * @return the new ShortBuffer
   */
  public static ShortBuffer createShort(int size) {
    ByteBuffer bb = ByteBuffer.allocateDirect(size * 2);
    bb.order(ByteOrder.nativeOrder());
    return bb.asShortBuffer();
  }

  /**
   * Creates a FloatBuffer of the specified size.
   *
   * @param size The number of elements of the buffer
   * @return the new FloatBuffer
   */
  public static FloatBuffer createFloat(int size) {
    ByteBuffer bb = ByteBuffer.allocateDirect(size * 4);
    bb.order(ByteOrder.nativeOrder());
    return bb.asFloatBuffer();
  }

  /**
   * Creates a FloatBuffer from a float array.
   *
   * @param data The float array
   * @return the new FloatBuffer
   */
  public static FloatBuffer createFromArray(float[] data) {
    FloatBuffer buffer = createFloat(data.length);
    buffer.put(data);
    buffer.position(0);
    return buffer;
  }
}
