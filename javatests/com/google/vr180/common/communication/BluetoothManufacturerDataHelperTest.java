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
import java.security.KeyPair;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class BluetoothManufacturerDataHelperTest {

  @Test
  public void testPreviouslyPairedMatches() throws Exception {
    // Create a key pair for the camera.
    KeyPair cameraKeyPair = CryptoUtilities.generateECDHKeyPair();
    byte[] cameraPublicKeyBytes =
        CryptoUtilities.convertECDHPublicKeyToBytes(cameraKeyPair.getPublic());

    // Generate a manufacturer specific data field.
    byte[] manufacturerData =
        BluetoothManufacturerDataHelper.generateManufacturerData(cameraPublicKeyBytes);

    // Check that we would recognize the camera.
    Assert.assertTrue(
        BluetoothManufacturerDataHelper.matchesPreviouslyPairedCamera(
            manufacturerData, cameraPublicKeyBytes));

    // Check that we would not recognize a different camera.
    KeyPair cameraKeyPair2 = CryptoUtilities.generateECDHKeyPair();
    byte[] cameraPublicKeyBytes2 =
        CryptoUtilities.convertECDHPublicKeyToBytes(cameraKeyPair2.getPublic());
    Assert.assertFalse(
        BluetoothManufacturerDataHelper.matchesPreviouslyPairedCamera(
            manufacturerData, cameraPublicKeyBytes2));
  }

  @Test
  public void testManufacturerDataRotates() throws Exception {
    // Create a key pair for the camera.
    KeyPair cameraKeyPair = CryptoUtilities.generateECDHKeyPair();
    byte[] cameraPublicKeyBytes =
        CryptoUtilities.convertECDHPublicKeyToBytes(cameraKeyPair.getPublic());

    // Generate a manufacturer specific data field.
    byte[] manufacturerData =
        BluetoothManufacturerDataHelper.generateManufacturerData(cameraPublicKeyBytes);
    // Generate another field.
    byte[] manufacturerData2 =
        BluetoothManufacturerDataHelper.generateManufacturerData(cameraPublicKeyBytes);

    // Check that they aren't the same.
    Assert.assertFalse(Arrays.equals(manufacturerData, manufacturerData2));
  }
}
