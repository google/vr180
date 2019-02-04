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

package com.google.vr180.testhelpers.shadows;

import android.media.ExifInterface;
import java.util.HashMap;
import java.util.Map;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Shadow for android.media.ExifInterface. */
@Implements(ExifInterface.class)
public class ShadowExifInterface {
  private static final Map<String, String> attributes = new HashMap<String, String>();

  public void __constructor__(String path) {}

  @Implementation
  public String getAttribute(String tag) {
    return attributes.get(tag);
  }

  public static void setAttribute(String tag, String value) {
    attributes.put(tag, value);
  }

  public static void clearAttributes() {
    attributes.clear();
  }
}
