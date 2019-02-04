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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import com.google.common.base.Preconditions;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import com.google.vr180.common.communication.BluetoothConstants;
import com.google.vr180.common.logging.Log;
import com.google.vr180.communication.bluetooth.gatt.BluetoothGattServerHelper;
import com.google.vr180.communication.bluetooth.gatt.BluetoothServiceConfig;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bluetooth socket service.
 * It represents a Bluetooth GATT service which handles requests from remote devices.
 *
 * <p>Listens for bluetooth connections and calls the callback when a client connects.
 */
public class BluetoothSocketService implements StatusNotifier {
  private static final String TAG = BluetoothSocketService.class.getSimpleName();

  /** Service handler for incoming requests on Bluetooth LE GATT. */
  public interface ConnectionHandler {
    /**
     * Listener called when when a new request arrives.
     * requestData the request data.
     */
    byte[] handleRequest(byte[] requestData);
  }

  private final int manufacturerId;
  private final byte[] manufacturerSpecificData;
  private final BluetoothGattServerHelper bluetoothGattServerHelper;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean enabled = new AtomicBoolean(false);
  private final BluetoothServiceConfig serviceConfig;
  private BluetoothGattService gattService;
  private BluetoothLeAdvertiserHelper advertiserHelper;

  /**
   * Constructs a service that serves requests over Bluetooth LE GATT.
   *
   * @param bluetoothGattServerHelper The BluetoothGattServerHelper representing the gatt server
   * @param serviceUuid The UUID for the GATT service to advertise.
   * @param manufacturerSpecificData Additional data to include in the advertising record.
   * @param connectionHandler Handler for client requests.
   */
  public BluetoothSocketService(
      BluetoothGattServerHelper bluetoothGattServerHelper,
      UUID serviceUuid,
      int manufacturerId,
      byte[] manufacturerSpecificData,
      ConnectionHandler connectionHandler) {
    Preconditions.checkNotNull(connectionHandler);
    this.manufacturerId = manufacturerId;
    this.manufacturerSpecificData = manufacturerSpecificData;
    this.serviceConfig =
        new BluetoothServiceConfig()
            .setServiceUuid(serviceUuid)
            .setResponseCharacteristic(
                BluetoothConstants.CAMERA_API_RESPONSE_CHARACTERISTIC_UUID)
            .setRequestCharacteristic(
                BluetoothConstants.CAMERA_API_REQUEST_CHARACTERISTIC_UUID)
            .setStatusChangeCharacteristic(
                BluetoothConstants.CAMERA_API_STATUS_CHARACTERISTIC_UUID)
            .setRequestHandler(connectionHandler);
    this.bluetoothGattServerHelper = bluetoothGattServerHelper;
  }

  public BluetoothServiceConfig getServiceConfig() {
    return serviceConfig;
  }

  /**
   * Add this Bluetooth service to the GATT server.
   *
   * The service will NOT advertise or handle incoming requests until enable() is called.
   *
   * Note that this should be called before any connection is established. For many Android devices,
   * the service changed notification is not handled on the client side, and therefore if a new
   * service is added when the connection is already established, the service might not be
   * discoverable on the client side.
   */
  public void start() {
    if (started.compareAndSet(false, true)) {
      try {
        bluetoothGattServerHelper.addService(this);
        BluetoothLeAdvertiser bluetoothLeAdvertiser =
            BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        if (bluetoothLeAdvertiser == null) {
          Log.w(TAG, "Error getting BluetoothLeAdvertiser");
          return;
        }
        advertiserHelper = new BluetoothLeAdvertiserHelper(bluetoothLeAdvertiser);
      } catch (BluetoothException e) {
        Log.w(TAG, "error in opening Gatt server", e);
      }
    }
  }

  /** Stops advertising for Bluetooth LE connections and close gatt server. */
  public void stop() {
    if (started.compareAndSet(true, false)) {
      disable();
      bluetoothGattServerHelper.removeService(this);
      advertiserHelper = null;
      Log.d(TAG, "GATT server helper close() done");
    }
  }

  /** Start advertising the service and handling incoming requests. */
  public void enable() {
    if (advertiserHelper == null) {
      Log.e(TAG, "Calling enable() when the service is not added to the server");
      return;
    }
    if (enabled.compareAndSet(false, true)) {
      try {
        AdvertiseSettings settings =
            BluetoothLeAdvertiserHelper.defaultSettingsBuilder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .build();
        advertiserHelper.startAdvertising(
            settings, manufacturerId, manufacturerSpecificData, serviceConfig);
      } catch (BluetoothException e) {
        Log.w(TAG, "error in start advertising", e);
        bluetoothGattServerHelper.close();
      }
    }
  }

  /** Stop advertising the service and stop handling incoming requests. */
  public void disable() {
    if (advertiserHelper == null) {
      Log.e(TAG, "Calling disable() when the service is not added to the server");
      return;
    }
    if (enabled.compareAndSet(true, false)) {
      advertiserHelper.stopAdvertising();
    }
  }

  /**
   * @return Whether the service is enabled and should handle incoming requests.
   */
  public boolean isEnabled() {
    return enabled.get();
  }

  public BluetoothGattService getGattService() {
    return gattService;
  }

  public void setGattService(BluetoothGattService gattService) {
    this.gattService = gattService;
  }

  @Override
  public void notifyStatusChanged() {
    bluetoothGattServerHelper.notifyStatusChanged(this);
  }
}
