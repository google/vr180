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

package com.google.vr180.app.stubs;

import android.widget.TextView;
import com.google.vr180.device.Hardware;

/** Stub implementation of {@link Hardware} using UI controls in the MainActivity. */
public class Emulator implements Hardware {

  private TextView modeView;
  private HardwareListener hardwareListener;

  public void setViews(
      android.widget.Button shutterButton,
      android.widget.Button modeButton,
      TextView modeView) {
    this.modeView = modeView;
    if (shutterButton != null) {
      shutterButton.setOnClickListener(unused -> onButtonPress(Button.SHUTTER, false));
      shutterButton.setOnLongClickListener(
          unused -> {
            onButtonPress(Button.SHUTTER, true);
            return true;
          });
    }
    if (modeButton != null) {
      modeButton.setOnClickListener(unused -> onButtonPress(Button.MODE, false));
      modeButton.setOnLongClickListener(
          unused -> {
            onButtonPress(Button.MODE, true);
            return true;
          });
    }
  }

  @Override
  public void start(HardwareListener hardwareListener) {
    this.hardwareListener = hardwareListener;
  }

  @Override
  public void stop() {
    this.hardwareListener = null;
  }

  @Override
  public void setMode(Mode mode) {
    if (modeView != null) {
      modeView.setText(mode.toString());
    }
  }

  private void onButtonPress(Button button, boolean isLongPress) {
    if (hardwareListener == null) {
      return;
    }
    if (isLongPress) {
      hardwareListener.onButtonLongPress(button);
    } else {
      hardwareListener.onButtonShortPress(button);
    }
  }
}
