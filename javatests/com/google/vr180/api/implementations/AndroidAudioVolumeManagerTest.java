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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.media.AudioManager;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.AudioConfiguration;
import com.google.vr180.CameraApi.CameraStatus.AudioVolumeStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class AndroidAudioVolumeManagerTest {
  @Mock private Context mockContext;
  @Mock private AudioManager mockAudioManager;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(mockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager);
  }

  @Test
  public void testGetVolumeStatus() throws Exception {
    when(mockAudioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)).thenReturn(5);
    when(mockAudioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)).thenReturn(7);

    AndroidAudioVolumeManager audioVolumeManager = new AndroidAudioVolumeManager(mockContext);
    assertThat(audioVolumeManager.getVolumeStatus())
        .isEqualTo(AudioVolumeStatus.newBuilder().setVolume(5).setMaxVolume(7).build());
  }

  @Test
  public void testSetVolume() throws Exception {
    AndroidAudioVolumeManager audioVolumeManager = new AndroidAudioVolumeManager(mockContext);
    audioVolumeManager.updateAudioConfiguration(
        AudioConfiguration.newBuilder().setVolume(3).build());
    verify(mockAudioManager).setStreamVolume(AudioManager.STREAM_SYSTEM, 3, 0);
  }
}
