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

import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.TimeConfiguration;
import com.google.vr180.CameraApi.CameraCalibration;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.IndicatorBrightnessConfiguration;
import com.google.vr180.CameraApi.SleepConfiguration;
import java.security.KeyPair;

/** Interface for fetching and storing persistent camera stettings. */
public interface CameraSettings {

  void setLocalKeyPair(KeyPair localKeyPair);

  /** Gets the camera's key pair. */
  KeyPair getLocalKeyPair();

  /** Sets the shared key for bluetooth communication. */
  void setSharedKey(byte[] sharedKey);

  /** Gets the shared key for bluetooth communication. */
  byte[] getSharedKey();

  /**
   * Sets whether the shared key is pending finalization. If set to true, the api funcionality
   * will not be fully available.
   */
  void setSharedKeyIsPending(boolean pending);

  /** Gets whether the shared is temporary. */
  boolean getSharedKeyIsPending();

  /** Set time on the device. */
  void setTime(TimeConfiguration timeConfiguration);

  /**
   * Sets the camera calibration data.
   *
   * @param cameraCalibration The updated camera calibration data. This data applies to the widest
   *     field of view supported by the camera (according to its CameraCapabilities). The camera
   *     should scale and crop this if other fields of view are supported.
   */
  void setCameraCalibration(CameraCalibration cameraCalibration);

  /**
   * Gets the camera calibration. This could depend on the active capture mode if that mode crops
   * part of the sensor field-of-view.
   */
  CameraCalibration getCameraCalibration();

  /** Performs a factory reset on the camera. */
  void factoryReset();

  /**
   * Reformats the camera storage (e.g. sd card).
   */
  void formatStorage();

  /** Sets the indicator brightness configuration. */
  void setIndicatorBrightness(IndicatorBrightnessConfiguration brightness);

  /**
   * Gets the current indicator brightness configuration (or null if brightness setting is not
   * supported).
   */
  IndicatorBrightnessConfiguration getIndicatorBrightness();

  /** Sets the camera sleep configuration. */
  void setSleepConfiguration(SleepConfiguration sleepConfiguration);

  /** Gets the camera sleep configuration (or null if sleep configuration is not supported). */
  SleepConfiguration getSleepConfiguration();

  /** Sets the active capture mode */
  void setActiveCaptureMode(CaptureMode captureMode);

  /** Gets the active capture mode */
  CaptureMode getActiveCaptureMode();

  /** Removes the live endpoint from the capture mode */
  void clearLiveEndPoint();

  /** Sets the spherical metadata */
  void setSphericalMetadata(byte[] st3d, byte[] sv3d);
}
