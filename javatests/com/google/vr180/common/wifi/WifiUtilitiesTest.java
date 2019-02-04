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

package com.google.vr180.common.wifi;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class WifiUtilitiesTest {

  @Test
  public void testTrimQuotes() throws Exception {
    Assert.assertEquals(
        "Direct-TA-Android_8db6", WifiUtilities.trimQuotes("\"Direct-TA-Android_8db6\""));
    Assert.assertEquals("abc", WifiUtilities.trimQuotes("abc"));
    Assert.assertEquals("\"abc", WifiUtilities.trimQuotes("\"abc"));
  }

  @Test
  public void testSameNetwork() throws Exception {
    Assert.assertTrue(
        WifiUtilities.sameNetwork("Direct-TA-Android_8db6", "\"Direct-TA-Android_8db6\""));
    Assert.assertTrue(
        WifiUtilities.sameNetwork("Direct-TA-Android_8db6", "Direct-TA-Android_8db6"));
    Assert.assertFalse(WifiUtilities.sameNetwork("Direct-TA-Android_8db6", "abc"));
  }
}
