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
 * Interface for functions needed for encryption
 */
public interface CryptoProvider {
  /** Generate a key pair */
  KeyPairGenerator getKeyPairGenerator() throws GeneralSecurityException;

  /** Generate key agreement */
  KeyAgreement getKeyAgreement() throws GeneralSecurityException;

  /** Generate key factory */
  KeyFactory getKeyFactory() throws GeneralSecurityException;

  /** Generate MAC */
  Mac getMac() throws GeneralSecurityException;

  /** Generate cipher */
  Cipher getCipher() throws GeneralSecurityException;
}
