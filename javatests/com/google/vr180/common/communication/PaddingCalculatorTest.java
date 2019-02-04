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
import com.google.vr180.CameraApi.WifiAccessPointInfo;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class PaddingCalculatorTest {

  @Test
  public void testMessagePadding() throws Exception {
    Assert.assertEquals(9, PaddingCalculator.computePadding("a").length);
    Assert.assertEquals(8, PaddingCalculator.computePadding("ab").length);
    Assert.assertEquals(0, PaddingCalculator.computePadding("").length);
    Assert.assertEquals(7, PaddingCalculator.computePadding("我").length);
  }

  @Test
  public void checkMessageLengths() throws Exception {
    int baseLength = encodeWifiInfo("a").length;

    Assert.assertEquals(baseLength, encodeWifiInfo("b").length);
    Assert.assertEquals(baseLength, encodeWifiInfo("ab").length);
    Assert.assertEquals(baseLength, encodeWifiInfo("abc").length);
    Assert.assertEquals(baseLength, encodeWifiInfo("abcdefg").length);
    Assert.assertEquals(baseLength, encodeWifiInfo("我").length);
  }

  /** Encodes a test message that contains a wifi password. */
  private static byte[] encodeWifiInfo(String password) {
    return WifiAccessPointInfo.newBuilder()
        .setSsid("test network")
        .setPassword(password)
        .setPadding(ByteString.copyFrom(PaddingCalculator.computePadding(password)))
        .build()
        .toByteArray();
  }
}
