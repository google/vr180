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

package com.google.vr180.api.internal;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import com.google.vr180.CameraInternalApi.CameraInternalStatus;
import com.google.vr180.CameraInternalApi.CameraState;
import com.google.vr180.api.camerainterfaces.PairingStatusListener.PairingStatus;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import java.util.HashSet;
import java.util.Set;

/**
 * A class managing camera internal status.
 */
public class CameraInternalStatusManager {

  private final BehaviorSubject<CameraInternalStatus> cameraInternalStatusBehaviorSubject =
      BehaviorSubject.create();

  private final BehaviorSubject<CameraInternalStatus.PairingStatus> pairingStatusObservable =
      BehaviorSubject.createDefault(CameraInternalStatus.PairingStatus.DEFAULT_NOT_ADVERTISING);
  private final BehaviorSubject<Set<String>> connectedDevicesObservable =
      BehaviorSubject.createDefault(new HashSet<>());
  private final BehaviorSubject<CameraState> cameraStateBehaviorSubject =
      BehaviorSubject.createDefault(CameraState.INACTIVE);

  public CameraInternalStatusManager() {
    pairingStatusObservable
        .distinctUntilChanged()
        .map(pairingStatus -> computeCameraInternalStatus())
        .subscribe(cameraInternalStatusBehaviorSubject);
    connectedDevicesObservable
        .distinctUntilChanged()
        .map(devices -> computeCameraInternalStatus())
        .subscribe(cameraInternalStatusBehaviorSubject);
    cameraStateBehaviorSubject
        .distinctUntilChanged()
        .map(cameraState -> computeCameraInternalStatus())
        .subscribe(cameraInternalStatusBehaviorSubject);
  }

  public Observable<CameraInternalStatus> getCameraInternalStatusObservable() {
    return cameraInternalStatusBehaviorSubject;
  }

  public CameraInternalStatus getCameraInternalStatus() {
    return cameraInternalStatusBehaviorSubject.getValue();
  }

  public void onCameraStateChanged(CameraState cameraState) {
    cameraStateBehaviorSubject.onNext(cameraState);
  }

  public void onPairingStatusChanged(PairingStatus pairingStatus) {
    pairingStatusObservable.onNext(mapPairingStatus(pairingStatus));
  }

  public void onConnectionsChanged(
      int numConnections,
      BluetoothDevice device,
      int changeState
  ) {
    Set<String> connectedDevices = new HashSet<>(connectedDevicesObservable.getValue());
    if (changeState == BluetoothGattServer.STATE_CONNECTED) {
      connectedDevices.add(device.getAddress());
    } else if (changeState == BluetoothGattServer.STATE_DISCONNECTED) {
      connectedDevices.remove(device.getAddress());
    }
    connectedDevicesObservable.onNext(connectedDevices);
  }

  private CameraInternalStatus computeCameraInternalStatus() {
    CameraInternalStatus.Builder status = CameraInternalStatus.newBuilder()
        .setPairingStatus(pairingStatusObservable.getValue())
        .setCameraState(cameraStateBehaviorSubject.getValue());
    for (String address : connectedDevicesObservable.getValue()) {
      status.addConnectedDevices(address);
    }
    return status.build();
  }

  // The BluetoothPairingManager uses BluetoothPairingManager.PairingStatus, but in internal API we
  // use the PairingStatus in camera_internal_api.proto. So a conversion is done here.
  private static CameraInternalStatus.PairingStatus mapPairingStatus(PairingStatus status) {
    switch (status) {
      case NOT_ADVERTISING:
        return CameraInternalStatus.PairingStatus.DEFAULT_NOT_ADVERTISING;
      case ADVERTISING:
        return CameraInternalStatus.PairingStatus.ADVERTISING;
      case WAITING_FOR_USER_CONFIRMATION:
        return CameraInternalStatus.PairingStatus.WAITING_FOR_USER_CONFIRMATION;
      case USER_CONFIRMATION_TIMEOUT:
        return CameraInternalStatus.PairingStatus.USER_CONFIRMATION_TIMEOUT;
      case PAIRED:
        return CameraInternalStatus.PairingStatus.PAIRED;
    }
    return CameraInternalStatus.PairingStatus.DEFAULT_NOT_ADVERTISING;
  }
}
