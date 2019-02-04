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

import com.google.vr180.CameraApi.CameraCalibration;
import com.google.vr180.CameraApi.CameraStatus.RecordingStatus.LiveStreamStatus;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.api.camerainterfaces.Exceptions.CriticallyLowBatteryException;
import com.google.vr180.api.camerainterfaces.Exceptions.InsufficientStorageException;
import com.google.vr180.api.camerainterfaces.Exceptions.InvalidRequestException;
import com.google.vr180.api.camerainterfaces.Exceptions.ThermalException;
import java.util.Date;

/**
 * Interface that provides control methods for recording or taking a photo and configuring capture
 * modes.
 */
public interface CaptureManager {
  /**
   * Start recording video or take a photo.
   *
   * @throws CriticallyLowBatteryException if the battery is too low to start capturing.
   * @throws InsufficientStorageException if there isn't enough storage on the camera to perform the
   *     requested action.
   * @throws ThermalException if the camera got too overheated to perform the request.
   */
  void startCapture()
      throws CriticallyLowBatteryException, InsufficientStorageException, ThermalException;

  /** Stop recording video. */
  void stopCapture();

  /** Returns whether recording is running (or a live stream is broadcasting). */
  boolean isRecording();

  /**
   * If the camera is recording, returns when the current recording session started.
   * If not recording, returns null.
   */
  Date getRecordingStartTime();

  /** Returns the currently active capture mode. */
  CaptureMode getActiveCaptureMode();

  /**
   * Sets the currently active capture mode.
   * @param mode The new mode to apply.
   * @throws InvalidRequestException if the camera cannot apply the new mode. For example, if the
   *   mode changes the active capture type but the camera is currently recording.
   */
  void setActiveCaptureMode(CaptureMode mode) throws InvalidRequestException;

  /** Gets the camera calibration associated with the active capture mode. */
  CameraCalibration getActiveCalibration();

  /**
   * Return the status of the live stream if the camera is currently live streaming. If not
   * currently streaming, returns null.
   */
  LiveStreamStatus getLiveStreamStatus();
}
