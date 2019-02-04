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

import com.google.vr180.communication.http.ByteRange.RangeFormatException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Contains a ByteRange and length parsed from a Content-Range header.
 *
 * <p>For example, a header like: Content-Range: bytes 15-30/100
 *
 * <p>Parses into a byte range representing bytes 15 through 30, and a length of 100.
 */
public class ContentRange {

  private static final String BYTES_UNIT = "bytes";
  private static final String UNKNOWN_LENGTH = "*";
  private static final String VALID_CONTENT_RANGE_HEADER_REGEX =
      BYTES_UNIT + "\\s+(\\d+)-(\\d+)/(\\d+|\\*)";
  private static final Pattern CONTENT_RANGE_HEADER_PATTERN =
      Pattern.compile("^\\s*" + VALID_CONTENT_RANGE_HEADER_REGEX + "\\s*$");

  private final ByteRange byteRange;
  private final Long length;

  /**
   * Parse content range from header.
   *
   * @param contentRange Content range string as received from header.
   * @return ContentRange object set to byte range and length as parsed from the string.
   * @throws RangeFormatException Unable to parse header because of invalid format.
   */
  public static ContentRange parse(String contentRange) throws RangeFormatException {
    Matcher matcher = CONTENT_RANGE_HEADER_PATTERN.matcher(contentRange);
    if (!matcher.matches()) {
      throw new RangeFormatException("Invalid content-range format: " + contentRange);
    }

    try {
      ByteRange byteRange =
          new ByteRange(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)));

      if (matcher.group(3).equals(UNKNOWN_LENGTH)) {
        return new ContentRange(byteRange, /* length= */ null);
      } else {
        return new ContentRange(byteRange, Long.parseLong(matcher.group(3)));
      }
    } catch (NumberFormatException e) {
      throw new RangeFormatException("Unable to parse ContentRange.", e);
    }
  }

  /**
   * Creates a ContentRange with the specified range and total length.
   *
   * @param byteRange The range of bytes specified in the content range.
   * @param length The total length of the file, which byteRange is taken from. Can be null if the
   *     total length is unknown.
   */
  public ContentRange(ByteRange byteRange, @Nullable Long length) {
    this.byteRange = byteRange;
    this.length = length;
  }

  public ByteRange getByteRange() {
    return byteRange;
  }

  public long getLength() {
    if (!hasLength()) {
      throw new IllegalStateException(
          "Content-range does not have length.  Check hasLength() before use");
    }
    return length;
  }

  public boolean hasLength() {
    return length != null;
  }

  @Override
  public String toString() {
    if (hasLength()) {
      return byteRange + "/" + length;
    } else {
      return byteRange + "/" + UNKNOWN_LENGTH;
    }
  }

  @Override
  public int hashCode() {
    int result = byteRange.hashCode();
    if (length != null) {
      result = result ^ length.hashCode();
    }
    return result;
  }

  /**
   * Two {@code ContentRange} objects are considered equal if they have the same byte range and
   * length.
   */
  @Override
  public boolean equals(Object object) {
    if (!(object instanceof ContentRange)) {
      return false;
    }

    ContentRange other = (ContentRange) object;
    if (!byteRange.equals(other.getByteRange())) {
      return false;
    }

    if (hasLength() != other.hasLength()) {
      return false;
    }

    if (hasLength()) {
      return length.equals(other.getLength());
    } else {
      return true;
    }
  }
}
