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

package com.google.vr180.api.implementations;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.google.vr180.CameraApi.Vector3;
import com.google.vr180.api.camerainterfaces.GravityVectorProvider;

/** */
public class AndroidGravityVectorProvider implements GravityVectorProvider {
  /** Sampling interval in microseconds. 1 second. */
  private static final int SAMPLING_INTERVAL_US = (1000 * 1000);

  private Vector3 latestGravity = null;

  public AndroidGravityVectorProvider(Context context) {
    SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    Sensor gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
    sensorManager.registerListener(
        new SensorEventListener() {
          @Override
          public void onAccuracyChanged(Sensor sensor, int accuracy) {}

          @Override
          public void onSensorChanged(SensorEvent event) {
            latestGravity =
                Vector3.newBuilder()
                    .setX(event.values[0])
                    .setY(event.values[1])
                    .setZ(event.values[2])
                    .build();
          }
        },
        gravitySensor,
        SAMPLING_INTERVAL_US);
  }

  @Override
  public Vector3 getGravityVector() {
    return latestGravity;
  }
}
