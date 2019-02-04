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

package com.google.vr180.communication.bluetooth;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiRequest.KeyExchangeRequest;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraApi.CameraApiResponse.KeyExchangeResponse;
import com.google.vr180.api.CameraApiHandler;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.PairingStatusListener;
import com.google.vr180.api.camerainterfaces.PairingStatusListener.PairingStatus;
import com.google.vr180.common.communication.BluetoothConstants;
import com.google.vr180.common.crypto.CryptoUtilities;
import com.google.vr180.common.crypto.CryptoUtilities.CryptoException;
import com.google.vr180.common.logging.Log;
import java.security.KeyPair;

/** The API handler for the key exchange api pairing. */
public class PairingApiHandler implements BluetoothSocketService.ConnectionHandler {
  private static final String TAG = "PairingApiHandler";
  /** Size of the salt to generate (matches the AES key size) */
  private static final int SALT_BYTES = 32;
  private static final long MAX_CONFIRMATION_TIME_MS = 10000;  // 10 seconds to confirm.

  private CameraSettings cameraSettings;
  private final PairingStatusListener pairingStatusListener;
  private KeyExchangeRequest keyExchangeInitiateRequest;
  private long keyExchangeInitiateTimestampMs = 0;
  private boolean userHasConfirmedPairing = false;
  private boolean pairingFinalized = false;

  public PairingApiHandler(
      CameraSettings cameraSettings,
      PairingStatusListener pairingStatusListener) {
    this.cameraSettings = cameraSettings;
    this.pairingStatusListener = pairingStatusListener;
  }

  @Override
  public byte[] handleRequest(byte[] requestData) {
    CameraApiRequest request;
    try {
      request = CameraApiRequest.parseFrom(requestData);
    } catch (InvalidProtocolBufferException e) {
      return CameraApiHandler.invalidRequestResponse().build().toByteArray();
    }

    // Handle any possible timeouts before dealing with the request.
    handleTimeouts();

    CameraApiResponse.Builder response;
    Log.d(TAG, "Handling request (type=" + request.getType() + ")");
    switch (request.getType()) {
      case KEY_EXCHANGE_INITIATE:
        try {
          response = handleKeyExchangeInitiateRequest(request.getKeyExchangeRequest());
        } catch (CryptoException e) {
          Log.e(TAG, "Failed to initiate a key exchange", e);
          response = CameraApiHandler.invalidRequestResponse();
        }
        break;
      case KEY_EXCHANGE_FINALIZE:
        try {
          response = handleKeyExchangeFinalizeRequest(request.getKeyExchangeRequest());
        } catch (CryptoException e) {
          Log.e(TAG, "Failed to finalize a key exchange", e);
          response = CameraApiHandler.invalidRequestResponse();
        }
        break;
      default:
        // This service supports nothing except KEY_EXCHANGE requests.
        response = CameraApiHandler.notSupportedResponse();
        break;
    }

    return response.setRequestId(request.getHeader().getRequestId()).build().toByteArray();
  }


  /**
   * Confirms the current KEY_EXCHANGE_INITIATE request. After confirmation, the next
   * KEY_EXCHANGE_FINALIZE request will be successful.
   */
  public synchronized boolean confirmLastKeyExchangeRequest() {
    handleTimeouts();
    if (keyExchangeInitiateRequest == null) {
      Log.e(TAG, "User confirmed pairing, but no initial pairing was found");
      return false;
    }
    userHasConfirmedPairing = true;
    return true;
  }

  public boolean isPairingFinalized() {
    return pairingFinalized;
  }

  private synchronized void handleTimeouts() {
    if (keyExchangeInitiateTimestampMs == 0
        || System.currentTimeMillis() < keyExchangeInitiateTimestampMs + MAX_CONFIRMATION_TIME_MS) {
      return;
    }
    pairingStatusListener.onPairingStatusChanged(PairingStatus.USER_CONFIRMATION_TIMEOUT);
    cancelCurrentKeyExchange();
  }

