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

package com.google.vr180.media.rtmp;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Encodes and decodes Adobe Action Message Format data.
 */
@SuppressWarnings("unused")
final class ActionMessageFormat {
  // Type markers
  private static final int TYPE_NUMBER = 0x00;
  private static final int TYPE_BOOLEAN = 0x01;
  private static final int TYPE_STRING = 0x02;
  private static final int TYPE_OBJECT = 0x03;
  private static final int TYPE_MOVIE_CLIP = 0x04;
  private static final int TYPE_NULL = 0x05;
  private static final int TYPE_UNDEFINED = 0x06;
  private static final int TYPE_REFERENCE = 0x07;
  private static final int TYPE_ECMA_ARRAY = 0x08;
  private static final int TYPE_OBJECT_END = 0x09;
  private static final int TYPE_STRICT_ARRAY = 0x0A;
  private static final int TYPE_DATE = 0x0B;
  private static final int TYPE_LONG_STRING = 0x0C;
  private static final int TYPE_UNSUPPORTED = 0x0D;
  private static final int TYPE_RECORDSET = 0x0E;
  private static final int TYPE_XML_DOC = 0x0F;
  private static final int TYPE_TYPED_OBJECT = 0x10;

  /**
   * A ByteArrayOutputStream that provides access to the underlying buffer without copying
   * Requires reset after each use.
   */
  private static class ReusableByteArrayOutputStream extends ByteArrayOutputStream {
    ReusableByteArrayOutputStream(int size) {
      super(size);
    }

    ReusableByteArrayOutputStream() {
      super();
    }

    byte[] getByteArray() {
      return buf;
    }

    int getWritten() {
      return count;
    }
  }

  /** Encodes AMF0 data on an associated {@link java.io.DataOutputStream}. */
  static final class Writer {
    private final DataOutputStream outputStream;
    private final ReusableByteArrayOutputStream buffer = new ReusableByteArrayOutputStream();

    Writer(DataOutputStream outputStream) {
      this.outputStream = outputStream;
    }

    Writer() {
      outputStream = new DataOutputStream(buffer);
    }

    void reset() {
      buffer.reset();
    }

    ByteBuffer toByteBuffer() {
      return ByteBuffer.wrap(buffer.getByteArray(), 0, buffer.getWritten());
    }

    void writeNumber(double value) throws IOException {
      outputStream.writeByte(TYPE_NUMBER);
      outputStream.writeDouble(value);
    }

    void writeNull() throws IOException {
      outputStream.writeByte(TYPE_NULL);
    }

    void writeBoolean(boolean value) throws IOException {
      outputStream.writeByte(TYPE_BOOLEAN);
      outputStream.writeByte(value ? 1 : 0);
    }

    void writeString(String value) throws IOException {
      outputStream.writeByte(TYPE_STRING);
      outputStream.writeShort(value.length());
      outputStream.write(value.getBytes(UTF_8));
    }

    void writeArrayBegin(int size) throws IOException {
      outputStream.writeByte(TYPE_ECMA_ARRAY);
      outputStream.writeInt(size);
    }

    void writeObjectBegin() throws IOException {
      outputStream.writeByte(TYPE_OBJECT);
    }

    void writePropertyName(String name) throws IOException {
      outputStream.writeShort(name.length());
      outputStream.write(name.getBytes(UTF_8));
    }

    void writeObjectEnd() throws IOException {
      outputStream.writeShort(0);
      outputStream.writeByte(TYPE_OBJECT_END);
    }
  }

  /** Decodes AMF0 data from an associated {@link java.io.DataOutputStream}. */
  static class Reader {
    private final DataInputStream inputStream;

    Reader(DataInputStream inputStream) {
      this.inputStream = inputStream;
    }

    String readString() throws IOException {
      readExpectedType(TYPE_STRING);
      return inputStream.readUTF();
    }

    double readNumber() throws IOException {
      readExpectedType(TYPE_NUMBER);
      return inputStream.readDouble();
    }

    void readNull() throws IOException {
      readExpectedType(TYPE_NULL);
    }

    Map<String, Object> readObject() throws IOException {
      readExpectedType(TYPE_OBJECT);
      return readObjectInternal();
    }

    private Map<String, Object> readObjectInternal() throws IOException {
      HashMap<String, Object> object = new HashMap<>();
      while (true) {
        String name = inputStream.readUTF();
        if (name.length() == 0) {
          break;
        }
        Object value = readValue();
        object.put(name, value);
      }
      readExpectedType(TYPE_OBJECT_END);
      return object;
    }

    private Object readArrayInternal() throws IOException {
      inputStream.readInt();  // Array length.
      return readObjectInternal();
    }

    Object readValue() throws IOException {
      int type = inputStream.readByte();
      switch (type) {
        case TYPE_NULL:
          return null;
        case TYPE_NUMBER:
          return inputStream.readDouble();
        case TYPE_STRING:
          return inputStream.readUTF();
        case TYPE_OBJECT:
          return readObjectInternal();
        case TYPE_ECMA_ARRAY:
          return readArrayInternal();
        default:
          throw new ProtocolException("Unsupported AMF type: " + type);
      }
    }

    private void readExpectedType(int expected) throws IOException {
      int type = inputStream.readByte();
      if (type != expected) {
        throw new ProtocolException("Expected AMF type " + expected + ", got: " + type);
      }
    }
  }
}
