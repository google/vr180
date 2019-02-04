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

package com.google.vr180.common.crypto;

import com.google.vr180.common.communication.BluetoothConstants;
import java.security.KeyPair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class CryptoUtilitiesTest {

  @Test
  public void testEncryptionRoundtrip() throws Exception {
    byte[] aesKey = CryptoUtilities.generateRandom(32);
    byte[] plainText = CryptoUtilities.generateRandom(1234);
    Assert.assertArrayEquals(
        plainText, CryptoUtilities.decrypt(CryptoUtilities.encrypt(plainText, aesKey), aesKey));
  }

  @Test
  public void testKeyExchange() throws Exception {
    KeyPair phoneKeyPair = CryptoUtilities.generateECDHKeyPair();
    KeyPair cameraKeyPair = CryptoUtilities.generateECDHKeyPair();
    byte[] salt = CryptoUtilities.generateRandom(32);

    // Generate from the camera side.
    byte[] phonePublicKeyBytes =
        CryptoUtilities.convertECDHPublicKeyToBytes(phoneKeyPair.getPublic());
    byte[] cameraKeyData =
        CryptoUtilities.generateECDHMasterKey(cameraKeyPair, phonePublicKeyBytes);
    byte[] cameraKey =
        CryptoUtilities.generateHKDFBytes(cameraKeyData, salt, BluetoothConstants.KEY_INFO);

    // Generate from the phone side.
    byte[] cameraPublicKeyBytes =
        CryptoUtilities.convertECDHPublicKeyToBytes(cameraKeyPair.getPublic());
    byte[] phoneKeyData = CryptoUtilities.generateECDHMasterKey(phoneKeyPair, cameraPublicKeyBytes);
    byte[] phoneKey =
        CryptoUtilities.generateHKDFBytes(phoneKeyData, salt, BluetoothConstants.KEY_INFO);

    Assert.assertArrayEquals(phoneKey, cameraKey);
  }

  @Test
  public void testPublicKeySerialization() throws Exception {
    KeyPair keyPair = CryptoUtilities.generateECDHKeyPair();
    byte[] publicKeyBytes = CryptoUtilities.convertECDHPublicKeyToBytes(keyPair.getPublic());
    Assert.assertEquals(
        keyPair.getPublic(), CryptoUtilities.convertBytesToECDHPublicKey(publicKeyBytes));
  }
}
