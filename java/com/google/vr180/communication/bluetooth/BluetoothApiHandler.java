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

import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ResponseStatus;
import com.google.vr180.api.CameraApiHandler;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.common.crypto.CryptoUtilities;
import com.google.vr180.common.crypto.CryptoUtilities.CryptoException;
import com.google.vr180.common.logging.Log;

/**
 * The API handler for the Bluetooth LE GATT api. Wraps a CameraApiHandler instance and implements
 * the BluetoothSocketService.ConnectionHandler interface.
 */
public class BluetoothApiHandler implements BluetoothSocketService.ConnectionHandler {
  private static final String TAG = "BluetoothApiHandler";

  /**
   * An empty response to return if we have some error that doesn't allow us to construct any
   * response.
   */
  private static final byte[] ERROR_RESPONSE = new byte[] {};

  private final CameraSettings settings;
  private final CameraApiHandler apiHandler;
  private final ExtensionRegistryLite extensionRegistry;

  public BluetoothApiHandler(CameraSettings cameraSettings, CameraApiHandler apiHandler) {
    this(cameraSettings, apiHandler, ExtensionRegistryLite.newInstance());
  }

  public BluetoothApiHandler(
      CameraSettings cameraSettings,
      CameraApiHandler apiHandler,
      ExtensionRegistryLite extensionRegistry) {
    this.settings = cameraSettings;
    this.apiHandler = apiHandler;
    this.extensionRegistry = extensionRegistry;
  }

  @Override
  public byte[] handleRequest(byte[] requestData) {
    byte[] sharedKey = settings.getSharedKey();
    if (sharedKey == null) {
      Log.w(TAG, "Camera not paired");
      return ERROR_RESPONSE;
    }

    byte[] requestBytes;
    try {
      requestBytes = CryptoUtilities.decrypt(requestData, sharedKey);
    } catch (CryptoException e) {
      Log.e(TAG, "Failed to decrypt request");
      return ERROR_RESPONSE;
    }

    CameraApiRequest request;
    CameraApiResponse response;
    try {
      request = CameraApiRequest.parseFrom(requestBytes, extensionRegistry);
      if (isRequestAllowed(request)) {
        response = apiHandler.handleRequest(request);
      } else {
        Log.w(TAG, "Camera request does not have enough privileges");
        return ERROR_RESPONSE;
      }
    } catch (InvalidProtocolBufferException e) {
      response = CameraApiHandler.createResponse(ResponseStatus.StatusCode.INVALID_REQUEST).build();
    } catch (Exception e) {
      Log.e(TAG, "Error handling request!", e);
      response = CameraApiHandler.createResponse(ResponseStatus.StatusCode.ERROR).build();
    }

    try {
      return CryptoUtilities.encrypt(response.toByteArray(), sharedKey);
    } catch (CryptoException e) {
      Log.e(TAG, "Error encrypting response", e);
      return ERROR_RESPONSE;
    }
  }

  boolean isRequestAllowed(CameraApiRequest request) {
    if (!settings.getSharedKeyIsPending()) {
      // All requests are allowed if the request is coming from a device fully paired.
      return true;
    }
    return false;
  }
}
