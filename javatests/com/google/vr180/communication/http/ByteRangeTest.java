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

package com.google.vr180.communication.http;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ByteRangeTest {
  @Test
  public void testToString() {
    assertThat(new ByteRange(0).toString()).isEqualTo("bytes=0-");
    assertThat(new ByteRange(10).toString()).isEqualTo("bytes=10-");
    assertThat(new ByteRange(-10).toString()).isEqualTo("bytes=-10");
    assertThat(new ByteRange(0, 0).toString()).isEqualTo("bytes=0-0");
    assertThat(new ByteRange(5, 10).toString()).isEqualTo("bytes=5-10");
  }

  @Test
  public void testEquals() {
    assertThat(new ByteRange(0)).isEqualTo(new ByteRange(0));
    assertThat(new ByteRange(10)).isEqualTo(new ByteRange(10));
    assertThat(new ByteRange(0, 0)).isEqualTo(new ByteRange(0, 0));
    assertThat(new ByteRange(0, 10)).isEqualTo(new ByteRange(0, 10));

    assertThat(new ByteRange(0).equals(new ByteRange(1))).isFalse();
    assertThat(new ByteRange(0).equals(new ByteRange(1, 2))).isFalse();
    assertThat(new ByteRange(0, 0).equals(new ByteRange(0, 1))).isFalse();
    assertThat(new ByteRange(2, 10).equals(new ByteRange(1, 10))).isFalse();
  }

  @Test
  public void testHashCode() {
    Set<ByteRange> set = new HashSet<ByteRange>();

    set.add(new ByteRange(0));
    set.add(new ByteRange(0));
    set.add(new ByteRange(0, 10));
    set.add(new ByteRange(0, 10));

    assertThat(set.size()).isEqualTo(2);
    assertThat(set.contains(new ByteRange(0))).isTrue();
    assertThat(set.contains(new ByteRange(0, 10))).isTrue();
  }

  private void doInvalidRangeTest(long start, long end) {
    assertThrows(IllegalArgumentException.class, () -> new ByteRange(start, end));
  }

  @Test
  public void testInvalidByteRanges() {
    doInvalidRangeTest(20, 10);
    doInvalidRangeTest(-1, 10);
  }

  @Test
  public void testParseByteRange() throws Exception {
    assertThat(new ByteRange(0)).isEqualTo(ByteRange.parse("bytes=0-"));
    assertThat(new ByteRange(10)).isEqualTo(ByteRange.parse("bytes=10-"));
    assertThat(new ByteRange(-10)).isEqualTo(ByteRange.parse("bytes=-10"));
    assertThat(new ByteRange(10, 20)).isEqualTo(ByteRange.parse("bytes=10-20"));
    assertThat(new ByteRange(10, 20)).isEqualTo(ByteRange.parse(" bytes = 10 - 20 "));
  }

  @Test
  public void testInvalidRangeFormats() throws Exception {
    doInvalidRangeFormatTest("");
    doInvalidRangeFormatTest("bytes");
    doInvalidRangeFormatTest("bytes=");
    doInvalidRangeFormatTest("not correct at all");
  }

  private void doInvalidRangeFormatTest(String byteRange) {
    assertThrows(ByteRange.RangeFormatException.class, () -> ByteRange.parse(byteRange));
  }

  private void doUnsupportedRangeFormatTest(String byteRange) {
    assertThrows(ByteRange.RangeFormatException.class, () -> ByteRange.parse(byteRange));
  }

  @Test
  public void testUnsupportedRangeFormats() {
    doUnsupportedRangeFormatTest("words=10-");
    doUnsupportedRangeFormatTest("bytes=10-,20-,20-40");
    doUnsupportedRangeFormatTest("bytes=1-2, 4-4, -1");
  }

  @Test
  public void testAccessUnsetEnd() {
    assertThrows(IllegalStateException.class, () -> new ByteRange(10).getEnd());
  }
}
