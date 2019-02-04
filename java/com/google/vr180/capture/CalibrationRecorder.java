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

import android.content.Context;
import android.hardware.camera2.CaptureResult;
import android.media.MediaScannerConnection;
import android.os.Build;
import com.google.common.collect.ImmutableMap;
import com.google.vr180.capture.motion.MotionCaptureSource;
import com.google.vr180.capture.video.VideoCaptureSource;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.motion.MotionEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.EnumMap;

/** A class for recording camera calibration data. */
public class CalibrationRecorder
    implements MotionCaptureSource.MotionEventListener, VideoCaptureSource.CaptureResultCallback {

  private static final String TAG = "CalibrationRecorder";

  private enum DataType {
    ACCEL,
    GYRO,
    FRAME,
    METADATA;
  }

  private static final ImmutableMap<DataType, String> FILENAMES =
      ImmutableMap.of(
          DataType.ACCEL, "accel_data.txt",
          DataType.GYRO, "gyro_data.txt",
          DataType.FRAME, "calibration_frame_timestamps.txt",
          DataType.METADATA, "metadata.txt");

  private final Context context;
  private final EnumMap<DataType, BufferedWriter> writers;
  private final EnumMap<DataType, String> filepaths;
  private final VideoCaptureSource videoCaptureSource;
  private final MotionCaptureSource motionCaptureSource;
  private String outputDir;

  public CalibrationRecorder(
      Context context,
      VideoCaptureSource videoCaptureSource,
      MotionCaptureSource motionCaptureSource) {
    this.context = context;
    this.videoCaptureSource = videoCaptureSource;
    this.motionCaptureSource = motionCaptureSource;
    writers = new EnumMap<DataType, BufferedWriter>(DataType.class);
    filepaths = new EnumMap<DataType, String>(DataType.class);
  }

  public boolean open(String outputDir) {
    File dir = new File(outputDir);
    if (!dir.exists()) {
      Log.e(TAG, "Calibration output directory does not exist: " + outputDir);
      return false;
    }
    if (isOpen()) {
      Log.e(TAG, "Calibration recorder is already open for directory: " + this.outputDir);
      return false;
    }
    if (!filepaths.isEmpty() || !writers.isEmpty()) {
      Log.e(TAG, "Existing files still open.");
      return false;
    }
    try {
      this.outputDir = outputDir;
      for (DataType type : DataType.values()) {
        String filename = String.format("%s/%s", outputDir, FILENAMES.get(type));
        filepaths.put(type, filename);
        writers.put(type, new BufferedWriter(new FileWriter(filename)));
      }
      if (videoCaptureSource != null) {
        videoCaptureSource.setCaptureResultCallback(this, /* handler= */ null);
      }
      if (motionCaptureSource != null) {
        motionCaptureSource.addMotionEventListener(this);
      }
      return true;
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
      close();
    }
    return false;
  }

  public void close() {
    if (isOpen()) {
      writeMetadata();
      // Close all the files.
      for (BufferedWriter writer : writers.values()) {
        tryClose(writer);
      }
      writers.clear();

      // Scan the files so they are visible to the system immediately.
      MediaScannerConnection.scanFile(
          context, filepaths.values().toArray(new String[filepaths.size()]), null, null);
      filepaths.clear();

      this.outputDir = null;
      if (videoCaptureSource != null) {
        videoCaptureSource.setCaptureResultCallback(null, null);
      }
      if (motionCaptureSource != null) {
        motionCaptureSource.removeMotionEventListener(this);
      }
    }
  }

  private void tryClose(BufferedWriter writer) {
    if (writer != null) {
      try {
        writer.close();
      } catch (Exception e) {
        Log.e(TAG, e.getMessage());
      }
    }
  }

  public void writeMetadata() {
    BufferedWriter writer = writers.get(DataType.METADATA);
    tryWrite(writer, String.format("%s=%s", "DutSerial", Build.SERIAL));
    tryWrite(writer, String.format("%s=%s", "DutBuildFingerprint", Build.FINGERPRINT));

    // TODO: Write real values.
    tryWrite(writer, String.format("%s=%d", "VideoISO", 0));
    tryWrite(writer, String.format("%s=%f", "VideoExposureTime", 0.0));
  }

  public boolean isOpen() {
    return outputDir != null;
  }

  private void tryWrite(BufferedWriter writer, String string) {
    if (!isOpen()) {
      Log.e(TAG, "Attempting to write to inactive calibration recorder.");
      return;
    }
    try {
      writer.write(string);
      writer.newLine();
    } catch (Exception e) {
      e.printStackTrace();
      close();
    }
  }

  @Override
  public void onCaptureResult(CaptureResult result) {
    tryWrite(
        writers.get(DataType.FRAME),
        String.format(
            "%d %d %d %d",
            result.get(CaptureResult.SENSOR_TIMESTAMP),
            result.getFrameNumber(),
            result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)));
  }

  @Override
  public void onMotionEvent(MotionEvent e) {
    switch (e.type) {
      case ACCELEROMETER:
        if (e.values.length < 3) {
          Log.e(TAG, "Invalid accel data.");
          return;
        }
        tryWrite(
            writers.get(DataType.ACCEL),
            String.format("%d %a %a %a", e.timestamp, e.values[0], e.values[1], e.values[2]));
        break;
      case GYROSCOPE:
        if (e.values.length < 6) {
          Log.e(TAG, "Invalid gyro data.");
          return;
        }
        tryWrite(
            writers.get(DataType.GYRO),
            String.format(
                "%d %a %a %a",
                e.timestamp,
                e.values[0] - e.values[3],
                e.values[1] - e.values[4],
                e.values[2] - e.values[5]));
        break;
      default:
        break;
    }
  }
}
