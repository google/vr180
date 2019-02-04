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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Constants related to bluetooth for camera API. */
public class BluetoothConstants {
  /**
   * Service UUID that represents the camera API service.
   *
   * <p>Clients wishing to connect via bluetooth must specify this UUID.
   */
  public static final UUID CAMERA_SERVICE_UUID =
      UUID.fromString("49eabc2a-73b0-411e-a26d-75415dd7708e");

  /**
   * Service UUID advertised for camera that is in pairing mode. Clients shouldn't connect on this
   * UUID but use it to find a camera to pair with.
   */
  public static final UUID CAMERA_PAIRING_UUID =
      UUID.fromString("18723f72-8c4e-4dd7-8f3e-b93b9c29481f");

  /** The UUID of the GATT channel to send request data via BLE. */
  public static final UUID CAMERA_API_REQUEST_CHARACTERISTIC_UUID =
      UUID.fromString("48f03338-852e-4dd5-aa44-cd1b32fcaeb9");

  /** The UUID of the GATT channel to read data via BLE. */
  public static final UUID CAMERA_API_RESPONSE_CHARACTERISTIC_UUID =
      UUID.fromString("9f14e1da-4add-4ec7-aa34-6106669e2c12");

  public static final UUID CAMERA_API_STATUS_CHARACTERISTIC_UUID =
      UUID.fromString("a03fedd3-0923-4398-854e-e2806d159a7f");

  /** The number of bytes from the MTU that can't be used. */
  public static final int MTU_RESERVED_BYTES = 3;
  /** Default MTU when value is unknown. */
  public static final int DEFAULT_MTU = 23;
  /** The size of MTU to request. */
  public static final int DESIRED_MTU = 517;

  /** The base 128-bit UUID representation of a 16-bit UUID */
  public static final UUID BASE_16_BIT_UUID =
      UUID.fromString("00000000-0000-1000-8000-00805F9B34FB");

  /**
   * This descriptor shall be persistent across connections for bonded devices. The Client
   * Characteristic Configuration descriptor is unique for each client. A client may read and write
   * this descriptor to determine and set the configuration for that client. Authentication and
   * authorization may be required by the server to write this descriptor. The default value for the
   * Client Characteristic Configuration descriptor is 0x00. Upon connection of non-binded clients,
   * this descriptor is set to the default value.
   *
   * <p>See reserved UUID org.bluetooth.descriptor.gatt.client_characteristic_configuration.
   */
  public static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION = fromShortUuid((short) 0x2902);

  /** Converts a short bluetooth UUID to its long form. */
  public static UUID fromShortUuid(short shortUuid) {
    return new UUID(
        ((((long) shortUuid) << 32) & 0x0000FFFF00000000L)
            | BASE_16_BIT_UUID.getMostSignificantBits(),
        BASE_16_BIT_UUID.getLeastSignificantBits());
  }

  /** The "info" parameter to use in key generation in the HKDF RFC 5869 algorithm. */
  public static final byte[] KEY_INFO = "ENCRYPTION".getBytes(StandardCharsets.US_ASCII);
}
