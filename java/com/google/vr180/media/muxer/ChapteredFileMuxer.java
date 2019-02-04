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

package com.google.vr180.media.muxer;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaMuxer.OutputFormat;
import android.media.MediaScannerConnection;
import android.support.annotation.Nullable;
import android.util.Pair;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.MediaConstants;
import com.google.vr180.media.MediaCreationUtils;
import com.google.vr180.media.MediaEncoder;
import com.google.vr180.media.metadata.MetadataInjector;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Implementation of the {@link MediaMux} interface that supports creating a local MP4 file (or a
 * collection of chaptered MP4 files) from an AVC video track, an AAC audio track, and optionally a
 * camera motion metadata track. In addition, the muxer can take a {@link MetadataInjector} to add
 * additional video metadata (e.g. VR) to the recorded video.
 *
 * This class traps all Exceptions and returns success/fail instead so that clients can be
 * written in a clean fashion.
 */
@TargetApi(21)
@NotThreadSafe
public final class ChapteredFileMuxer implements MediaMux {
  private static final String TAG = "ChapteredFileMuxer";
  // Chapter size of about 2GB. The actual file size will be slightly larger than the sample
  // data size due to additional mp4 boxes (mostly MOOV).
  //
  // WARNING : to change MAX_CHAPTER_SAMPLE_DATA_BYTES to >2GB for larger chapter files, the app
  // must be compiled in 64-bit mode.
  private static final long MAX_CHAPTER_SAMPLE_DATA_BYTES = 2000 * 1024L * 1024L;

  // Use a predefined track index for video, audio and motion, to keep video track first.
  private static final int VIDEO_TRACK_INDEX = 0;
  private static final int AUDIO_TRACK_INDEX = 1;
  private static final int MOTION_TRACK_INDEX = 2;

  // Remember the context for triggering a mediascan after each video.
  private final Context context;
  // The filename for the first chapter.
  private final String basePath;
  // Whether the muxer wants a motion metadata track.
  private final boolean requiresMotionTrack;
  // Metadata injector for the video.
  private final MetadataInjector metadataInjector;

  // List for MediaFormat in the order of being added.
  private final MediaFormat[] formats = new MediaFormat[3];
  // Encoder for each track for getting buffers.
  private final MediaEncoder[] encoders = new MediaEncoder[3];
  // Buffered audio and motion metadata samples.
  private final ArrayDeque<Pair<Integer, BufferInfo>> bufferedAudioSamples = new ArrayDeque<>();
  private final ArrayDeque<Pair<Integer, BufferInfo>> bufferedMotionSamples = new ArrayDeque<>();

  // Muxer for the active chapter file.
  private MediaMuxer muxer;
  // Total bytes written of the active chapter file.
  private long bytesWritten;
  // Total bytes written of the finished chapters.
  private long pastChapterBytes;

  // Muxer state
  private boolean isPrepared;
  private boolean isStarted;
  private boolean isStopped;
  private boolean isReleased;

  // The first and last video timestamp of active chapter.
  private long firstVideoTimestamp = -1;
  private long lastVideoTimestamp = 0;
  // Current chapter index.
  private int chapterIndex = 0;
  // File name of the active chapter file.
  private String chapterPath;

  public ChapteredFileMuxer(
      Context context, String path, boolean requiresMotionTrack, MetadataInjector metadataInjector)
      throws IOException {
    this.context = context;
    this.basePath = path;
    this.requiresMotionTrack = requiresMotionTrack;
    this.metadataInjector = metadataInjector;

    muxer = new MediaMuxer(this.basePath, OutputFormat.MUXER_OUTPUT_MPEG_4);
    chapterPath = basePath;
  }

  @Override
  public void setErrorCallback(@Nullable ErrorCallback errorCallback) {
    // Ignore.  There are no asynchronous errors.
  }

  @Override
  public int addTrack(MediaFormat format, MediaEncoder encoder) {
    if (isStarted) {
      Log.e(TAG, "Cannot add a track once started");
      return -1;
    }
    if (isStopped) {
      Log.e(TAG, "Cannot add a track once stopped");
      return -1;
    }
    if (isReleased) {
      Log.e(TAG, "Cannot add a track after release");
      return -1;
    }

    Preconditions.checkState(format != null && encoder != null);
    int trackIndex = -1;
    if (MediaCreationUtils.isVideoFormat(format)) {
      trackIndex = VIDEO_TRACK_INDEX;
    } else if (MediaCreationUtils.isAudioFormat(format)) {
      trackIndex = AUDIO_TRACK_INDEX;
    } else if (MediaCreationUtils.isMotionFormat(format)) {
      trackIndex = MOTION_TRACK_INDEX;
    } else {
      Log.e(TAG, "Invalid MediaFormat");
      return -1;
    }
    if (encoders[trackIndex] != null) {
      Log.e(TAG, "Track #" + trackIndex + " was already added");
      return -1;
    }
    formats[trackIndex] = format;
    encoders[trackIndex] = encoder;
    return trackIndex;
  }

