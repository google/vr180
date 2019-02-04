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

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import com.google.common.annotations.VisibleForTesting;
import com.google.vr180.common.logging.Log;
import com.google.vr180.communication.bluetooth.BluetoothException;
import com.google.vr180.communication.bluetooth.BluetoothGattException;
import com.google.vr180.communication.bluetooth.BluetoothSocketService;
import com.google.vr180.communication.bluetooth.gatt.BluetoothOperationExecutor.Operation;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/** Helper for simplifying operations on {@link BluetoothGattServer}. */
@TargetApi(18)
public class BluetoothGattServerHelper {
  private static final String TAG = "BluetoothGattServerHelper";

  @VisibleForTesting static final long OPERATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(1);
  private static final int MAX_PARALLEL_OPERATIONS = 5;

  /** BT operation types that can be in flight. */
  public enum OperationType {
    ADD_SERVICE,
    CLOSE_CONNECTION,
    START_ADVERTISING
  }

  /** Server handler to notify of server status changes. */
  public interface ConnectionStatusListener {
    /**
     * Listener called when the number of connections to the server changes.
     *
     * @param numConnections the number of current connections.
     * @param device The remote device which causes the change
     * @param changeState
     * BluetoothGattServer.STATE_CONNECTED or BluetoothGattServer.STATE_DISCONNECTED indicating
     * whether the callback is triggered by a device connected or disconnected
     */
    void onConnectionsChanged(
        int numConnections,
        BluetoothDevice device,
        int changeState
    );
  }

  private final Object operationLock = new Object();
  private final BluetoothGattServerCallback gattServerCallback = new GattServerCallback();
  private BluetoothOperationExecutor bluetoothOperationScheduler =
      new BluetoothOperationExecutor(MAX_PARALLEL_OPERATIONS);

  private final Context context;
  private final ConnectionStatusListener statusListener;

  final ConcurrentMap<BluetoothDevice, BluetoothGattServerConnection> connections =
      new ConcurrentHashMap<BluetoothDevice, BluetoothGattServerConnection>();
  final ConcurrentMap<UUID, BluetoothSocketService> services = new ConcurrentHashMap<>();

  private BluetoothGattServer bluetoothGattServer;

  public BluetoothGattServerHelper(Context context, ConnectionStatusListener statusListener) {
    this.context = context;
    this.statusListener = statusListener;
  }

  public void open() {
    synchronized (operationLock) {
      BluetoothManager bluetoothManager =
          (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
      if (bluetoothManager == null) {
        Log.e(TAG, "BluetoothManager is null!");
        return;
      }
      bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback);
      if (bluetoothGattServer == null) {
        Log.e(TAG, "BluetoothGattServer is null!");
      }
    }
  }

  /** Notifies all clients of a new camera status. */
  public void notifyStatusChanged(BluetoothSocketService service) {
    for (BluetoothGattServerConnection connection : connections.values()) {
      connection.notifyStatusChanged(service.getServiceConfig());
    }
  }

  public void addService(BluetoothSocketService service) throws BluetoothException {
    services.put(service.getServiceConfig().getServiceUuid(), service);
    openService(service);
  }

  public void removeService(BluetoothSocketService service) {
    synchronized (operationLock) {
      services.remove(service.getServiceConfig().getServiceUuid());
      if (bluetoothGattServer != null) {
        bluetoothGattServer.removeService(service.getGattService());
      }
    }
  }

  public void close() {
    synchronized (operationLock) {
      if (bluetoothGattServer != null) {
        bluetoothGattServer.close();
        bluetoothGattServer = null;
      }
      services.clear();
      connections.clear();
    }
  }

  @VisibleForTesting
  void sendNotification(
      BluetoothDevice device,
      BluetoothGattCharacteristic characteristic,
      byte[] data,
      boolean confirm)
      throws BluetoothException {
    Log.d(
        TAG,
        String.format(
            "Sending a %s of %d bytes on characteristics %s on device %s.",
            confirm ? "indication" : "notification",
            data.length,
            characteristic.getUuid(),
            device));
    if (getConnectionByDevice(device) == null) {
      Log.e(
          TAG,
          String.format("Ignoring request to send notification on unknown device: %s", device));
      return;
    }
    synchronized (operationLock) {
      BluetoothGattCharacteristic clonedCharacteristic = BluetoothGattUtils.clone(characteristic);
      clonedCharacteristic.setValue(data);
      try {
        getBluetoothGattServer().notifyCharacteristicChanged(device, clonedCharacteristic, confirm);
      } catch (Exception e) {
        throw new BluetoothException(e.getMessage(), e);
      }
    }
  }

