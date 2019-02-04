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

import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.AudioConfiguration;
import com.google.vr180.CameraApi.CameraStatus.AudioVolumeStatus;

/** Interface for getting and setting the current audio volume level. */
public interface AudioVolumeManager {
  /** Returns the current volume status. */
  AudioVolumeStatus getVolumeStatus();

  /**
   * Updates the audio configuration of the device.
   *
   * @param newConfiguration The updated configuration of the audio volume.
   */
  void updateAudioConfiguration(AudioConfiguration newConfiguration);
}