  @Override
  public boolean hasAllTracks() {
    return (!isReleased
        && !isStopped
        && (encoders[VIDEO_TRACK_INDEX] != null)
        && (encoders[AUDIO_TRACK_INDEX] != null)
        && (!requiresMotionTrack || encoders[MOTION_TRACK_INDEX] != null));
  }

  @Override
  public boolean release() {
    if (isReleased) {
      // Allow multiple calls without error.
      return true;
    }
    try {
      muxer.release();
      isReleased = true;
    } catch (Exception e) {
      Log.e(TAG, "Releasing media muxer failed", e);
    }

    return isReleased;
  }

  @Override
  public int prepare() {
    if (isReleased) {
      Log.e(TAG, "Cannot prepare once released");
      return MediaConstants.STATUS_ERROR;
    }
    if (isStopped) {
      Log.e(TAG, "Cannot prepare once stopped");
      return MediaConstants.STATUS_ERROR;
    }
    if (isStarted) {
      Log.e(TAG, "Cannot prepare once started");
      return MediaConstants.STATUS_ERROR;
    }

    // Nothing to do.
    bytesWritten = 0;
    pastChapterBytes = 0;
    isPrepared = true;
    return MediaConstants.STATUS_SUCCESS;
  }

  @Override
  public boolean start() {
    if (isReleased) {
      Log.e(TAG, "Cannot start once released");
      return false;
    }
    if (isStopped) {
      Log.e(TAG, "Cannot restart once stopped");
      return false;
    }
    if (!isPrepared) {
      Log.e(TAG, "Muxer not prepared");
      return false;
    }
    if (isStarted) {
      // Allow multiple calls without error.
      return true;
    }
    if (!hasAllTracks()) {
      Log.e(TAG, "Cannot start without all tracks");
      return false;
    }
    isStarted = startMuxer();
    return isStarted;
  }

  @Override
  public boolean stop() {
    if (isReleased) {
      Log.e(TAG, "Cannot stop once released");
      return false;
    }
    if (!isStarted) {
      Log.e(TAG, "Muxer not started");
      return false;
    }
    if (isStopped) {
      // Allow multiple calls without error.
      return true;
    }

    // Mark the muxer as stopped.
    isStopped = true;
    return stopMuxer();
  }

  @Override
  public boolean isStarted() {
    return isStarted && !isStopped && !isReleased;
  }

  @Override
  public int getOutputBufferUsed() {
    return -1;
  }

  @Override
  public Pair<Integer, Integer> getCurrentByteThroughput() {
    // Not implemented.
    return null;
  }

  @Override
  public void setOutputBufferLimit(int bytes) {}

  @Override
  public boolean writeSampleDataAsync(int trackIndex, int bufferIndex, BufferInfo bufferInfo) {
    if (isReleased) {
      Log.e(TAG, "Cannot write data once released");
      return false;
    }
    if (isStopped) {
      Log.e(TAG, "Cannot write data once stopped");
      return false;
    }
    if (!isStarted) {
      Log.e(TAG, "Muxer not started");
      return false;
    }

    // Prescreen audio and motion sample data for chaptering.
    if (prescreenSampleData(trackIndex, bufferIndex, bufferInfo)) {
      return true;
    }
    // Start a new chapter if the size is approaching the limit.
    if (!prepareChapter(trackIndex, bufferInfo)) {
      return false;
    }

    try {
      ByteBuffer buffer = encoders[trackIndex].getOutputBuffer(bufferIndex);
      muxer.writeSampleData(trackIndex, buffer, bufferInfo);
      bytesWritten += (bufferInfo.size - bufferInfo.offset);
      encoders[trackIndex].releaseOutputBuffer(bufferIndex);

      // Write audio and motion metadata up to the video timestamp.
      if (trackIndex == VIDEO_TRACK_INDEX) {
        lastVideoTimestamp = bufferInfo.presentationTimeUs;
        writeBufferedSamples(AUDIO_TRACK_INDEX);
        writeBufferedSamples(MOTION_TRACK_INDEX);
      }
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Writing sample data failed", e);
    }
    return false;
  }

  @Override
  public void cleanupPartialResults() {
    deleteFile();
  }

  @Override
  public long getBytesWritten() {
    return bytesWritten + pastChapterBytes;
  }

  // Add all the tracks and start the muxer.
  private boolean startMuxer() {
    // Create a new muxer by adding the MediaFormat in the video/aidio/motion order.
    try {
      Log.i(TAG, "Start chapter " + chapterPath);
      muxer = new MediaMuxer(chapterPath, OutputFormat.MUXER_OUTPUT_MPEG_4);
      for (MediaFormat format : formats) {
        if (format != null) { // Motion format could be null.
          muxer.addTrack(format);
        }
      }
      muxer.start();
      return true;
    } catch (Exception e) {
      Log.e(TAG, "Failed to create muxer for " + chapterPath, e);
      return false;
    }
  }

