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

import com.google.common.base.Optional;
import com.google.vr180.api.camerainterfaces.Exceptions.InsufficientStorageException;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.common.logging.Log;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Class for providing capture path for video and photo. */
public class CapturePathProvider {
  private static final String TAG = "CapturePathProvider";
  private static final String MP4_FILE_EXTENSION = ".vr.mp4";
  private static final String JPEG_FILE_EXTENSION = ".vr.jpg";
  private static final String CALIBRATION_VIDEO_FILENAME = "calibration.h264";
  private static final String CALIBRATION_PHOTO_FILENAME = "calibration.jpg";
  private static final String DATE_FORMAT = "yyyyMMdd_HHmmss";
  private final StorageStatusProvider storageStatusProvider;

  public CapturePathProvider(StorageStatusProvider storageStatusProvider) {
    this.storageStatusProvider = storageStatusProvider;
  }

  // Get the path for a video capture.
  public String getVideoPath(Date date, boolean calibrationMode)
      throws InsufficientStorageException {
    if (calibrationMode) {
      return getCalibrationDir(date) + "/" + CALIBRATION_VIDEO_FILENAME;
    }
    Optional<String> writePath = storageStatusProvider.getWriteBasePath();
    if (!writePath.isPresent()) {
      throw new InsufficientStorageException();
    }
    String timestamp = new SimpleDateFormat(DATE_FORMAT).format(date);
    return writePath.get() + "/" + timestamp + MP4_FILE_EXTENSION;
  }

  // Get the path for a photo capture.
  public String getPhotoPath(Date date, boolean calibrationMode)
      throws InsufficientStorageException {
    if (calibrationMode) {
      return getCalibrationDir(date) + "/" + CALIBRATION_PHOTO_FILENAME;
    }
    Optional<String> writePath = storageStatusProvider.getWriteBasePath();
    if (!writePath.isPresent()) {
      throw new InsufficientStorageException();
    }
    String timestamp = new SimpleDateFormat(DATE_FORMAT).format(date);
    return writePath.get() + "/" + timestamp + JPEG_FILE_EXTENSION;
  }

  // Returns the calibration folder given the date. The folder is created automatically if missing.
  public String getCalibrationDir(Date date) throws InsufficientStorageException {
    Optional<String> writePath = storageStatusProvider.getWriteBasePath();
    if (!writePath.isPresent()) {
      throw new InsufficientStorageException();
    }
    String timestamp = new SimpleDateFormat(DATE_FORMAT).format(date);
    String folder = writePath.get() + "/" + timestamp;
    try {
      new File(folder).mkdirs();
    } catch (Exception e) {
      Log.e(TAG, "Unable to create folder " + folder);
      InsufficientStorageException exception = new InsufficientStorageException();
      exception.addSuppressed(e);
      throw exception;
    }
    return folder;
  }
}
