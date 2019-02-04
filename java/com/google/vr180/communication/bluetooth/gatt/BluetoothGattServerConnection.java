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
import android.os.AsyncTask;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.vr180.common.communication.BluetoothConstants;
import com.google.vr180.common.communication.MessageMarkers;
import com.google.vr180.common.logging.Log;
import com.google.vr180.communication.bluetooth.BluetoothException;
import com.google.vr180.communication.bluetooth.BluetoothGattException;
import com.google.vr180.communication.bluetooth.gatt.BluetoothOperationExecutor.Operation;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.GuardedBy;

/**
 * Connection to a bluetooth LE device over Gatt. This handles callbacks on
 * BluetoothGattServerCallback for the device.
 */
@TargetApi(18)
public class BluetoothGattServerConnection implements Closeable {
  @SuppressWarnings("unused")
  private static final String TAG = BluetoothGattServerConnection.class.getSimpleName();

  /** Status notification value (an empty byte array). */
  private static final byte[] STATUS_NOTIFICATION_VALUE = new byte[0];

  @VisibleForTesting static final long OPERATION_TIMEOUT = TimeUnit.SECONDS.toMillis(1);

  /** BT operation types that can be in flight. */
  public enum OperationType {
    SEND_NOTIFICATION
  }

  private final BluetoothGattServerHelper mBluetoothGattServerHelper;
  private final BluetoothDevice mBluetoothDevice;

  @VisibleForTesting BluetoothOperationExecutor mBluetoothOperationScheduler =
      new BluetoothOperationExecutor(1);

  @GuardedBy("this")
  private volatile int mtuSize = BluetoothConstants.DEFAULT_MTU;
  @GuardedBy("this")
  private final Map<UUID, ByteString> queuedRequestDataMap = new HashMap<>();

  @GuardedBy("this")
  private final Map<UUID, PreparedWriteState> preparedWriteMap = new HashMap<>();

  public BluetoothGattServerConnection(
      BluetoothGattServerHelper bluetoothGattServerHelper,
      BluetoothDevice device) {
    mBluetoothGattServerHelper = bluetoothGattServerHelper;
    mBluetoothDevice = device;
  }

  /** Notifies client of a new camera status. */
  public void notifyStatusChanged(BluetoothServiceConfig serviceConfig) {
    if (serviceConfig.getStatusChangeCharacteristic() == null) {
      Log.w(TAG, "No statusChangeCharacteristic configured.");
      return;
    }

    // Marshal to a background thread to make sure we aren't running in the context of another
    // request/operation.
    AsyncTask.THREAD_POOL_EXECUTOR.execute(
        () -> {
          try {
            Log.d(TAG, "Sending status notification to device " + mBluetoothDevice);
            sendNotification(
                serviceConfig.getStatusChangeCharacteristic(), STATUS_NOTIFICATION_VALUE);
          } catch (Exception e) {
            Log.e(TAG, "Unable to notify " + mBluetoothDevice + " of status change.", e);
          }
        });
  }

  public BluetoothDevice getDevice() {
    return mBluetoothDevice;
  }

  public synchronized int getMtu() {
    return mtuSize;
  }

  public synchronized void setMtu(int mtu) {
    mtuSize = mtu;
  }

  public int getMaxDataPacketSize() {
    // Per BT specs (3.2.9), only MTU - 3 bytes can be used to transmit data
    return getMtu() - BluetoothConstants.MTU_RESERVED_BYTES;
  }

