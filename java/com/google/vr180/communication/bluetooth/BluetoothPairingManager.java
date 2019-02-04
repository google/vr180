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

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.PairingCompleteListener;
import com.google.vr180.api.camerainterfaces.PairingManager;
import com.google.vr180.api.camerainterfaces.PairingStatusListener;
import com.google.vr180.api.camerainterfaces.PairingStatusListener.PairingStatus;
import com.google.vr180.common.communication.BluetoothConstants;
import com.google.vr180.communication.bluetooth.gatt.BluetoothGattServerHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * The BluetoothPairingManager helps host and advertise the pairing service. When the user long
 * presses the camera button, it should trigger this to listen for KEY_EXCHANGE_INITIATE and
 * KEY_EXCHANGE_FINALIZE requests.
 */
public class BluetoothPairingManager implements PairingManager {

  public static final String TAG = "BluetoothPairingManager";

  /** How long we advertise on the pairing UUID after a long press. */
  private static final int PAIRING_ADVERTISE_TIME_MS = 60000;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final PairingApiHandler pairingHandler;
  private final List<PairingStatusListener> pairingStatusListeners = new ArrayList<>();
  private final List<PairingCompleteListener> pairingCompleteListeners = new ArrayList<>();
  private final BluetoothSocketService pairingSocketServer;
  private Runnable advertisingStopRunnable;

  public BluetoothPairingManager(
      BluetoothGattServerHelper bluetoothGattServerHelper,
      CameraSettings settings,
      int bluetoothManufactorerId) {
    byte[] manufactererSpecificData = new byte[0];
    pairingHandler =
        new PairingApiHandler(
            settings,
            new PairingStatusListener() {
              @Override
              public void onPairingStatusChanged(PairingStatus status) {
                if (status == PairingStatus.PAIRED && isPairingActive()) {
                  stopPairing();
                  publishPairingComplete();
                }
                publishPairingStatus(status);
              }
            });
    pairingSocketServer =
        new BluetoothSocketService(
            bluetoothGattServerHelper,
            BluetoothConstants.CAMERA_PAIRING_UUID,
            bluetoothManufactorerId,
            manufactererSpecificData,
            pairingHandler);
  }

  public synchronized void open() {
    handler.removeCallbacks(advertisingStopRunnable);
    AsyncTask.execute(() -> pairingSocketServer.start());
  }

  /**
   * Adds a new pairing listener to the list of pairing status listeners to be notified when there
   * is a pairing change.
   *
   * @param pairingListener The new pairing listener to add to the list of listeners.
   */
  @Override
  public synchronized void addPairingStatusListener(PairingStatusListener pairingListener) {
    pairingStatusListeners.add(pairingListener);
  }

  /**
   * Adds a new pairing complete listener to the list of listeners to be notified when pairing has
   * completed.
   *
   * @param pairingListener The new pairing listener to add to the list of listeners.
   */
  @Override
  public synchronized void addPairingCompleteListener(PairingCompleteListener pairingListener) {
    pairingCompleteListeners.add(pairingListener);
  }

  /** Start advertising the pairing service UUID for a period of time. */
  @Override
  public synchronized void startPairing() {
    if (advertisingStopRunnable != null) {
      // If we are already advertising, just push out the timeout to stop.
      handler.removeCallbacks(advertisingStopRunnable);
      handler.postDelayed(advertisingStopRunnable, PAIRING_ADVERTISE_TIME_MS);
      return;
    }

    publishPairingStatus(PairingStatus.ADVERTISING);
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> pairingSocketServer.enable());
    // Set a timeout to stop advertising.
    advertisingStopRunnable =
        () -> {
          stopPairing();
        };
    handler.postDelayed(advertisingStopRunnable, PAIRING_ADVERTISE_TIME_MS);
  }

  /**
   * Confirms the current KEY_EXCHANGE_INITIATE request. This confirmation will make the pairing
   * handler return success for the next KEY_EXCHANGE_FINALIZE request.
   */
  @Override
  public synchronized void confirmPairing() {
    pairingHandler.confirmLastKeyExchangeRequest();
  }

  @Override
  public synchronized void stopPairing() {
    handler.removeCallbacks(advertisingStopRunnable);
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> pairingSocketServer.disable());
    publishPairingStatus(PairingStatus.NOT_ADVERTISING);
    advertisingStopRunnable = null;
  }

  /** Completely close this BluetoothPairingManager. It cannot be used after close(). */
  public void close() {
      pairingSocketServer.stop();
  }

  /** Returns whether the camera is currently in pairing mode. */
  @Override
  public boolean isPairingActive() {
    return pairingSocketServer.isEnabled();
  }

  private void publishPairingStatus(PairingStatus status) {
    for (PairingStatusListener listener : pairingStatusListeners) {
      listener.onPairingStatusChanged(status);
    }
  }

  private void publishPairingComplete() {
    for (PairingCompleteListener listener : pairingCompleteListeners) {
      listener.onPairingComplete();
    }
  }
}
