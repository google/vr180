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

package com.google.vr180.api.implementations;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.google.vr180.api.camerainterfaces.SslManager;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.security.auth.x500.X500Principal;

/**
 * Generates a self-signed certificate on construction, and provides an SSL context for accepting
 * HTTPS connections. Also provides the certificate signature for returning in the NetworkStatus.
 */
public class SelfSignedSslManager implements SslManager {
  private static final String TAG = "SelfSignedSslManager";

  /** The key store for generating and storing the SSL cert. */
  private static final String KEY_STORE_NAME = "AndroidKeyStore";
  /** The alias of the certificate to generate in the key store. */
  private static final String CERT_ALIAS = "ssl_cert";
  /** The X500 principal for the cert to generate. */
  private static final String CERT_PRINCIPAL = "CN=*";
  /** The protocol to use for the SSL context. */
  private static final String SSL_CONTEXT_PROTOCOL = "TLS";
  /** Elliptic curve spec to use for SSL cert. */
  private static final String SSL_CURVE = "prime256v1";
  /** How long the SSL certificate is valid. 365 days */
  private static final long CERT_EXPIRATION_TIME = 365L * 24 * 60 * 60 * 100;

  private final SSLContext sslContext;
  private final X509Certificate certificate;

  public static SelfSignedSslManager create() {
    try {
      return new SelfSignedSslManager();
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  public SelfSignedSslManager() throws GeneralSecurityException {
    // Create a new KeyStore.
    KeyStore keyStore = KeyStore.getInstance(KEY_STORE_NAME);
    try {
      keyStore.load(null, null);
    } catch (IOException e) {
      throw new GeneralSecurityException("Unable to initialize key store", e);
    }

    Date expirationDate = new Date(new Date().getTime() + CERT_EXPIRATION_TIME);
    KeyPairGenerator keyPairGenerator =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEY_STORE_NAME);
    keyPairGenerator.initialize(
        new KeyGenParameterSpec.Builder(CERT_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setCertificateSubject(new X500Principal(CERT_PRINCIPAL))
            .setCertificateNotAfter(expirationDate)
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(new ECGenParameterSpec(SSL_CURVE))
            .setUserAuthenticationRequired(false)
            .build());
    // Generate a key pair and store it in the KeyStore.
    keyPairGenerator.generateKeyPair();

    certificate = (X509Certificate) keyStore.getCertificate(CERT_ALIAS);
    // Make sure the cert is valid.
    certificate.checkValidity();

    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, null);

    sslContext = SSLContext.getInstance(SSL_CONTEXT_PROTOCOL);
    sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
  }

  @Override
  public ServerSocketFactory getServerSocketFactory() {
    return sslContext.getServerSocketFactory();
  }

  @Override
  public byte[] getCameraCertificateSignature() {
    return certificate.getSignature();
  }
}
