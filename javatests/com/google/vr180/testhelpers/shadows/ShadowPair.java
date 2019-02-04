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

import android.util.Pair;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;

/** Shadow of {@link android.util.Pair} */
@Implements(Pair.class)
public class ShadowPair {
  @RealObject private Pair realPair;

  public void __constructor__(Object first, Object second) {
    ReflectionHelpers.setField(realPair, "first", first);
    ReflectionHelpers.setField(realPair, "second", second);
  }

  @Implementation
  public static <F, S> Pair<F, S> create(F f, S s) {
    return new Pair<F, S>(f, s);
  }
}
