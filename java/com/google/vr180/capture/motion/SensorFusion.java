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

/** A Java object wrapper for the native implementation of sensor fusion. */
public class SensorFusion {
  static {
    System.loadLibrary("camera");
  }

  private final float[] deviceToImuTransform;
  private long nativeFilterPtr;

  public SensorFusion(float[] deviceToImuTransform) {
    this.deviceToImuTransform = deviceToImuTransform;
  }

  /** Initialized the native sensor fusion. Must be called before this object can be used. */
  public void init() {
    nativeFilterPtr = nativeInit(deviceToImuTransform);
  }

  /**
   * Releases the native sensor fusion. Must be called before releasing this object. After {@link
   * #release}, {@link #init} must be called again before this object can be used.
   */
  public void release() {
    nativeRelease(nativeFilterPtr);
  }

  /**
   * Adds a gyroscope measurement to the sensor fusion. {@see
   * http://developer.android.com/reference/android/hardware/SensorEvent.html}
   *
   * @param sample 3-tuple containing the rotation rate (rad/s) around the x-, y-, and z-axis
   * @param timestampNs the timestamp (in ns) when the measurement was taken
   */
  public void addGyroMeasurement(float[] sample, long timestampNs) {
    nativeAddGyroMeasurement(nativeFilterPtr, sample, timestampNs);
  }

  /**
   * Adds an accelerometer measurement to the sensor fusion. {@see
   * http://developer.android.com/reference/android/hardware/SensorEvent.html}
   *
   * @param sample 3-tuple containing the acceleration (in m/s^2) along the x-, y-, and z-axis
   * @param timestampNs the timestamp (in ns) when the measurement was taken
   */
  public void addAccelMeasurement(float[] sample, long timestampNs) {
    nativeAddAccelMeasurement(nativeFilterPtr, sample, timestampNs);
  }

  /**
   * Retrieves the sensor-fused device orientation converted to camera orientation coordinates.
   *
   * @return the camera coorindate orientation an angle axis (x,y,z)
   */
  public float[] getOrientation() {
    return nativeGetOrientation(nativeFilterPtr);
  }

  /** Sets the gyro bias to use when filtering sensor samples. */
  public void setGyroBias(float[] bias) {
    nativeSetGyroBias(nativeFilterPtr, bias);
  }

  /** Recenters the orientation filter, canceling yaw while keeping pitch and tilt the same. */
  public void recenter() {
    nativeRecenter(nativeFilterPtr);
  }

  /**
   * Create the native sensor fusion instance. if IMU and camera is not aligned, the relative
   * orientation is assumed to be like a typical phone.
   *
   * @param deviceToImuTransform the quaternion x,y,z,w transformation from the camera coordinate
   *     system to the imu coordinate system.
   */
  private native long nativeInit(float[] deviceToImuTransform);

  private native void nativeRelease(long nativeFilterPtr);

  private native void nativeAddGyroMeasurement(
      long nativeFilterPtr, float[] gyro, long timestampNs);

  private native void nativeAddAccelMeasurement(
      long nativeFilterPtr, float[] accel, long timestampNs);

  private native float[] nativeGetOrientation(long nativeFilterPtr);

  private native void nativeSetGyroBias(long nativeFilterPtr, float[] bias);

  private native void nativeRecenter(long nativeFilterPtr);
}