  /**
   * Handle the read characteristic request from remote device
   * Currently read is not supported.
   *
   * @param serviceConfig The service configuration of the corresponding request
   * @param offset The offset of the read
   * @param characteristic The characteristic which received the read request
   * @return (not supported)
   * @throws BluetoothGattException
   */
  public synchronized byte[] readCharacteristic(
      BluetoothServiceConfig serviceConfig, int offset, BluetoothGattCharacteristic characteristic)
      throws BluetoothGattException {
    throw new BluetoothGattException(
        "Read not supported.", BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
  }

  /** Handles a write from the client to a characteristic. */
  public synchronized void writeCharacteristic(
      BluetoothServiceConfig serviceConfig,
      BluetoothGattCharacteristic characteristic, boolean preparedWrite, int offset, byte[] value)
      throws BluetoothGattException {
    Log.d(TAG, String.format(
        "Received %d bytes at offset %d on %s from device %s, prepareWrite=%s.",
        value.length,
        offset,
        BluetoothGattUtils.toString(characteristic),
        mBluetoothDevice,
        preparedWrite));
    if (!serviceConfig.getRequestCharacteristic().getUuid().equals(characteristic.getUuid())) {
      throw new BluetoothGattException(
          String.format("Got write on bad characteristic %s", characteristic),
          BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
    }
    if (!mBluetoothGattServerHelper.connections.containsKey(mBluetoothDevice)) {
      throw new BluetoothGattException(
          String.format("Received characteristic write from unknown device: %s", mBluetoothDevice),
          BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
    }

    if (preparedWrite) {
      prepareWrite(serviceConfig, offset, value);
    } else {
      handleWrite(serviceConfig, ByteString.copyFrom(value));
    }
  }

  /**
   * Executes a prepared write operation.
   *
   * @param execute Whether to execute (true) or cancel (false) the pending writes.
   */
  public synchronized void executeWrite(boolean execute) throws BluetoothGattException {
    try {
      if (execute) {
        // Try to execute all of the pending writes to different services.
        for (PreparedWriteState state : preparedWriteMap.values()) {
          handleWrite(state.getServiceConfig(), state.assembleWrite());
        }
      }
    } finally {
      // Either way, we clear the pending write state.
      preparedWriteMap.clear();
    }
  }

  /**
   * Close the connection with the remote device
   *
   * @throws IOException
   */
  @Override
  public void close() throws IOException {
    try {
      mBluetoothGattServerHelper.closeConnection(mBluetoothDevice);
    } catch (BluetoothException e) {
      throw new IOException("Failed to close connection", e);
    }
  }

  /**
   * Callback function after notification is sent
   *
   * @param status Status of the operation, such as BluetoothGatt.GATT_SUCCESS
   */
  public void onNotificationSent(int status) {
    mBluetoothOperationScheduler.notifyCompletion(
        new Operation<Void>(OperationType.SEND_NOTIFICATION), status);
  }

  /** Pushes response data to the client. */
  private void sendResponse(BluetoothServiceConfig serviceConfig, byte[] responseData) {
    // Put the end of message marker on the response (and escape any other instances of it).
    ByteString bytesRemaining = ByteString.copyFrom(MessageMarkers.encode(responseData));

    try {
      while (bytesRemaining.size() > 0) {
        int packetSize = Math.min(getMaxDataPacketSize(), bytesRemaining.size());
        sendNotification(
            serviceConfig.getResponseCharacteristic(),
            bytesRemaining.substring(0, packetSize).toByteArray());
        bytesRemaining = bytesRemaining.substring(packetSize);
      }
    } catch (BluetoothException e) {
      Log.e(TAG, "Error writing response", e);
    }
  }

  private void sendNotification(BluetoothGattCharacteristic characteristic, byte[] data)
      throws BluetoothException {
    final boolean getConfirmation = false;
    mBluetoothOperationScheduler.execute(
        new Operation<Void>(OperationType.SEND_NOTIFICATION) {
          @Override
          public void run() throws BluetoothException {
            mBluetoothGattServerHelper.sendNotification(
                mBluetoothDevice, characteristic, data, getConfirmation);
          }
        },
        OPERATION_TIMEOUT);
  }

  /** Handles a prepared write request (which just adds data to the pending state). */
  @GuardedBy("this")
  private void prepareWrite(BluetoothServiceConfig serviceConfig, int offset, byte[] value) {
    if (!preparedWriteMap.containsKey(serviceConfig.getServiceUuid())) {
      preparedWriteMap.put(serviceConfig.getServiceUuid(), new PreparedWriteState(serviceConfig));
    }

    PreparedWriteState state = preparedWriteMap.get(serviceConfig.getServiceUuid());
    state.prepareWrite(offset, ByteString.copyFrom(value));
  }

  /**
   * Handles data written to the request characteristic, whether by a single write request or
   * an executed series of pending requests.
   */
  @GuardedBy("this")
  private void handleWrite(BluetoothServiceConfig serviceConfig, ByteString value) {
    // Queue the written data.
    ByteString queuedRequestData = queuedRequestDataMap.get(serviceConfig.getServiceUuid());
    if (queuedRequestData == null) {
      queuedRequestData = ByteString.EMPTY;
    }
    queuedRequestData = queuedRequestData.concat(value);

    // Check if we have the full request.
    byte[] requestData = getQueuedRequest(queuedRequestData);
    if (requestData != null) {
      queuedRequestDataMap.put(serviceConfig.getServiceUuid(), ByteString.EMPTY);
      byte[] result = serviceConfig.getRequestHandler().handleRequest(requestData);
      // Notify the client of the response.
      AsyncTask.execute(() -> sendResponse(serviceConfig, result));
    } else {
      queuedRequestDataMap.put(serviceConfig.getServiceUuid(), queuedRequestData);
    }
  }

  /**
   * Returns the request data if it is complete (and removes it from the queue). If the request is
   * incomplete, returns null.
   */
  private static byte[] getQueuedRequest(ByteString queuedRequestData) {
    if (!MessageMarkers.messageComplete(queuedRequestData)) {
      return null;
    }

    byte[] request = MessageMarkers.decode(queuedRequestData.toByteArray());
    return request;
  }
}
