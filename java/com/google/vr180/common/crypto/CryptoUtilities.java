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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Utility methods for cryptography primitives used in Bluetooth LE. */
public class CryptoUtilities {

  /**
   * Custom exception that hides error detail from the caller yet makes sure it knows to terminate.
   */
  public static class CryptoException extends Exception {

    public CryptoException(String message) {
      super(message);
    }

    public CryptoException(String message, Throwable throwable) {
      super(message, throwable);
    }
  }

  public static final String ENCRYPTION_TRANSFORMATION = "AES/GCM/NoPadding";
  public static final String ASYMMETRIC_KEY_TYPE = "EC";
  public static final String KEY_AGREEMENT_ALGORITHM = "ECDH";

  // Explicitly specify gms core open ssl because we download it dynamically and it's the only
  // provider we know will be present and support the operations we need.
  public static final String HMAC_ALGORITHM = "HmacSHA256";

  private static final String ENCRYPTION_ALGORITHM = "AES";
  // Prefix for an uncompressed elliptic curve public key point.
  private static final byte EC_UNCOMPRESSED_POINT_PREFIX = 0x04;
  private static final int EC_PUBLIC_KEY_BYTES_LENGTH = 65;
  private static final String EC_CURVE = "prime256v1";
  private static final ECParameterSpec EC_SPEC = getNistP256Params();
  private static final int FIELD_SIZE_IN_BYTES =
      (EC_SPEC.getCurve().getField().getFieldSize() + 7) / 8;
  private static final byte[] HKDF_CONSTANT_01 = { 0x01 };
  private static final int IV_BYTE_COUNT = 12;
  private static final int VERSION = 1;
  private static final int VERSION_BYTE_COUNT = 1;
  private static final int GCM_AUTHENTICATION_TAG_BIT_COUNT = 128;

  private static CryptoProvider cryptoProvider = new DefaultCipherProvider();

  public static synchronized void setCryptoProvider(CryptoProvider cryptoProvider) {
    CryptoUtilities.cryptoProvider = cryptoProvider;
  }

  /** Generate a key pair on NIST P-256 curve for elliptic curve Diffie-Hellman. */
  public static KeyPair generateECDHKeyPair() throws CryptoException {
    try {
      KeyPairGenerator generator = cryptoProvider.getKeyPairGenerator();
      ECGenParameterSpec ecSpec = new ECGenParameterSpec(EC_CURVE);
      generator.initialize(ecSpec, new SecureRandom());
      return generator.genKeyPair();
    } catch (GeneralSecurityException e)  {
      throw new CryptoException("Failed to generate local ECDH key pair.");
    }
  }

  /** Generate the master key - the result of the elliptic curve Diffie-Hellman key exchange. */
  public static byte[] generateECDHMasterKey(KeyPair localKeyPair, byte[] peerPublicKeyBytes)
      throws CryptoException {
    try {
      KeyAgreement keyAgreement = cryptoProvider.getKeyAgreement();
      keyAgreement.init(localKeyPair.getPrivate());
      keyAgreement.doPhase(convertBytesToECDHPublicKey(peerPublicKeyBytes), true);
      return keyAgreement.generateSecret();
    } catch (GeneralSecurityException e) {
      throw new CryptoException("Failed to generate ecdh shared key.", e);
    }
  }

  /**
   * Convert peer public key raw bytes into PublicKey format. This method is adapted from
   * Bouncy Castle's decodePoint method in ECCurve.java. We don't call the getEncoded method
   * directly because 1) depending on Bouncy Castle for two utility methods is a bit of an overkill
   * 2) we would have to juggle java and Bouncy Castle's interfaces, and 3) dependency on Bouncy
   * Castle is recommended against by the security team.
   **/
  public static PublicKey convertBytesToECDHPublicKey(byte[] peerPublicKeyBytes)
      throws CryptoException {
    if (!checkECPublicKeyBytesFormat(peerPublicKeyBytes)) {
      throw new CryptoException("Peer public key is in invalid format.");
    }
    byte[] x = Arrays.copyOfRange(peerPublicKeyBytes, 1, FIELD_SIZE_IN_BYTES + 1);
    byte[] y =
        Arrays.copyOfRange(peerPublicKeyBytes, FIELD_SIZE_IN_BYTES + 1, peerPublicKeyBytes.length);
    try {
      KeyFactory keyFactory = cryptoProvider.getKeyFactory();
      ECPublicKeySpec peerPublicKeySpec =
          new ECPublicKeySpec(
              new ECPoint(new BigInteger(1 /* positive */, x), new BigInteger(1, y)), EC_SPEC);
      return keyFactory.generatePublic(peerPublicKeySpec);
    } catch (GeneralSecurityException e) {
      throw new CryptoException("Failed to convert peerPublicKeyBytes to public key object");
    }
  }

