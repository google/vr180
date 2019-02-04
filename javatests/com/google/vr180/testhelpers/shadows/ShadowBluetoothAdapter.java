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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeAdvertiser;
import java.util.concurrent.Callable;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

/** Shadow for android.bluetooth.BluetoothAdapter. */
@Implements(BluetoothAdapter.class)
public class ShadowBluetoothAdapter {

  private static boolean enabled = true;
  private static Callable<Boolean> enableHandler = null;

  public ShadowBluetoothAdapter() {}

  @Implementation
  public static BluetoothAdapter getDefaultAdapter() {
    return ReflectionHelpers.newInstance(BluetoothAdapter.class);
  }

  @Implementation
  public BluetoothLeAdvertiser getBluetoothLeAdvertiser() {
    return ReflectionHelpers.newInstance(BluetoothLeAdvertiser.class);
  }

  @Implementation
  public boolean isEnabled() {
    android.util.Log.e("bluetoothAdapter", "isEnabled = " + enabled);
    return enabled;
  }

  @Implementation
  public boolean isMultipleAdvertisementSupported() {
    return true;
  }

  @Implementation
  public boolean enable() {
    if (enableHandler != null) {
      try {
        return enableHandler.call();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } else {
      return false;
    }
  }

  public static void setEnableHandler(Callable<Boolean> enableHandler) {
    ShadowBluetoothAdapter.enableHandler = enableHandler;
  }

  public static void setEnabled(boolean enabled) {
    ShadowBluetoothAdapter.enabled = enabled;
  }
}
