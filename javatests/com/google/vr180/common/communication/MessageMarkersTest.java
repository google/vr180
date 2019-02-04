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

import com.google.protobuf.ByteString;
import com.google.vr180.common.crypto.CryptoUtilities;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MessageMarkersTest {

  @Test
  public void testSimpleMessage() throws Exception {
    byte[] testMessage = new byte[] {1, 2, 3};
    byte[] roundTrip = MessageMarkers.decode(MessageMarkers.encode(testMessage));
    Assert.assertArrayEquals(testMessage, roundTrip);
  }

  @Test
  public void testMessageWithMarker() throws Exception {
    byte[] testMessage = new byte[] {0, 0, 0};
    byte[] roundTrip = MessageMarkers.decode(MessageMarkers.encode(testMessage));
    Assert.assertArrayEquals(testMessage, roundTrip);
  }

  @Test
  public void testMessageWithEncodedMarker() throws Exception {
    byte[] testMessage = new byte[] {0, 1, 0};
    byte[] roundTrip = MessageMarkers.decode(MessageMarkers.encode(testMessage));
    Assert.assertArrayEquals(testMessage, roundTrip);
  }

  @Test
  public void testMessageWithEncodedMarker2() throws Exception {
    byte[] testMessage = new byte[] {0, 1, 1};
    byte[] roundTrip = MessageMarkers.decode(MessageMarkers.encode(testMessage));
    Assert.assertArrayEquals(testMessage, roundTrip);
  }

  @Test
  public void testRandomMessage() throws Exception {
    byte[] testMessage = CryptoUtilities.generateRandom(5000);
    byte[] roundTrip = MessageMarkers.decode(MessageMarkers.encode(testMessage));
    Assert.assertArrayEquals(testMessage, roundTrip);
  }

  @Test
  public void testMultipleEncodeDecods() throws Exception {
    byte[] testMessage = CryptoUtilities.generateRandom(5000);

    byte[] encoded = testMessage;
    for (int i = 0; i < 20; ++i) {
      encoded = MessageMarkers.encode(encoded);
    }

    byte[] decoded = encoded;
    for (int i = 0; i < 20; ++i) {
      decoded = MessageMarkers.decode(decoded);
    }

    Assert.assertArrayEquals(decoded, testMessage);
  }

  @Test
  public void testTruncatedMessages() throws Exception {
    byte[] testMessage = new byte[] {0};
    ByteString encoded = ByteString.copyFrom(MessageMarkers.encode(testMessage));

    Assert.assertTrue(MessageMarkers.messageComplete(encoded));
    Assert.assertArrayEquals(testMessage, MessageMarkers.decode(encoded.toByteArray()));

    for (int i = 0; i < encoded.size(); ++i) {
      ByteString truncated = encoded.substring(0, i);
      Assert.assertFalse(MessageMarkers.messageComplete(truncated));
    }
  }
}
