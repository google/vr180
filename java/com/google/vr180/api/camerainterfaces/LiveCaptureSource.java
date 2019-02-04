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

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import com.google.vr180.CameraApi.CameraStatus.RecordingStatus.LiveStreamStatus.LiveStreamErrorState;
import com.google.vr180.CameraApi.VideoMode;
import java.nio.ByteBuffer;

/** Interface that provides encoded video and audio data for a live video capture. */
public interface LiveCaptureSource {

  /**
   * An interface to which the capture source writes encoded video and audio data.
   *
   * <p>The camera encoder must first call addTrack with the format of the audio and video tracks.
   * Encoder data is ignored until both a video and audio track are added.
   */
  public interface OutputMuxer {
    /**
     * Adds a track to the output stream. Both an Audio and a Video track must be added to start the
     * stream.
     *
     * <p>MUST be called from the codecHandler thread.
     *
     * @param format The format of the track
     * @return The track number (to pass to writeSampleData)
     */
    int addTrack(MediaFormat format);

    /**
     * Writes a buffer of encoded data to the RTMP stream.
     *
     * <p>MUST be called from the codecHandler thread.
     *
     * @param trackIndex The track index, returned by addTrack
     * @param buffer The buffer containing the encoded data
     * @param bufferInfo The metadata about the buffer
     */
    void writeSampleData(int trackIndex, ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo);

    /**
     * Indicates that the capture source has finished writing data.
     *
     * <p>MUST be called from the codecHandler thread.
     */
    void onEndOfStream();

    /**
     * Reports an error encountered by the capture source.
     *
     * <p>If fatal, the LiveStreamManager will attempt to stop the stream.
     *
     * @param errorState The type of error encountered (e.g. LiveStreamErrorState.CODEC_ERROR)
     */
    void onError(LiveStreamErrorState errorState);
  }

  /**
   * Starts capturing video and audio data based on the provided mode, writing the output to the
   * provided OutputMuxer.
   *
   * @param outputMuxer The target to write encoded data to.
   * @param captureMode The video capture mode for the live stream.
   * @param codecHandler The handler thread to run codec operations on.
   */
  void startCapture(OutputMuxer outputMuxer, VideoMode captureMode, Handler codecHandler);

  /** Stops the current capture. */
  void stopCapture();

  /**
   * Adjusts the target bitrate of the video codec based on the RTMP stream conditions. The encoder
   * should change its bitrate setting so that the stream can adjust based on the network speed.
   */
  void setTargetBitrate(int bitrate);

  /** Returns the minimum supported bitrate. */
  int getMinBitrate(VideoMode videoMode);

  /** Returns the maximum supported bitrate. */
  int getMaxBitrate(VideoMode videoMode);
}
