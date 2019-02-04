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
import android.media.AudioManager;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.AudioConfiguration;
import com.google.vr180.CameraApi.CameraStatus.AudioVolumeStatus;
import com.google.vr180.api.camerainterfaces.AudioVolumeManager;

/** Manages the volume of system sounds. */
public class AndroidAudioVolumeManager implements AudioVolumeManager {
  /** Which audio stream we manage the volume for. */
  private static final int STREAM_TYPE = AudioManager.STREAM_SYSTEM;

  private final AudioManager audioManager;

  public AndroidAudioVolumeManager(Context context) {
    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
  }

  /** Returns the current volume status. */
  @Override
  public AudioVolumeStatus getVolumeStatus() {
    return AudioVolumeStatus.newBuilder()
        .setVolume(audioManager.getStreamVolume(STREAM_TYPE))
        .setMaxVolume(audioManager.getStreamMaxVolume(STREAM_TYPE))
        .build();
  }

  /**
   * Updates the volume configuration of the device.
   *
   * @param newConfiguration The updated configuration of the audio volume.
   */
  @Override
  public void updateAudioConfiguration(AudioConfiguration newConfiguration) {
    audioManager.setStreamVolume(STREAM_TYPE, newConfiguration.getVolume(), 0);
  }
}
