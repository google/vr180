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

import com.google.vr180.common.logging.Log;
import java.lang.reflect.Method;

/** class for getting and setting system properties */
public class SystemUtil {
  private static final String TAG = "SystemUtil";
  private static final String SYSTERM_PROPERTY_CLASS_NAME = "android.os.SystemProperties";

  /**
   * Reads a system property set by {@code adb shell setprop} or {@code System.setProperty()}.
   * Returns null if the property is not set.
   */
  public static String getProperty(String name) {
    // First, try System.getProperty() for the case where the property has been set at runtime
    // with System.setProperty().
    String value = System.getProperty(name);
    if (value != null) {
      return value;
    }
    try {
      Class<?> c = Class.forName(SYSTERM_PROPERTY_CLASS_NAME);
      Method getprop = c.getDeclaredMethod("get", String.class);
      return (String) getprop.invoke(null, name);
    } catch (Exception e) {
      Log.e(TAG, "Unable to getprop ", e);
    }
    return null;
  }

  /** Sets a property globally. Note that all processses will see the same value. */
  public static boolean setProperty(String name, String value) {
    try {
      Class<?> c = Class.forName(SYSTERM_PROPERTY_CLASS_NAME);
      Method setprop = c.getDeclaredMethod("set", String.class, String.class);
      setprop.invoke(null, name, value);
    } catch (Exception e) {
      Log.e(TAG, "Unable to setprop ", e);
      return false;
    }
    return true;
  }
}
