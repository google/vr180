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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiRequest.KeyExchangeRequest;
import com.google.vr180.CameraApi.CameraApiRequest.RequestHeader;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ResponseStatus.StatusCode;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.PairingStatusListener;
import com.google.vr180.common.communication.BluetoothConstants;
import com.google.vr180.common.crypto.CryptoUtilities;
import java.security.KeyPair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class PairingApiHandlerTest {
  private PairingApiHandler handler;
  private KeyPair phoneKeyPair;
  private CameraSettings settings;
  private byte[] sharedKey;
  private boolean isSharedKeyPending;

  @Before
  public void setup() throws Exception {
    phoneKeyPair = CryptoUtilities.generateECDHKeyPair();
    settings = Mockito.mock(CameraSettings.class);
    when(settings.getLocalKeyPair()).thenReturn(phoneKeyPair);
    when(settings.getSharedKey()).thenAnswer(invocation -> sharedKey);
    when(settings.getSharedKeyIsPending()).thenAnswer(invocation -> isSharedKeyPending);
    doAnswer(invocation -> {
      sharedKey = (byte[]) invocation.getArguments()[0];
      return null;
    }).when(settings).setSharedKey(Mockito.any());
    doAnswer(invocation -> {
      isSharedKeyPending = (boolean) invocation.getArguments()[0];
      return null;
    }).when(settings).setSharedKeyIsPending(anyBoolean());
    handler = new PairingApiHandler(settings, Mockito.mock(PairingStatusListener.class));
  }

  @Test
  public void testInitiateKeyExchangeRequest() throws Exception {
    KeyExchangeRequest keyExchangeRequest = createKeyExchangeRequest();
    CameraApiRequest request = createCameraInitiateRequest(keyExchangeRequest);

    CameraApiResponse response =
        CameraApiResponse.parseFrom(handler.handleRequest(request.toByteArray()));

    checkResponseStatus(response, StatusCode.OK);
    assertThat(response.hasKeyExchangeResponse()).isTrue();
    assertThat(response.hasRequestId()).isTrue();
    assertThat(response.getRequestId()).isEqualTo(19);
    byte[] salt =
        CryptoUtilities.xor(
            response.getKeyExchangeResponse().getSalt().toByteArray(),
            keyExchangeRequest.getSalt().toByteArray());

    byte[] keyData =
        CryptoUtilities.generateECDHMasterKey(
            phoneKeyPair, response.getKeyExchangeResponse().getPublicKey().toByteArray());
    byte[] sharedKey =
        CryptoUtilities.generateHKDFBytes(keyData, salt, BluetoothConstants.KEY_INFO);
    assertThat(settings.getSharedKey()).isEqualTo(sharedKey);
  }

  @Test
  public void testConcurrentKeyExchange() throws Exception {
    // A 1st initiate request will succeed.
    KeyExchangeRequest keyExchangeRequest = createKeyExchangeRequest();
    CameraApiRequest request = createCameraInitiateRequest(keyExchangeRequest);
    CameraApiResponse response =
        CameraApiResponse.parseFrom(handler.handleRequest(request.toByteArray()));
    checkResponseStatus(response, StatusCode.OK);

    // A second initiate request will fail.
    KeyExchangeRequest keyExchangeRequest2 = createKeyExchangeRequest();
    CameraApiRequest request2 = createCameraInitiateRequest(keyExchangeRequest2);
    response = CameraApiResponse.parseFrom(handler.handleRequest(request2.toByteArray()));
    checkResponseStatus(response, StatusCode.INVALID_REQUEST);

    // Confirm the 1st key exchange.
    handler.confirmLastKeyExchangeRequest();

    // We cannot finalize the 2nd request.
    request = createCameraFinalizeRequest(keyExchangeRequest2);
    response = CameraApiResponse.parseFrom(handler.handleRequest(request.toByteArray()));
    checkResponseStatus(response, StatusCode.INVALID_REQUEST);

    // We cannot finalize the 1st request either.
    request = createCameraFinalizeRequest(keyExchangeRequest);
    response = CameraApiResponse.parseFrom(handler.handleRequest(request.toByteArray()));
    checkResponseStatus(response, StatusCode.INVALID_REQUEST);
  }

  @Test
  public void testFinalizeKeyExchangeWithoutInitiate() throws Exception {
    KeyExchangeRequest keyExchangeRequest = createKeyExchangeRequest();
    CameraApiRequest request = createCameraFinalizeRequest(keyExchangeRequest);

    CameraApiResponse response =
        CameraApiResponse.parseFrom(handler.handleRequest(request.toByteArray()));

    checkResponseStatus(response, StatusCode.INVALID_REQUEST);
  }

  @Test
  public void testFinalizeKeyExchangeAfterInitiate() throws Exception {
    KeyExchangeRequest keyExchangeRequest = createKeyExchangeRequest();
    CameraApiRequest initiateRequest = createCameraInitiateRequest(keyExchangeRequest);
    CameraApiResponse initiateResponse =
        CameraApiResponse.parseFrom(handler.handleRequest(initiateRequest.toByteArray()));
    checkResponseStatus(initiateResponse, StatusCode.OK);

    CameraApiRequest finalizeRequest = createCameraFinalizeRequest(keyExchangeRequest);
    CameraApiResponse finalizeResponse =
        CameraApiResponse.parseFrom(handler.handleRequest(finalizeRequest.toByteArray()));
    // We have not confirmed the pending request yet.
    checkResponseStatus(finalizeResponse, StatusCode.INVALID_REQUEST);

    handler.confirmLastKeyExchangeRequest();
    finalizeResponse =
        CameraApiResponse.parseFrom(handler.handleRequest(finalizeRequest.toByteArray()));
    checkResponseStatus(finalizeResponse, StatusCode.OK);
  }

  private KeyExchangeRequest createKeyExchangeRequest() {
    byte[] phoneSalt = CryptoUtilities.generateRandom(32);
    return KeyExchangeRequest.newBuilder()
        .setPublicKey(
            ByteString.copyFrom(
                CryptoUtilities.convertECDHPublicKeyToBytes(phoneKeyPair.getPublic())))
        .setSalt(ByteString.copyFrom(phoneSalt))
        .build();
  }

  private CameraApiRequest createCameraInitiateRequest(KeyExchangeRequest keyExchangeRequest) {
    RequestHeader requestHeader = RequestHeader.newBuilder().setRequestId(19).build();
    return CameraApiRequest.newBuilder()
        .setType(CameraApiRequest.RequestType.KEY_EXCHANGE_INITIATE)
        .setKeyExchangeRequest(keyExchangeRequest)
        .setHeader(requestHeader)
        .build();
  }

  private CameraApiRequest createCameraFinalizeRequest(KeyExchangeRequest keyExchangeRequest) {
    RequestHeader requestHeader = RequestHeader.newBuilder().setRequestId(19).build();
    return CameraApiRequest.newBuilder()
        .setType(CameraApiRequest.RequestType.KEY_EXCHANGE_FINALIZE)
        .setKeyExchangeRequest(keyExchangeRequest)
        .setHeader(requestHeader)
        .build();
  }

  private void checkResponseStatus(
      CameraApiResponse response, CameraApiResponse.ResponseStatus.StatusCode status) {
    assertThat(response.getResponseStatus().getStatusCode()).isEqualTo(status);
  }
}
