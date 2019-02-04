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

/** Interface that tells whether pairing is currently active. */
public interface PairingManager {

  void startPairing();

  void confirmPairing();

  void stopPairing();

  /** Returns whether the camera is currently in pairing mode. */
  boolean isPairingActive();

  /** Adds a listener for updates on the pairing status. */
  void addPairingStatusListener(PairingStatusListener listener);

  /** Adds a listener for when pairing has completed. */
  void addPairingCompleteListener(PairingCompleteListener listener);
}
