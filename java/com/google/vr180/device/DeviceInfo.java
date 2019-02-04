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

package com.google.vr180.device;

/** A interface for device-specific information. */
public interface DeviceInfo {

  /**
   * Get the timestamp offset between IMU and Camera.
   *
   * This is the offset that should be ADDED to IMU timestamps.
   */
  long getImuTimestampOffsetNs();

  /**
   * Whether the app is running on an emulator.
   *
   * Some features are disabled when running as an emulator (phone) for convenience of development,
   * including not starting the app on device boot and not automatically controlling WiFi and
   * Bluetooth.
   */
  boolean isEmulator();

  /** A 3x3 rotation matrix describing the camera to imu coordinate system transform. */
  float[] deviceToImuTransform();

  /**
   * Gets the manufacturer identifier to use on bluetooth advertising. Should match the assigned
   * numbers from https://www.bluetooth.com/specifications/assigned-numbers/company-identifiers.
   */
  int getBluetoothManufacturerId();
}