  @VisibleForTesting
  void closeConnection(final BluetoothDevice bluetoothDevice) throws BluetoothException {
    BluetoothManager bluetoothManager =
        (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    if (bluetoothManager == null) {
      Log.e(TAG, "BluetoothManager is null!");
      return;
    }
    int connectionSate =
        bluetoothManager.getConnectionState(bluetoothDevice, BluetoothProfile.GATT);
    if (connectionSate != BluetoothGatt.STATE_CONNECTED) {
      Log.i(TAG, "Connection already closed.");
      return;
    }
    bluetoothOperationScheduler.execute(
        new Operation<Void>(OperationType.CLOSE_CONNECTION) {
          @Override
          public void run() throws BluetoothException {
            Log.i(TAG, String.format("Cancelling connection to BLE device:%s", bluetoothDevice));
            getBluetoothGattServer().cancelConnection(bluetoothDevice);
          }
        },
        OPERATION_TIMEOUT_MILLIS);
  }

  private void openService(BluetoothSocketService service) throws BluetoothException {
    synchronized (operationLock) {
      BluetoothGattService gattService = service.getServiceConfig().getBluetoothGattService();
      service.setGattService(gattService);
      try {
        bluetoothOperationScheduler.execute(
            new Operation<Void>(OperationType.ADD_SERVICE, gattService) {
              @Override
              public void run() throws BluetoothException {
                boolean success =
                    bluetoothGattServer != null && bluetoothGattServer.addService(gattService);
                if (!success) {
                  throw new BluetoothException(
                      String.format("Fails on adding service:%s", gattService));
                } else {
                  Log.i(TAG, String.format("Added service:%s", gattService));
                }
              }
            },
            OPERATION_TIMEOUT_MILLIS);
      } catch (BluetoothException e) {
        close();
        throw e;
      }
    }
  }

  private BluetoothGattServerConnection getConnectionByDevice(BluetoothDevice device)
      throws BluetoothGattException {
    BluetoothGattServerConnection bluetoothLeConnection = connections.get(device);
    if (bluetoothLeConnection == null) {
      throw new BluetoothGattException(
          String.format("Received operation on an unknown device: %s", device),
          BluetoothGatt.GATT_FAILURE);
    }
    return bluetoothLeConnection;
  }

  private BluetoothGattServer getBluetoothGattServer() {
    synchronized (operationLock) {
      if (bluetoothGattServer == null) {
        throw new IllegalStateException("Bluetooth GATT server is not open.");
      } else {
        return bluetoothGattServer;
      }
    }
  }

  private class GattServerCallback extends BluetoothGattServerCallback {
    private static final String TAG = "GattServerCallback";

    @Override
    public void onServiceAdded(int status, BluetoothGattService service) {
      Log.e(TAG, "onServiceAdded");
      bluetoothOperationScheduler.notifyCompletion(
          new Operation<Void>(OperationType.ADD_SERVICE, service), status);
    }

    @Override
    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
      Log.i(
          TAG,
          String.format(
              "onConnectionStateChange: device:%s status:%s state:%d",
              device,
              BluetoothGattUtils.getMessageForStatusCode(status),
              newState));
      switch (newState) {
        case BluetoothGattServer.STATE_CONNECTED:
          if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(
                TAG,
                String.format(
                    "Connection to %s failed: %s",
                    device, BluetoothGattUtils.getMessageForStatusCode(status)));
            return;
          }
          Log.i(TAG, String.format("Connected to device %s.", device));
          if (connections.containsKey(device)) {
            Log.w(
                TAG,
                String.format(
                    "A connection is already open with device %s. Keeping existing one.", device));
            return;
          }
          connections.put(
              device,
              new BluetoothGattServerConnection(
                  BluetoothGattServerHelper.this, device));
          if (statusListener != null) {
            statusListener.onConnectionsChanged(
                connections.size(),
                device,
                BluetoothGattServer.STATE_CONNECTED
            );
          }
          break;
        case BluetoothGattServer.STATE_DISCONNECTED:
          Log.d(
              TAG,
              String.format(
                  "Disconnection from %s for: %s",
                  device, BluetoothGattUtils.getMessageForStatusCode(status)));

          connections.remove(device);
          bluetoothOperationScheduler.notifyCompletion(
              new Operation<Void>(OperationType.CLOSE_CONNECTION), status);
          if (statusListener != null) {
            statusListener.onConnectionsChanged(
                connections.size(),
                device,
                BluetoothGattServer.STATE_DISCONNECTED
            );
          }
          break;
        default:
          Log.e(TAG, String.format("Unexpected connection state: %d", newState));
          return;
      }
    }

