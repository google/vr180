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

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;

/**
 * The default implementation of CryptoProvider using Android default provider
 */
public class DefaultCipherProvider implements CryptoProvider {

  @Override
  public KeyPairGenerator getKeyPairGenerator() throws GeneralSecurityException {
    return KeyPairGenerator.getInstance(CryptoUtilities.ASYMMETRIC_KEY_TYPE);
  }

  @Override
  public KeyAgreement getKeyAgreement() throws GeneralSecurityException {
    return KeyAgreement.getInstance(CryptoUtilities.KEY_AGREEMENT_ALGORITHM);
  }

  @Override
  public KeyFactory getKeyFactory() throws GeneralSecurityException {
    return KeyFactory.getInstance(CryptoUtilities.ASYMMETRIC_KEY_TYPE);
  }

  @Override
  public Mac getMac() throws GeneralSecurityException {
    return Mac.getInstance(CryptoUtilities.HMAC_ALGORITHM);
  }

  @Override
  public Cipher getCipher() throws GeneralSecurityException {
    return Cipher.getInstance(CryptoUtilities.ENCRYPTION_TRANSFORMATION);
  }
}
