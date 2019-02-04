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

package com.google.vr180.app.stubs;

import android.bluetooth.BluetoothAssignedNumbers;
import com.google.vr180.device.DeviceInfo;

/** Stub DeviceInfo for an emulator (phone). */
public class EmulatorDeviceInfo implements DeviceInfo {

  @Override
  public long getImuTimestampOffsetNs() {
    return 0;
  }

  @Override
  public boolean isEmulator() {
    return true;
  }

  @Override
  public float[] deviceToImuTransform() {
    // Typically a camera has a 90 rotation about the Z-axis and 180 degrees about the Y-axis,
    // because the default camera is back-facing. This maps X=-Y, Y=-X, and Z=-Z.
    return new float[] {0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, -1f};
  }

  @Override
  public int getBluetoothManufacturerId() {
    return BluetoothAssignedNumbers.GOOGLE;
  }
}
