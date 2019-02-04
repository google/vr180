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

package com.google.vr180.media.motion;

import android.hardware.Sensor;

/** MotionEvent to hold sensor event data for processing. */
public class MotionEvent {
  /**
   * Camm type https://developers.google.com/streetview/publish/camm-spec. We use
   * Sensor.TYPE_GYROSCOPE_UNCALIBRATED for CammType.GYROSCOPE which as a length of 6: xyz sensor
   * values and xyz bias values.
   */
  public enum CammType {
    ORIENTATION(0),
    GYROSCOPE(2),
    ACCELEROMETER(3);

    private final int value;

    private CammType(int value) {
      this.value = value;
    }

    public int getType() {
      return value;
    }
  }

  public MotionEvent(CammType type, float[] data, long timestamp) {
    this.type = type;
    this.timestamp = timestamp;
    this.values = new float[data.length];
    for (int i = 0; i < data.length; i++) {
      this.values[i] = data[i];
    }
  }

  public static CammType getCammType(int sensorType) {
    switch (sensorType) {
      case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
        return CammType.GYROSCOPE;
      case Sensor.TYPE_ACCELEROMETER:
        return CammType.ACCELEROMETER;
      default:
        return CammType.ORIENTATION;
    }
  }

  public final CammType type;
  public long timestamp;
  public final float[] values;
}
