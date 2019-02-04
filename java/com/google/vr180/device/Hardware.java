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

package com.google.vr180.device;

/**
 * Abstraction for OEM-specific hardware buttons and display/LED handling.
 */
public interface Hardware {
  /** Start tracking of hardware events and connect to display/LED controller. */
  void start(HardwareListener hardwareListener);
  /** Stop tracking of hardware events and disconnect to display/LED controller. */
  void stop();
  /** Change the UI mode. */
  void setMode(Mode mode);

  /** Interface for hardware button callbacks. */
  interface HardwareListener {
    /** Called when button is short pressed. */
    void onButtonShortPress(Button button);
    /** Called when button is long pressed. */
    void onButtonLongPress(Button button);
    /** Called when HDMI connection is changed. */
    void onHdmiStateChanged(boolean connected);
  }

  /**
   * The device mode shown to the user. e.g. Make the LED indicator blink for VIDEO_RECORDING mode.
   */
  enum Mode {
    /** The mode when the camera is off (in sleep mode). */
    OFF,
    /** The mode when the camera is idle in video mode. */
    IDLE_VIDEO,
    /** The mode when the camera is idle in photo mode. */
    IDLE_PHOTO,
    /** The mode when the camera is idle in live mode. */
    IDLE_LIVE,
    /** The mode when the camera is recording video. */
    VIDEO_RECORDING,
    /** The mode when the camera is live streaming. */
    LIVE_STREAMING,
    /** The mode when the camera is in pairing mode waiting for connection. */
    PAIRING_SEARCHING,
    /** The mode when the camera is in pairing mode waiting for user confirmation. */
    PAIRING_WAITING_CONFIRMATION,
  }

  /** Common hardware buttons. */
  enum Button {
    /** The button to switch mode. */
    MODE,
    /** The button to capture. */
    SHUTTER,
    /** The power button */
    POWER,
  }
}
