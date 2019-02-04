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

package com.google.vr180.communication.bluetooth.gatt;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import com.google.vr180.common.communication.BluetoothConstants;
import com.google.vr180.communication.bluetooth.BluetoothSocketService;
import java.util.UUID;

/** Configuration of a GATT service. */
public class BluetoothServiceConfig {
  private UUID serviceUuid;
  private BluetoothGattCharacteristic requestCharacteristic;
  private BluetoothGattCharacteristic responseCharacteristic;
  private BluetoothGattCharacteristic statusChangeCharacteristic;
  private BluetoothSocketService.ConnectionHandler requestHandler;

  /** Sets the Uuid of the GATT service. */
  public BluetoothServiceConfig setServiceUuid(UUID serviceUuid) {
    this.serviceUuid = serviceUuid;
    return this;
  }

  /** Gets the Uuid of the GATT service. */
  public UUID getServiceUuid() {
    return this.serviceUuid;
  }

  /** Sets the handler for requests. This is called when a complete request is received. */
  public BluetoothServiceConfig setRequestHandler(
      BluetoothSocketService.ConnectionHandler requestHandler) {
    this.requestHandler = requestHandler;
    return this;
  }

  /** Gets the handler for requests. */
  public BluetoothSocketService.ConnectionHandler getRequestHandler() {
    return this.requestHandler;
  }

  /** Sets the Uuid of the characteristic for sending responses. */
  public BluetoothServiceConfig setResponseCharacteristic(UUID characteristicUuid) {
    this.responseCharacteristic =
        new BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY
                | BluetoothGattCharacteristic.PROPERTY_READ
                | BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ);
    this.responseCharacteristic.addDescriptor(
        new BluetoothGattDescriptor(
            BluetoothConstants.CLIENT_CHARACTERISTIC_CONFIGURATION,
            BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
    return this;
  }

  /** Sets the Uuid of the characteristic for receiving requests. */
  public BluetoothServiceConfig setRequestCharacteristic(UUID characteristicUuid) {
    this.requestCharacteristic =
        new BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY
                | BluetoothGattCharacteristic.PROPERTY_READ
                | BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ
                | BluetoothGattCharacteristic.PERMISSION_WRITE);
    this.requestCharacteristic.addDescriptor(
        new BluetoothGattDescriptor(
            BluetoothConstants.CLIENT_CHARACTERISTIC_CONFIGURATION,
            BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
    return this;
  }

  /** Sets the Uuid of the characteristic for sending status change notifications. */
  public BluetoothServiceConfig setStatusChangeCharacteristic(UUID characteristicUuid) {
    this.statusChangeCharacteristic =
        new BluetoothGattCharacteristic(
            characteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY
                | BluetoothGattCharacteristic.PROPERTY_READ
                | BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ
                | BluetoothGattCharacteristic.PERMISSION_WRITE);
    this.statusChangeCharacteristic.addDescriptor(
        new BluetoothGattDescriptor(BluetoothConstants.CLIENT_CHARACTERISTIC_CONFIGURATION,
            BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
    return this;
  }

  /** Gets the characteristic for receiving requests. */
  public BluetoothGattCharacteristic getRequestCharacteristic() {
    return this.requestCharacteristic;
  }

  /** Gets the characteristic for sending responses. */
  public BluetoothGattCharacteristic getResponseCharacteristic() {
    return this.responseCharacteristic;
  }

  /** Gets the characteristic for sending status change notifications. */
  public BluetoothGattCharacteristic getStatusChangeCharacteristic() {
    return this.statusChangeCharacteristic;
  }

  /** Gets the BluetoothGattService based on the service configuration. */
  public BluetoothGattService getBluetoothGattService() {
    BluetoothGattService gattService =
        new BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);
    gattService.addCharacteristic(requestCharacteristic);
    gattService.addCharacteristic(responseCharacteristic);
    if (statusChangeCharacteristic != null) {
      gattService.addCharacteristic(statusChangeCharacteristic);
    }
    return gattService;
  }
}