  private boolean stopMuxer() {
    Log.i(TAG, "Finishing " + chapterPath);
    try {
      muxer.stop();
      muxer.release();
    } catch (Exception e) {
      Log.e(TAG, "Muxer not stopped cleanly. Deleting media file: " + chapterPath, e);
      deleteFile();
      return false;
    }

    if (metadataInjector != null
        && !metadataInjector.injectMetadata(
            chapterPath,
            formats[VIDEO_TRACK_INDEX].getInteger(MediaFormat.KEY_WIDTH),
            formats[VIDEO_TRACK_INDEX].getInteger(MediaFormat.KEY_HEIGHT))) {
      Log.e(TAG, "Failed to format video: " + chapterPath);
    }
    // Trigger media scanner to update database.
    MediaScannerConnection.scanFile(context, new String[] {chapterPath}, null, null);
    return true;
  }

  // Start a new chapter if the file size is approaching the limit and the current sample is
  // a video key frame.
  private boolean prepareChapter(int trackIndex, BufferInfo bufferInfo) {
    if (trackIndex != VIDEO_TRACK_INDEX
        || (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) == 0
        || bytesWritten + bufferInfo.size - bufferInfo.offset <= MAX_CHAPTER_SAMPLE_DATA_BYTES) {
      return true;
    }
    if (!stopMuxer()) {
      return false;
    }

    // Start next chapter.
    firstVideoTimestamp = bufferInfo.presentationTimeUs;
    Log.i(TAG, "Fist video sample @" + bufferInfo.presentationTimeUs);

    pastChapterBytes += bytesWritten;
    bytesWritten = 0;
    chapterIndex += 1;
    chapterPath = getChapterPath();
    return startMuxer();
  }

  // Screen the sample data, and return true if the sample data should not be written. The purpose
  // of this function is to avoid having audio and motion data before the first video frame.
  private boolean prescreenSampleData(int trackIndex, int bufferIndex, BufferInfo bufferInfo) {
    // Save timestamp of first video sample
    if (trackIndex == VIDEO_TRACK_INDEX) {
      if (firstVideoTimestamp == -1) {
        firstVideoTimestamp = bufferInfo.presentationTimeUs;
        Log.i(TAG, "Fist video sample @" + bufferInfo.presentationTimeUs);
      }
      return false;
    }
    if (firstVideoTimestamp != -1 && bufferInfo.presentationTimeUs < firstVideoTimestamp) {
      MediaEncoder encoder = encoders[trackIndex];
      Log.i(TAG, "Discard sample @" + bufferInfo.presentationTimeUs + " of " + encoder.getName());
      encoder.releaseOutputBuffer(bufferIndex);
      return true;
    } else if (firstVideoTimestamp == -1 || bufferInfo.presentationTimeUs > lastVideoTimestamp) {
      if (trackIndex == AUDIO_TRACK_INDEX) {
        bufferedAudioSamples.add(new Pair<>(bufferIndex, bufferInfo));
      } else {
        bufferedMotionSamples.add(new Pair<>(bufferIndex, bufferInfo));
      }
      return true;
    }
    return false;
  }

  private boolean writeBufferedSamples(int trackIndex) {
    MediaEncoder encoder = encoders[trackIndex];
    ArrayDeque<Pair<Integer, BufferInfo>> buffers =
        trackIndex == AUDIO_TRACK_INDEX ? bufferedAudioSamples : bufferedMotionSamples;
    while (!buffers.isEmpty() && buffers.peek().second.presentationTimeUs <= lastVideoTimestamp) {
      Pair<Integer, BufferInfo> entry = buffers.poll();
      ByteBuffer buffer = encoder.getOutputBuffer(entry.first);
      BufferInfo bufferInfo = entry.second;
      if (entry.second.presentationTimeUs >= firstVideoTimestamp) {
        muxer.writeSampleData(trackIndex, buffer, entry.second);
        bytesWritten += (bufferInfo.size - bufferInfo.offset);
      } else {
        Log.i(TAG, "Discard sample @" + bufferInfo.presentationTimeUs + " of " + encoder.getName());
      }
      encoder.releaseOutputBuffer(entry.first);
    }
    return true;
  }

  // Use basename_[chapter_index] as the name for the chapter file.
  private String getChapterPath() {
    int extensionIndex = basePath.indexOf('.', Math.max(basePath.lastIndexOf('/'), 0));
    return basePath.substring(0, extensionIndex)
        + String.format("_%04d", chapterIndex)
        + basePath.substring(extensionIndex);
  }

  private void deleteFile() {
    File file = new File(chapterPath);
    if (file.delete()) {
      Log.e(TAG, "Removed media file due to muxer failure: " + chapterPath);
    }
  }
}
