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

package com.google.vr180.common.communication;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for adding end-of-messaging markers and making sure the message doesn't contain
 * them.
 *
 * <p>This coding is used for our
 */
public class MessageMarkers {

  private static final ByteString END_OF_MESSAGE = ByteString.copyFrom(new byte[] {0, 0});

  /** Indicates whether a message is finished (ends with the end-of-message marker). */
  public static boolean messageComplete(ByteString message) {
    return message.endsWith(END_OF_MESSAGE);
  }

  /** Replaces all EOM markers in the message and then adds one to the end. */
  public static byte[] encode(byte[] message) {
    ArrayList<Byte> result = new ArrayList<Byte>();

    for (int i = 0; i < message.length; ++i) {
      // Convert 0,0 to 0,1,0 and 0,1 to 0,1,1 by adding an extra 1 before the second byte.
      // This ensures the message body will never contain two consecutive 0s.
      if (i > 0 && message[i - 1] == 0 && (message[i] == 0 || message[i] == 1)) {
        result.add((byte) 1);
      }

      result.add(message[i]);
    }

    // If the message ends with a 0 add a 1 after it so it doesn't interfere with the end-of-message
    // marker (two zeros). This 1 will be ignored by the decoding routine since it follows a 0.
    if (result.size() > 0 && result.get(result.size() - 1) == 0) {
      result.add((byte) 1);
    }

    // Put 2 zeros in a row at the end.
    result.add((byte) 0);
    result.add((byte) 0);
    return toByteArray(result);
  }

  /**
   * Converts all encoded markers in a stream back to their original form. Message must end with an
   * end of message marker (two 0 bytes).
   */
  public static byte[] decode(byte[] message) {
    Preconditions.checkArgument(message.length >= 2);
    Preconditions.checkArgument(message[message.length - 2] == 0);
    Preconditions.checkArgument(message[message.length - 1] == 0);

    ArrayList<Byte> result = new ArrayList<Byte>();

    for (int i = 0; i < message.length - 2; ++i) {
      // Skip any 1 that's after a 0.
      if (i > 0 && message[i - 1] == 0 && message[i] == 1) {
        continue;
      }
      result.add(message[i]);
    }
    return toByteArray(result);
  }

  private static byte[] toByteArray(List<Byte> byteList) {
    byte[] result = new byte[byteList.size()];
    for (int i = 0; i < byteList.size(); ++i) {
      result[i] = byteList.get(i);
    }
    return result;
  }
}
