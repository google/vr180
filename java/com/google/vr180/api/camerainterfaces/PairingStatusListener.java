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

package com.google.vr180.api.camerainterfaces;

/**
 * An interface that indicates the pairing status (whether the camera is advertising for new
 * connections.
 */
public interface PairingStatusListener {

  /** Enum indicating the stage within the pairing process. */
  public enum PairingStatus {
    /** We are not advertising on the pairing UUID. */
    NOT_ADVERTISING,
    /** We are advertising on the pairing UUID. */
    ADVERTISING,
    /** We had a pairing request, we need user confirmation before proceeding. */
    WAITING_FOR_USER_CONFIRMATION,
    /** Timeout while waiting for user confirmation. */
    USER_CONFIRMATION_TIMEOUT,
    /** We succeeded in pairing. */
    PAIRED,
  }

  /** Called when the pairing status has changed. */
  void onPairingStatusChanged(PairingStatus status);
}
