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

import com.google.vr180.common.crypto.CryptoUtilities;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;

/**
 * Helper class for generating and parsing the manufacturer specific data used in the Bluetooth LE
 * Advertising process. This data allows VR180 app to identify a previously paired camera so that it
 * can reconnect to it.
 */
public class BluetoothManufacturerDataHelper {
  // Note, these values must be quite small to fit in the 31 byte BLE packet along with a 16 byte
  // Service UUID. 6 bytes total matches the MAC address size. See
  // https://newcircle.com/s/post/1786/2016/01/04/bluetooth-uuids-and-interoperable-advertisements
  // for a description about the BLE advertisement packet.
  private static final int SALT_LENGTH = 3;
  private static final int HMAC_LENGTH = 3;

  public static byte[] generateManufacturerData(byte[] cameraPublicKey)
      throws CryptoUtilities.CryptoException {
    byte[] salt = CryptoUtilities.generateRandom(SALT_LENGTH);
    byte[] hmac = CryptoUtilities.generateHMAC(cameraPublicKey, Collections.singletonList(salt));
    byte[] result = new byte[SALT_LENGTH + HMAC_LENGTH];
    System.arraycopy(salt, 0, result, 0, SALT_LENGTH);
    System.arraycopy(hmac, 0, result, SALT_LENGTH, HMAC_LENGTH);
    return result;
  }

  public static boolean matchesPreviouslyPairedCamera(
      byte[] manufacturerData, byte[] cameraPublicKey) throws CryptoUtilities.CryptoException {
    if (manufacturerData.length != SALT_LENGTH + HMAC_LENGTH) {
      return false;
    }

    byte[] salt = Arrays.copyOf(manufacturerData, SALT_LENGTH);
    byte[] expectedHmac =
        Arrays.copyOf(
            CryptoUtilities.generateHMAC(cameraPublicKey, Collections.singletonList(salt)),
            HMAC_LENGTH);
    byte[] actualHmac =
        Arrays.copyOfRange(manufacturerData, SALT_LENGTH, SALT_LENGTH + HMAC_LENGTH);
    return MessageDigest.isEqual(actualHmac, expectedHmac);
  }
}
