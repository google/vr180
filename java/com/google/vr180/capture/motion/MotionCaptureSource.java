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

package com.google.vr180.capture.motion;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener2;
import android.hardware.SensorManager;
import android.os.Handler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.device.DebugConfig;
import com.google.vr180.media.motion.MotionEvent;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Implemenation of {@link SensorEventListener2} that performs sensor fusion of gyroscope and
 * accelerometer data to produce a camera orientation angle axis.
 */
public class MotionCaptureSource implements SensorEventListener2 {
  private static final String TAG = "MotionCaptureSource";
  private static final int GYRO_BIAS_OFFSET = 3;
  private static final int LATENCY_US_LOW = 100_000;
  private static final int SAMPLE_INTERNVAL_US_HIGH = 1_000_000 / 50;
  private static final int LATENCY_US_HIGH = 120_000_000;
  private static final int WARNING_INTERVAL_MS = SAMPLE_INTERNVAL_US_HIGH * 2 / 1000;

  public static final int DEFAULT_SAMPLE_INTERNVAL_US = 1_000_000 / 200;

  /** Callback to notify client when new motion data is available. */
  public interface MotionEventListener {
    void onMotionEvent(MotionEvent e);
  }

  /** Callback to notify client when an error is encountered while started. */
  public interface ErrorCallback {
    /** Error was encountered */
    void onError(int errorCode);
  }

  private final SensorManager sensorManager;
  private final Sensor accelSensor;
  private final Sensor gyroSensor;
  private final SensorFusion filter;
  private final float[] gyroBias;
  private final ArrayDeque<MotionEvent> gyroQueue;
  private final ArrayDeque<MotionEvent> accelQueue;
  private final Set<MotionEventListener> motionEventListeners;
  private final long imuTimestampOffsetNs;
  private long timestampOffsetNs;
  private int sampleIntervalUs;
  private Handler handler;
  private long lastGyroTimestamp = -1L;
  private long lastAccelTimestamp = -1L;
  private volatile boolean isActive;
  private boolean lowLatency;
  private boolean reconfigureAfterFlush;

  public MotionCaptureSource(
      Context context, float[] deviceToImuTransform, long imuTimestampOffsetNs) {
    this(context, new SensorFusion(deviceToImuTransform), imuTimestampOffsetNs);
  }

  @VisibleForTesting
  public MotionCaptureSource(Context context, SensorFusion filter, long imuTimestampOffsetNs) {
    this.filter = filter;
    this.imuTimestampOffsetNs = imuTimestampOffsetNs;
    filter.init();
    sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
    gyroBias = new float[3];
    timestampOffsetNs = imuTimestampOffsetNs;
    gyroQueue = new ArrayDeque<>();
    accelQueue = new ArrayDeque<>();
    motionEventListeners = new HashSet<>();
    sampleIntervalUs = DEFAULT_SAMPLE_INTERNVAL_US;
  }

  /**
   * Sets the rate for sampling from the gyroscope and accelerometer
   *
   * @param sampleRateHz the rate to sample the motion sensors for low-latency mode.
   * @param handler the handler with which to execute sensor and orientation callbacks
   */
  public synchronized boolean configure(int sampleRateHz, Handler handler) {
    this.sampleIntervalUs = (1000 * 1000) / sampleRateHz;
    this.handler = handler;
    // Starts at low-latency mode.
    Preconditions.checkArgument(
        sensorManager.registerListener(
            this, gyroSensor, sampleIntervalUs, LATENCY_US_LOW, handler));
    Preconditions.checkArgument(
        sensorManager.registerListener(
            this, accelSensor, sampleIntervalUs, LATENCY_US_LOW, handler));
    lowLatency = true;
    return true;
  }

  /** Changes the latency of sensor reporting. */
  public synchronized void configureLatency(boolean lowLatency) {
    if (this.lowLatency == lowLatency) {
      return;
    }
    this.lowLatency = lowLatency;
    reconfigureAfterFlush = true;

    // Flush the readings and change latency when flush is done.
    Log.i(TAG, "Change latency mode to " + (lowLatency ? "low" : "high"));
    if (!sensorManager.flush(this)) {
      Log.e(TAG, "Failed to flush SensorManger");
    }
  }

  /** Start the motion source, enabling notifications to sensor and orientation event listeners. */
  public synchronized boolean start() {
    // Reset the filter whenever we start capture.
    filter.recenter();
    filter.setGyroBias(gyroBias);
    timestampOffsetNs = DebugConfig.isCalibrationEnabled() ? 0 : imuTimestampOffsetNs;
    isActive = true;
    return true;
  }

  /**
   * Stop the motion source, removing all listeners. Note: sensor fusion will continue to run in the
   * background.
   */
  public synchronized boolean stop() {
    // Stop sending sample updates, but keep reading and filtering sensor data.
    reconfigureAfterFlush = false;
    sensorManager.flush(this);
    isActive = false;
    return true;
  }

  /** Releases the motion source, unregistering all senser listeners. */
  public synchronized boolean release() {
    sensorManager.unregisterListener(this);
    filter.release();
    return true;
  }

