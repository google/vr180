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

package com.google.vr180.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple class-to-instance map used to inject real implementations at run time and fake ones for
 * testing.
 */
public class InstanceMap {
  private static final Map<Class<?>, Object> instanceMap = new HashMap();

  /**
   * Binds an instance for the given class.
   * This does no dependency resolution whatsover. You are on your own to provide instances in the
   * correct order.
   */
  public static synchronized <T> void put(Class<T> type, T instance) {
    instanceMap.put(type, instance);
  }

  /**
   * Returns the previously-bound instance for the given class.
   */
  public static synchronized <T> T get(Class<T> type) {
    Object instance = instanceMap.get(type);
    if (instance == null) {
      throw new IllegalStateException("No instance for " + type.getName() + " has been provided.");
    }
    return (T) instance;
  }
}