  /** Checks that we have a valid EC public key format: 65 bytes in length and has 0x04 prefix. */
  private static boolean checkECPublicKeyBytesFormat(byte[] ecPublicKey) {
    return (ecPublicKey != null)
        && (ecPublicKey.length == EC_PUBLIC_KEY_BYTES_LENGTH)
        && (ecPublicKey[0] == EC_UNCOMPRESSED_POINT_PREFIX);
  }

  /**
   * Convert local ECDH Public Key into raw bytes. This method is adapted from Bouncy Castle's
   * getEncoded method in ECPoint.java.
   **/
  public static byte[] convertECDHPublicKeyToBytes(PublicKey localPublicKey) {
    ECPublicKey localECPublicKey = (ECPublicKey) localPublicKey;
    ECPoint localECPublicPoint = localECPublicKey.getW();
    byte[] x = ecCoordinateToBytes(localECPublicPoint.getAffineX());
    byte[] y = ecCoordinateToBytes(localECPublicPoint.getAffineY());
    // First byte denotes whether the public key uses compression or not, here since it is
    // uncompressed it should be 0x04.
    byte[] localPublicKeyBytes = new byte[x.length + y.length + 1];
    localPublicKeyBytes[0] = 0x04;
    System.arraycopy(x, 0, localPublicKeyBytes, 1, x.length);
    System.arraycopy(y, 0, localPublicKeyBytes, x.length + 1, y.length);
    return localPublicKeyBytes;
  }

  /** Adapted from Bouncy Castle's asn1.x9.X9IntegerConverter.integerToBytes method **/
  private static byte[] ecCoordinateToBytes(BigInteger coordinate) {
    byte[] coordinateBytes = coordinate.toByteArray();
    byte[] correctedBytes = new byte[FIELD_SIZE_IN_BYTES];
    if (FIELD_SIZE_IN_BYTES < coordinateBytes.length) {
      System.arraycopy(coordinateBytes, coordinateBytes.length - correctedBytes.length,
          correctedBytes, 0, correctedBytes.length);
      return correctedBytes;
    } else if (FIELD_SIZE_IN_BYTES > coordinateBytes.length) {
      System.arraycopy(coordinateBytes, 0, correctedBytes,
          correctedBytes.length - coordinateBytes.length, coordinateBytes.length);
      return correctedBytes;
    }
    return coordinateBytes;
  }

  /** Generate a len-byte-long random number in byte array format. */
  public static byte[] generateRandom(int len) {
    SecureRandom secureRandom = new SecureRandom();
    byte[] random = new byte[len];
    secureRandom.nextBytes(random);
    return random;
  }

  /** Computes the XOR of two arrays. */
  public static byte[] xor(byte[] data1, byte[] data2) throws CryptoException {
    if (data1.length != data2.length) {
      throw new CryptoException("Array lengths don't match");
    }
    byte[] result = new byte[data1.length];
    for (int i = 0; i < data1.length; ++i) {
      result[i] = (byte) (data1[i] ^ data2[i]);
    }
    return result;
  }

  /** Computes the SHA2 hash of the provided data. */
  public static byte[] sha2Hash(byte[] data) throws CryptoException {
    MessageDigest digest = null;
    try {
      digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(data);
    } catch (GeneralSecurityException e) {
      throw new CryptoException("SHA-256 is missing.", e);
    }
  }

  /** Generate an HMAC of the concatenation of messages in messageList. */
  public static byte[] generateHMAC(byte[] hmacKey, List<byte[]> messageList)
      throws CryptoException {
    try {
      Mac hmac = cryptoProvider.getMac();
      SecretKeySpec secretKeySpec = new SecretKeySpec(hmacKey, HMAC_ALGORITHM);
      hmac.init(secretKeySpec);
      for (byte[] message : messageList) {
        hmac.update(message);
      }
      return hmac.doFinal();
    } catch (GeneralSecurityException e) {
      throw new CryptoException("Failed to generate HMAC.", e);
    }
  }

  /** Implements HKDF (RFC 5869) with the SHA-256 hash and a 256-bit output key length. */
  public static byte[] generateHKDFBytes(byte[] inputKeyMaterial, byte[] salt, byte[] info)
      throws CryptoException {
    if ((inputKeyMaterial == null) || (salt == null) || (info == null)) {
      throw new CryptoException("HKDF failed because one of the input is null");
    }
    return hkdfSha256Expand(hkdfSha256Extract(inputKeyMaterial, salt), info);
  }

  /**
   * The HKDF (RFC 5869) extraction function, using the SHA-256 hash function. This function is
   * used to pre-process the inputKeyMaterial and mix it with the salt, producing output suitable
   * for use with HKDF expansion function (which produces the actual derived key).
   */
  private static byte[] hkdfSha256Extract(byte[] inputKeyMaterial, byte[] salt)
      throws CryptoException {
    List<byte[]> hmacMessage = new ArrayList<>();
    hmacMessage.add(inputKeyMaterial);
    return generateHMAC(salt, hmacMessage);
  }