  private void cancelCurrentKeyExchange() {
    keyExchangeInitiateTimestampMs = 0;
    userHasConfirmedPairing = false;
    keyExchangeInitiateRequest = null;
    pairingFinalized = false;
  }

  /** Handle a request to do a Elliptic Curve Diffie Hellman key exchange */
  private synchronized CameraApiResponse.Builder handleKeyExchangeInitiateRequest(
      KeyExchangeRequest request) throws CryptoException {
    if (keyExchangeInitiateRequest != null) {
      Log.e(TAG, "invalid KEY_EXCHANGE_INITIATE request, not unique.");
      cancelCurrentKeyExchange();
      return CameraApiHandler.invalidRequestResponse();
    }
    // Save the initiate request, so we can check the finalize request is using the exact same one.
    keyExchangeInitiateRequest = request;
    keyExchangeInitiateTimestampMs = System.currentTimeMillis();
    userHasConfirmedPairing = false;
    pairingFinalized = false;

    if (pairingStatusListener != null) {
      pairingStatusListener.onPairingStatusChanged(PairingStatus.WAITING_FOR_USER_CONFIRMATION);
    }

    byte[] phonePublicKeyBytes = request.getPublicKey().toByteArray();
    byte[] phoneSalt = request.getSalt().toByteArray();
    if (phoneSalt.length != SALT_BYTES) {
      Log.e(TAG, "Request salt is the wrong size.");
      return CameraApiHandler.invalidRequestResponse();
    }
    byte[] cameraSalt = CryptoUtilities.generateRandom(SALT_BYTES);
    byte[] salt = CryptoUtilities.xor(phoneSalt, cameraSalt);

    // Fetch our key pair.
    KeyPair keyPair = cameraSettings.getLocalKeyPair();
    byte[] publicKeyBytes = CryptoUtilities.convertECDHPublicKeyToBytes(keyPair.getPublic());

    // Calculate the shared key, and save it to permanent storage.
    byte[] keyMaterial = CryptoUtilities.generateECDHMasterKey(keyPair, phonePublicKeyBytes);
    byte[] sharedKey =
        CryptoUtilities.generateHKDFBytes(keyMaterial, salt, BluetoothConstants.KEY_INFO);
    cameraSettings.setSharedKey(sharedKey);
    cameraSettings.setSharedKeyIsPending(true);

    // Respond with our public key and salt.
    KeyExchangeResponse keyResponse =
        KeyExchangeResponse.newBuilder()
            .setPublicKey(ByteString.copyFrom(publicKeyBytes))
            .setSalt(ByteString.copyFrom(cameraSalt))
            .build();
    return CameraApiHandler.okResponse().setKeyExchangeResponse(keyResponse);
  }

  /** Handle a request to finalize a key exchange */
  private synchronized CameraApiResponse.Builder handleKeyExchangeFinalizeRequest(
      KeyExchangeRequest request) throws CryptoException {
    if (!sameKeyExchangeRequest(keyExchangeInitiateRequest, request)) {
      Log.e(TAG, "KEY_EXCHANGE_FINALIZE request not valid.");
      return CameraApiHandler.invalidRequestResponse();
    }

    if (!userHasConfirmedPairing) {
      Log.e(TAG, "KEY_EXCHANGE_FINALIZE cannot finalize, user has not confirmed pairing yet.");
      return CameraApiHandler.invalidRequestResponse();
    }

    keyExchangeInitiateRequest = null;
    userHasConfirmedPairing = false;
    pairingFinalized = true;

    if (pairingStatusListener != null) {
      pairingStatusListener.onPairingStatusChanged(PairingStatus.PAIRED);
    }
    cameraSettings.setSharedKeyIsPending(false);

    return CameraApiHandler.okResponse();
  }

  private static boolean sameKeyExchangeRequest(KeyExchangeRequest request1,
      KeyExchangeRequest request2) {
    if (request1 == null || request2 == null) {
      return false;
    }
    if (!request1.hasPublicKey() || !request1.hasSalt()) {
      return false;
    }
    return request1.getPublicKey().equals(request2.getPublicKey())
        && request1.getSalt().equals(request2.getSalt());
  }
}