  public synchronized void setErrorCallback(@Nullable ErrorCallback callback) {
    // Ignore for now.
  }

  @Override
  public synchronized void onAccuracyChanged(Sensor sensor, int accuracy) {
    Log.i(TAG, "onAccuracyChanged #" + sensor + ", fifoMax=" + sensor.getFifoMaxEventCount());
  }

  @Override
  public synchronized void onFlushCompleted(final Sensor sensor) {
    Log.i(TAG, "onFlushCompleted #" + sensor.getType());
    if (!reconfigureAfterFlush) {
      return;
    }

    int intervalUs = lowLatency ? sampleIntervalUs : SAMPLE_INTERNVAL_US_HIGH;
    int latencyUs = lowLatency ? LATENCY_US_LOW : LATENCY_US_HIGH;
    sensorManager.unregisterListener(this, sensor);

    Log.i(TAG, "Changing #" + sensor.getType() + " to I=" + intervalUs + ",L=" + latencyUs);
    Preconditions.checkArgument(
        sensorManager.registerListener(this, sensor, intervalUs, latencyUs, handler));
  }

  @Override
  public synchronized void onSensorChanged(SensorEvent event) {
    // TODO: It might be more efficient to keep a queue of free and full buffer instead of
    // allocating a new buffer every time. See MicInput.java
    MotionEvent motionEvent =
        new MotionEvent(
            MotionEvent.getCammType(event.sensor.getType()), event.values, event.timestamp);
    onMotionEvent(motionEvent);
  }

  @VisibleForTesting
  public void onMotionEvent(MotionEvent event) {
    long lastTimestamp = -1L;
    switch (event.type) {
      case GYROSCOPE:
        if (event.timestamp > lastGyroTimestamp) {
          updateGyroBias(event);
          gyroQueue.addLast(event);
          lastTimestamp = lastGyroTimestamp;
          lastGyroTimestamp = event.timestamp;
          break;
        } else {
          Log.e(TAG, "Gyro data went backward: " + lastGyroTimestamp + "->" + event.timestamp);
          return;
        }
      case ACCELEROMETER:
        if (event.timestamp > lastAccelTimestamp) {
          accelQueue.addLast(event);
          lastTimestamp = lastAccelTimestamp;
          lastAccelTimestamp = event.timestamp;
          break;
        } else {
          Log.e(TAG, "Accel data went backward: " + lastAccelTimestamp + "->" + event.timestamp);
          return;
        }
      default:
        return;
    }

    // Give a warning about large timestamp jumps, which happends when changing latency.
    long deltaMs = (event.timestamp - lastTimestamp) / 1_000_000L;
    if (lastTimestamp >= 0 && deltaMs > WARNING_INTERVAL_MS) {
      Log.w(TAG, "Time jump for #" + event.type + " @" + lastTimestamp + ": " + deltaMs + "ms");
    }

    // TODO: Maybe proccess the buffer queues on a separate thread.
    processEvents();
  }

  private void processEvents() {
    MotionEvent event = null;
    while (!gyroQueue.isEmpty() && !accelQueue.isEmpty()) {
      if (gyroQueue.getFirst().timestamp < accelQueue.getFirst().timestamp) {
        event = gyroQueue.removeFirst();
        filter.addGyroMeasurement(event.values, event.timestamp);
        // Notify gyro update and orientation update.
        maybeNotifyMotionEvent(event);
        maybeNotifyMotionEvent(
            new MotionEvent(
                MotionEvent.CammType.ORIENTATION, filter.getOrientation(), event.timestamp));
      } else {
        event = accelQueue.removeFirst();
        filter.addAccelMeasurement(event.values, event.timestamp);
        maybeNotifyMotionEvent(event);
      }
    }
  }

  /** Adds a client to be notified when a new sensor data is available. */
  public synchronized void addMotionEventListener(MotionEventListener callback) {
    if (callback != null) {
      motionEventListeners.add(callback);
    }
  }

  /** Removes a client from sensor notifications. */
  public synchronized void removeMotionEventListener(MotionEventListener callback) {
    if (callback != null) {
      motionEventListeners.remove(callback);
    }
  }

  private void updateGyroBias(MotionEvent event) {
    for (int i = 0; i < gyroBias.length; i++) {
      if (isActive) {
        // When capture is active override online gyro bias with the static gyro bias.
        event.values[i + GYRO_BIAS_OFFSET] = gyroBias[i];
      } else {
        // When capture is inactive, update the static gyro bias.
        gyroBias[i] = event.values[i + GYRO_BIAS_OFFSET];
      }
    }
  }

  private void maybeNotifyMotionEvent(MotionEvent event) {
    if (isActive) {
      // Apply timestamp offset.
      event.timestamp += timestampOffsetNs;
      for (MotionEventListener listener : motionEventListeners) {
        listener.onMotionEvent(event);
      }
      // Undo the timestamp offset.
      event.timestamp -= timestampOffsetNs;
    }
  }
}