  /**
   * Special case of HKDF (RFC 5869) expansion function, using the SHA-256 hash function and
   * allowing for a maximum output length of 256 bits.
   */
  private static byte[] hkdfSha256Expand(byte[] pseudoRandomKey, byte[] info)
      throws CryptoException {
    // Note that RFC 5869 computes number of blocks N = ceil(hash length / output length), but
    // here since our output is 256 bit, N=1.
    List<byte[]> hmacMessage = new ArrayList<>();
    hmacMessage.add(info);
    hmacMessage.add(HKDF_CONSTANT_01);
    return generateHMAC(pseudoRandomKey, hmacMessage);
  }

  /**
   * Returns the ECParameterSpec for NIST P-256 curve.
   *
   * <p>JCE knows two classes to specify parameters for elliptic curve crypto:
   *
   * <ul>
   *   <li> ECParameterSpec can be used to specify the parameters by explicitly constructing, the
   *       elliptic curve, generator and order.
   *   <li> ECGenParameterSpec can be used to specify a curve by name.
   * </ul>
   *
   * Both classes are subgroups of AlgorithmParameterSpec. Unfortunately, there are methods that
   * require ECParameterSpec and so far I'm not aware of a simple method using JCE that converts an
   * ECGenParameterSpec or a name into ECParameterSpec. E.g. one possible conversion is
   * sun.security.ec.NamedCurve.getECParameterSpec("secp256r1"); However, this requires to call a
   * provider specific method and thus makes the code dependent on this provider.
   *
   * @return the parameter specification for NIST P-256 curve.
   */
  private static ECParameterSpec getNistP256Params() {
    final BigInteger p =
        new BigInteger(
            "115792089210356248762697446949407573530086143415290314195533631308867097853951");
    final BigInteger n =
        new BigInteger(
            "115792089210356248762697446949407573529996955224135760342422259061068512044369");
    final BigInteger three = new BigInteger("3");
    final BigInteger a = p.subtract(three);
    final BigInteger b =
        new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16);
    final BigInteger gX =
        new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16);
    final BigInteger gY =
        new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16);
    final int h = 1;
    ECFieldFp fp = new ECFieldFp(p);
    EllipticCurve curve = new EllipticCurve(fp, a, b);
    java.security.spec.ECPoint g = new java.security.spec.ECPoint(gX, gY);
    ECParameterSpec nistp256 = new ECParameterSpec(curve, g, n, h);
    return nistp256;
  }

  /**
   * Encrypt the message using the given key, generate the output in the order of version number,
   * iv, cipherText (with authenticationTag).
   */
  public static byte[] encrypt(byte[] message, byte[] key) throws CryptoException {
    byte[] iv = generateRandom(IV_BYTE_COUNT);
    Cipher encryptCipher = setupCipher(key, iv, Cipher.ENCRYPT_MODE);
    try {
      byte[] cipherTextWithTag = encryptCipher.doFinal(message);
      byte[] version = new byte[] {VERSION};
      return ByteBuffer.allocate(version.length + iv.length + cipherTextWithTag.length)
          .put(version)
          .put(iv)
          .put(cipherTextWithTag)
          .array();
    } catch (GeneralSecurityException e) {
      throw new CryptoException("Failed to encrypt.", e);
    }
  }

  /** Decrypt the given cipherText with meta data using the given key. */
  public static byte[] decrypt(byte[] cipherTextWithMeta, byte[] key) throws CryptoException {
    if (cipherTextWithMeta.length == 0) {
      throw new CryptoException("Cipher text is empty.");
    }
    if (cipherTextWithMeta[0] != VERSION) {
      throw new CryptoException(
          "Version numbers do not match. " + cipherTextWithMeta[0] + "!=" + VERSION);
    }
    byte[] cipherTextWithTag = new byte[cipherTextWithMeta.length - VERSION_BYTE_COUNT
        - IV_BYTE_COUNT];
    System.arraycopy(
        cipherTextWithMeta,
        VERSION_BYTE_COUNT + IV_BYTE_COUNT,
        cipherTextWithTag,
        0,
        cipherTextWithTag.length);
    byte[] iv =
        Arrays.copyOfRange(
            cipherTextWithMeta, VERSION_BYTE_COUNT, VERSION_BYTE_COUNT + IV_BYTE_COUNT);
    Cipher decryptCipher = setupCipher(key, iv, Cipher.DECRYPT_MODE);
    try {
      return decryptCipher.doFinal(cipherTextWithTag);
    } catch (GeneralSecurityException e) {
      throw new CryptoException("Failed to decrypt.", e);
    }
  }

  /** Set up the cipher with key, iv, and whether encryption or decryption is intended. */
  private static Cipher setupCipher(byte[] key, byte[] iv, int mode) throws CryptoException {
    SecretKey secretKey = new SecretKeySpec(key, ENCRYPTION_ALGORITHM);
    GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_AUTHENTICATION_TAG_BIT_COUNT, iv);
    try {
      Cipher cipher = cryptoProvider.getCipher();
      cipher.init(mode, secretKey, gcmParameterSpec);
      return cipher;
    } catch (GeneralSecurityException e) {
      throw new CryptoException("Failed to set up cipher.", e);
    }
  }

}