    @Override
    public void onCharacteristicReadRequest(
        BluetoothDevice device,
        int requestId,
        int offset,
        BluetoothGattCharacteristic characteristic) {
      BluetoothSocketService service = services.get(characteristic.getService().getUuid());
      if (service == null) {
        Log.e(TAG, "Service not found " + characteristic.getService().getUuid());
        return;
      }
      if (!service.isEnabled()) {
        Log.e(TAG, "Service not enabled " + characteristic.getService().getUuid());
        return;
      }
      try {
        byte[] value = getConnectionByDevice(device).readCharacteristic(
            service.getServiceConfig(), offset, characteristic);
        getBluetoothGattServer().sendResponse(
            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
      } catch (BluetoothGattException e) {
        Log.e(
            TAG,
            String.format(
                "Could not read  %s on device %s at offset %d",
                BluetoothGattUtils.toString(characteristic), device, offset),
            e);
        getBluetoothGattServer()
            .sendResponse(device, requestId, e.getGattErrorCode(), offset, null);
      }
    }

    @Override
    public void onCharacteristicWriteRequest(
        BluetoothDevice device,
        int requestId,
        BluetoothGattCharacteristic characteristic,
        boolean preparedWrite,
        boolean responseNeeded,
        int offset,
        byte[] value) {
      BluetoothSocketService service = services.get(characteristic.getService().getUuid());
      if (service == null) {
        Log.e(TAG, "Service not found " + characteristic.getService().getUuid());
        return;
      }
      if (!service.isEnabled()) {
        Log.e(TAG, "Service not enabled " + characteristic.getService().getUuid());
        return;
      }
      try {
        getConnectionByDevice(device).writeCharacteristic(
            service.getServiceConfig(), characteristic, preparedWrite, offset, value);
        if (responseNeeded) {
          getBluetoothGattServer().sendResponse(
              device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }
      } catch (BluetoothGattException e) {
        Log.e(
            TAG,
            String.format(
                "Could not write %s on device %s at offset %d",
                BluetoothGattUtils.toString(characteristic), device, offset),
            e);
        getBluetoothGattServer()
            .sendResponse(device, requestId, e.getGattErrorCode(), offset, null);
      }
    }

    @Override
    public void onNotificationSent(BluetoothDevice device, int status) {
      Log.d(
          TAG,
          String.format(
              "Received onNotificationSent for device %s with status %s", device, status));
      try {
        getConnectionByDevice(device).onNotificationSent(status);
      } catch (BluetoothGattException e) {
        Log.e(TAG, String.format("An error occurred when receiving onNotificationSent"), e);
      }
    }

    @Override
    public void onMtuChanged(BluetoothDevice device, int mtu) {
      Log.d(TAG, String.format("Received onMtuChanged for device %s with mtu %s", device, mtu));
      try {
        getConnectionByDevice(device).setMtu(mtu);
      } catch (BluetoothGattException e) {
        Log.e(TAG, String.format("An error occurred when receiving onMtuChanged."), e);
      }
    }

    @Override
    public void onDescriptorWriteRequest(
        BluetoothDevice device,
        int requestId,
        BluetoothGattDescriptor descriptor,
        boolean preparedWrite,
        boolean responseNeeded,
        int offset,
        byte[] value) {
      getBluetoothGattServer().sendResponse(
          device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
    }

    @Override
    public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
      Log.d(
          TAG,
          String.format("Received onExecuteWrite for device %s with execute: %s", device, execute));
      try {
        getConnectionByDevice(device).executeWrite(execute);
        getBluetoothGattServer()
            .sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
      } catch (BluetoothGattException e) {
        Log.e(TAG, String.format("Could not execute pending writes on device %s", device), e);
        getBluetoothGattServer().sendResponse(device, requestId, e.getGattErrorCode(), 0, null);
      }
    }
  }
}
