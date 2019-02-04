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

import static org.junit.Assert.assertThrows;

import com.google.common.truth.Truth;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Test for {@link ActionMessageFormat} */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class ActionMessageFormatTest {

  private ActionMessageFormat.Writer amfWriter;
  private ActionMessageFormat.Reader amfReader;
  private byte[] inputDataBuf;

  @Before
  public void setUp() throws Exception {
    amfWriter = new ActionMessageFormat.Writer();
    inputDataBuf = new byte[1024];
    amfReader =
        new ActionMessageFormat.Reader(new DataInputStream(new ByteArrayInputStream(inputDataBuf)));
  }

  @Test
  public void testWriteNumber() throws Exception {
    amfWriter.writeNumber(2.5);
    amfWriter.writeNumber(7654321);

    ByteBuffer result = amfWriter.toByteBuffer();
    ByteBuffer expected =
        ByteBuffer.wrap(new byte[] {0, 64, 4, 0, 0, 0, 0, 0, 0, 0, 65, 93, 50, -20, 64, 0, 0, 0});
    Truth.assertThat(result).isEqualTo(expected);
  }

  @Test
  public void testReadNumber() throws Exception {
    byte[] inputData = new byte[] {0, 64, 4, 0, 0, 0, 0, 0, 0, 0, 65, 93, 50, -20, 64, 0, 0, 0};
    System.arraycopy(inputData, 0, inputDataBuf, 0, inputData.length);
    Truth.assertThat(amfReader.readNumber()).isEqualTo(2.5);
    Truth.assertThat((int) amfReader.readNumber()).isEqualTo(7654321);
  }

  @Test
  public void testReadNumberBad() throws Exception {
    byte[] inputData = new byte[] {1};
    System.arraycopy(inputData, 0, inputDataBuf, 0, inputData.length);
    assertThrows(ProtocolException.class, () -> amfReader.readNumber());
  }

  @Test
  public void testWriteNull() throws Exception {
    amfWriter.writeNull();
    amfWriter.writeNull();
    amfWriter.writeNull();

    ByteBuffer result = amfWriter.toByteBuffer();
    ByteBuffer expected = ByteBuffer.wrap(new byte[] {5, 5, 5});
    Truth.assertThat(result).isEqualTo(expected);
  }

  @Test
  public void testReadNull() throws Exception {
    byte[] inputData = new byte[] {5, 5, 5};
    System.arraycopy(inputData, 0, inputDataBuf, 0, inputData.length);
    amfReader.readNull();
    amfReader.readNull();
    amfReader.readNull();
  }

  @Test
  public void testReadNullBad() throws Exception {
    byte[] inputData = new byte[] {1};
    System.arraycopy(inputData, 0, inputDataBuf, 0, inputData.length);
    assertThrows(ProtocolException.class, () -> amfReader.readNull());
  }

  @Test
  public void testWriteBoolean() throws Exception {
    amfWriter.writeBoolean(true);
    amfWriter.writeBoolean(false);
    amfWriter.writeBoolean(true);

    ByteBuffer result = amfWriter.toByteBuffer();
    ByteBuffer expected = ByteBuffer.wrap(new byte[] {1, 1, 1, 0, 1, 1});
    Truth.assertThat(result).isEqualTo(expected);
  }

  @Test
  public void testWriteString() throws Exception {
    amfWriter.writeString("This is a test");
    amfWriter.writeString("");
    amfWriter.writeString("This is another test");

    ByteBuffer result = amfWriter.toByteBuffer();
    ByteBuffer expected =
        ByteBuffer.wrap(
            new byte[] {
              2, 0, 14, 84, 104, 105, 115, 32, 105, 115, 32, 97, 32, 116, 101, 115, 116, 2, 0, 0, 2,
              0, 20, 84, 104, 105, 115, 32, 105, 115, 32, 97, 110, 111, 116, 104, 101, 114, 32, 116,
              101, 115, 116
            });
    Truth.assertThat(result).isEqualTo(expected);
  }

  @Test
  public void testReadString() throws Exception {
    byte[] inputData =
        new byte[] {
          2, 0, 14, 84, 104, 105, 115, 32, 105, 115, 32, 97, 32, 116, 101, 115, 116, 2, 0, 0, 2, 0,
          20, 84, 104, 105, 115, 32, 105, 115, 32, 97, 110, 111, 116, 104, 101, 114, 32, 116, 101,
          115, 116
        };
    System.arraycopy(inputData, 0, inputDataBuf, 0, inputData.length);
    Truth.assertThat(amfReader.readString()).isEqualTo("This is a test");
    Truth.assertThat(amfReader.readString()).isEqualTo("");
    Truth.assertThat(amfReader.readString()).isEqualTo("This is another test");
  }

  @Test
  public void testReadStringBad() throws Exception {
    byte[] inputData = new byte[] {1};
    System.arraycopy(inputData, 0, inputDataBuf, 0, inputData.length);
    assertThrows(ProtocolException.class, () -> amfReader.readString());
  }

  @Test(expected = NullPointerException.class)
  public void testWriteStringNull() throws Exception {
    amfWriter.writeString(null);
  }

  @Test
  public void testWriteArrayBegin() throws Exception {
    amfWriter.writeArrayBegin(0);
    amfWriter.writeArrayBegin(-123);
    amfWriter.writeArrayBegin(0x01023344);

    ByteBuffer result = amfWriter.toByteBuffer();
    ByteBuffer expected =
        ByteBuffer.wrap(new byte[] {8, 0, 0, 0, 0, 8, -1, -1, -1, -123, 8, 1, 2, 51, 68});
    Truth.assertThat(result).isEqualTo(expected);
  }

  @Test
  public void testReadArray() throws Exception {
    byte[] inputData =
        new byte[] {8, 0, 0, 0, 1, 0, 4, 110, 97, 109, 101, 0, 64, 20, 0, 0, 0, 0, 0, 0, 0, 0, 9};
    System.arraycopy(inputData, 0, inputDataBuf, 0, inputData.length);
    Map<String, Object> map = (Map<String, Object>) amfReader.readValue();
    double value = (double) map.get("name");

    Truth.assertThat((int) value).isEqualTo(5);
  }

  @Test
  public void testWriteObject() throws Exception {
    amfWriter.writeObjectBegin();
    amfWriter.writeString("name");
    amfWriter.writeNumber(5);
    amfWriter.writeObjectEnd();

    ByteBuffer result = amfWriter.toByteBuffer();
    ByteBuffer expected =
        ByteBuffer.wrap(
            new byte[] {3, 2, 0, 4, 110, 97, 109, 101, 0, 64, 20, 0, 0, 0, 0, 0, 0, 0, 0, 9});
    Truth.assertThat(result).isEqualTo(expected);
  }

  @Test
  public void testReadObject() throws Exception {
    byte[] inputData =
        new byte[] {3, 0, 4, 110, 97, 109, 101, 0, 64, 20, 0, 0, 0, 0, 0, 0, 0, 0, 9};
    System.arraycopy(inputData, 0, inputDataBuf, 0, inputData.length);
    Map<String, Object> map = amfReader.readObject();
    double value = (double) map.get("name");

    Truth.assertThat((int) value).isEqualTo(5);
  }

  @Test
  public void testReadObjectBad() throws Exception {
    byte[] inputData = new byte[] {31};
    System.arraycopy(inputData, 0, inputDataBuf, 0, inputData.length);
    assertThrows(ProtocolException.class, () -> amfReader.readObject());
  }

  @Test
  public void testReadObjectBadEnd() throws Exception {
    byte[] inputData =
        new byte[] {3, 0, 4, 110, 97, 109, 101, 0, 64, 20, 0, 0, 0, 0, 0, 0, 0, 0, 8};
    System.arraycopy(inputData, 0, inputDataBuf, 0, inputData.length);
    assertThrows(ProtocolException.class, () -> amfReader.readObject());
  }

  @Test
  public void testWritePropName() throws Exception {
    amfWriter.writePropertyName("PROPERTY");
    amfWriter.writePropertyName("");
    amfWriter.writePropertyName("pr0perty");

    ByteBuffer result = amfWriter.toByteBuffer();
    ByteBuffer expected =
        ByteBuffer.wrap(
            new byte[] {
              0, 8, 80, 82, 79, 80, 69, 82, 84, 89, 0, 0, 0, 8, 112, 114, 48, 112, 101, 114, 116,
              121
            });
    Truth.assertThat(result).isEqualTo(expected);
  }

  @Test(expected = NullPointerException.class)
  public void testWritePropNameNull() throws Exception {
    amfWriter.writePropertyName(null);
  }
}
