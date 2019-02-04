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

import android.annotation.TargetApi;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.Build;
import android.os.ParcelUuid;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.communication.bluetooth.gatt.BluetoothOperationExecutor;
import com.google.vr180.communication.bluetooth.gatt.BluetoothOperationExecutor.Operation;
import com.google.vr180.communication.bluetooth.gatt.BluetoothServiceConfig;
import java.util.concurrent.TimeUnit;

/** Helper for {@link BluetoothLeAdvertiser}.*/
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BluetoothLeAdvertiserHelper {
  private static final String TAG = "BluetoothLeAdvertiserHelper";

  @VisibleForTesting static final long OPERATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);
  @VisibleForTesting BluetoothOperationExecutor mBluetoothOperationScheduler =
      new BluetoothOperationExecutor(1);
  @VisibleForTesting final AdvertiseCallback mAdvertiseCallback = new InternalAdvertiseCallback();
  private final BluetoothLeAdvertiser mBluetoothLeAdvertiser;

  /** BT operation types that can be in flight. */
  public enum OperationType {
    START_ADVERTISING
  }

  public BluetoothLeAdvertiserHelper(BluetoothLeAdvertiser bluetoothLeAdvertiser) {
    mBluetoothLeAdvertiser = Preconditions.checkNotNull(bluetoothLeAdvertiser);
  }

  public static AdvertiseSettings.Builder defaultSettingsBuilder() {
    return new AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
        .setConnectable(true)
        .setTimeout(0 /* disabled */)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
  }

  /**
   * Start advertising.
   */
  public void startAdvertising(
      AdvertiseSettings advertiseSettings,
      int manufacturerId, byte[] manufacturerSpecificData,
      BluetoothServiceConfig serviceConfig) throws BluetoothException {
    if (serviceConfig == null) {
      throw new BluetoothException("Server is not open!");
    }
    AdvertiseData.Builder advertiseDataBuilder =
        new AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(manufacturerId, manufacturerSpecificData)
            .addServiceUuid(new ParcelUuid(serviceConfig.getServiceUuid()));
    startAdvertising(advertiseSettings, advertiseDataBuilder.build());
  }

  public void startAdvertising(final AdvertiseSettings advertiseSettings,
      final AdvertiseData advertiseData) throws BluetoothException {
    Preconditions.checkNotNull(advertiseSettings);
    Preconditions.checkNotNull(advertiseData);
    Log.i(TAG, String.format("Schedule advertising start:%s", advertiseData));
    mBluetoothOperationScheduler.execute(new Operation<Void>(OperationType.START_ADVERTISING) {
      @Override
      public void run() throws BluetoothException {
        Log.i(TAG, String.format("Start advertising:%s", advertiseData));
        mBluetoothLeAdvertiser.startAdvertising(
            advertiseSettings,
            advertiseData,
            mAdvertiseCallback);
      }
    }, OPERATION_TIMEOUT_MILLIS);
  }

  public void stopAdvertising() {
    Log.i(TAG, "Stop advertising.");
    mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
  }

  private class InternalAdvertiseCallback extends AdvertiseCallback {
    @Override
    public void onStartFailure(int errorCode) {
      String errorMessage;
      switch (errorCode) {
        case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
          errorMessage = "ALREADY_STARTED";
          break;
        case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
          errorMessage = "DATA_TOO_LARGE";
          break;
        case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
          errorMessage = "FEATURE_UNSUPPORTED";
          break;
        case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
          errorMessage = "INTERNAL_ERROR";
          break;
        case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
          errorMessage = "TOO_MANY_ADVERTISERS";
          break;
        default:
          errorMessage = "Unknown error code: " + errorCode;
          break;
      }
      mBluetoothOperationScheduler.notifyFailure(
          new Operation<Void>(OperationType.START_ADVERTISING),
          new BluetoothException("Starting advertisement failed: " + errorMessage));
    }

    @Override
    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
      mBluetoothOperationScheduler.notifySuccess(
          new Operation<Void>(OperationType.START_ADVERTISING));
    }
  }
}
