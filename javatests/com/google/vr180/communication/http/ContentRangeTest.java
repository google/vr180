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
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class ContentRangeTest {

  @Before
  public void setUp() {}

  @Test
  public void methodUnderTest_expectedResult() throws Exception {}

  @Test
  public void testParseContentRange() throws Exception {
    assertThat(ContentRange.parse("bytes 0-10/1000"))
        .isEqualTo(new ContentRange(new ByteRange(0, 10), 1000L));
  }

  @Test
  public void testParseUnspecifiedLength() throws Exception {
    assertThat(ContentRange.parse("bytes 1-20/*"))
        .isEqualTo(new ContentRange(new ByteRange(1, 20), null));
  }

  @Test
  public void testInvalidContentRangeFormats() {
    doInvalidContentRangeFormatTest("");
    doInvalidContentRangeFormatTest("bytes");
    doInvalidContentRangeFormatTest("bytes ");
    doInvalidContentRangeFormatTest("Very bad");
    doInvalidContentRangeFormatTest("bytes=1-20/1000");
    doInvalidContentRangeFormatTest("bytes 1-20");
    doInvalidContentRangeFormatTest("bytes */1000");
    doInvalidContentRangeFormatTest("bytes */*");
  }

  @Test
  public void testContentRangeEquals() {
    ContentRange basicRange = new ContentRange(new ByteRange(0, 10), 100L);

    assertThat(basicRange).isEqualTo(new ContentRange(new ByteRange(0, 10), 100L));
    assertThat(basicRange).isNotEqualTo(new ContentRange(new ByteRange(1, 10), 100L));
    assertThat(basicRange).isNotEqualTo(new ContentRange(new ByteRange(0, 11), 100L));
    assertThat(basicRange).isNotEqualTo(new ContentRange(new ByteRange(0, 10), 101L));
    assertThat(basicRange).isNotEqualTo(new ContentRange(new ByteRange(0, 10), null));

    assertThat(new ContentRange(new ByteRange(0, 10), null))
        .isEqualTo(new ContentRange(new ByteRange(0, 10), null));
  }

  @Test
  public void testHashCode() {
    ContentRange basicRange = new ContentRange(new ByteRange(0, 10), 100L);
    ContentRange unknownLengthRange = new ContentRange(new ByteRange(0, 10), null);
    ContentRange differentLengthRange = new ContentRange(new ByteRange(0, 10), 101L);

    assertThat(basicRange.hashCode()).isNotEqualTo(unknownLengthRange.hashCode());
    assertThat(basicRange.hashCode()).isNotEqualTo(differentLengthRange.hashCode());
  }

  private void doInvalidContentRangeFormatTest(String contentRange) {
    try {
      ContentRange.parse(contentRange);
      fail("Expected RangeFormatException");
    } catch (ByteRange.RangeFormatException ex) {
      // expected
    }
  }
}
