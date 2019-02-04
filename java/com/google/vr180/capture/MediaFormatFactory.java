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

package com.google.vr180.capture;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import com.google.vr180.CameraApi.EncodingFormat;
import com.google.vr180.CameraApi.VideoMode;
import com.google.vr180.device.DebugConfig;
import com.google.vr180.media.SlowmoFormat;
import com.google.vr180.media.motion.MotionEncoder;

/** Factory for creating MediaFormat for video, audio and motion track. */
public class MediaFormatFactory {
  /** Creates the video MediaFormat according to the desired VideoMode */
  public static MediaFormat createVideoFormat(VideoMode videoMode) {
    MediaFormat format =
        MediaFormat.createVideoFormat(
            videoMode.getEncodingFormat() == EncodingFormat.H265
                ? MediaFormat.MIMETYPE_VIDEO_HEVC
                : MediaFormat.MIMETYPE_VIDEO_AVC,
            videoMode.getFrameSize().getFrameWidth(),
            videoMode.getFrameSize().getFrameHeight());
    format.setInteger(
        MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    format.setInteger(MediaFormat.KEY_BIT_RATE, (int) videoMode.getBitsPerSecond());
    format.setInteger(MediaFormat.KEY_FRAME_RATE, (int) videoMode.getFramesPerSecond());
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
    format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC);
    // TODO: The full color range setting has no effect due to a likely Android bug.
    format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL);
    setFormatSpeedFactor(videoMode, format);
    return format;
  }

  /** Creates the audio MediaFormat according to the desired VideoMode */
  public static MediaFormat createAudioFormat(VideoMode videoMode) {
    MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2);
    format.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO);
    format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
    format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
    int minBufferSize =
        AudioRecord.getMinBufferSize(
            44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize * 4);
    setFormatSpeedFactor(videoMode, format);
    return format;
  }

  /** Creates the motion MediaFormat according to the desired VideoMode */
  public static MediaFormat createMotionFormat(VideoMode videoMode) {
    MediaFormat format = new MediaFormat();
    format.setString(MediaFormat.KEY_MIME, "application/motion");
    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16);
    format.setInteger(MediaFormat.KEY_BIT_RATE, 25600);
    setFormatSpeedFactor(videoMode, format);
    // Whether to save extra camm data in the motoin track.
    if (DebugConfig.isExtraCammDataEnabled()) {
      format.setInteger(MotionEncoder.KEY_EXTRA_CAMM_DATA, 1);
    }
    return format;
  }

  /** If the capture rate is 120fps, slow down the output by 4x */
  private static void setFormatSpeedFactor(VideoMode videoMode, MediaFormat format) {
    if (videoMode.getFramesPerSecond() == 120) {
      SlowmoFormat.setSpeedFactor(format, 4);
    }
  }
}
