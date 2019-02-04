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
import com.google.vr180.api.camerainterfaces.FileProvider;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.common.media.MediaStoreUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Date;

/** Implementation of FileProvider using the android file APIs. */
public class AndroidFileProvider implements FileProvider {
  private final Context context;
  private final StorageStatusProvider storageStatusProvider;

  /**
   * Constructs a FileProvider that provides media from the local filesystem.
   *
   * @param storageStatusProvider The storage status provider to get storage paths.
   */
  public AndroidFileProvider(Context context, StorageStatusProvider storageStatusProvider) {
    this.context = context;
    this.storageStatusProvider = storageStatusProvider;
  }

  @Override
  public InputStream openFile(String path) throws FileNotFoundException {
    checkValidPath(path);
    return new FileInputStream(getFileForPath(path));
  }

  @Override
  public void deleteFile(String path) throws FileNotFoundException {
    checkValidPath(path);
    MediaStoreUtil.deleteFile(context, getFileForPath(path));
  }

  @Override
  public long getFileSize(String path) {
    try {
      checkValidPath(path);
      return getFileForPath(path).length();
    } catch (FileNotFoundException e) {
      return 0;
    }
  }

  @Override
  public Date getLastModified(String path) {
    try {
      checkValidPath(path);
      return new Date(getFileForPath(path).lastModified());
    } catch (FileNotFoundException e) {
      return new Date(0);
    }
  }

  @Override
  public boolean fileExists(String path) {
    if (storageStatusProvider.isValidPath(path)) {
      File file = new File(path);
      return file.exists();
    }
    return false;
  }

  @Override
  public File getFileForPath(String path) throws FileNotFoundException {
    checkValidPath(path);
    File file = new File(path);
    if (file.exists()) {
      return file;
    }
    throw new FileNotFoundException("File doesn't exist");
  }

  private void checkValidPath(String path) throws FileNotFoundException {
    if (!storageStatusProvider.isValidPath(path)) {
      throw new FileNotFoundException(path);
    }
  }
}
