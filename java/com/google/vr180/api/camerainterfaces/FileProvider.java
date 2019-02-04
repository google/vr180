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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Date;

/** Helper for mapping /media/ paths to data. */
public interface FileProvider {
  /**
   * Opens the file to read.
   *
   * @param path The path to read.
   * @return A stream to read the file (should be seekable to fast-forward for a range).
   */
  InputStream openFile(String path) throws FileNotFoundException;

  /**
   * Deletes the file with the specified path, or throws FileNotFoundException if it is missing.
   *
   * @param path The path to delete.
   */
  void deleteFile(String path) throws FileNotFoundException;

  /**
   * Gets the size of the file, or 0 if the file doesn't exist.
   *
   * @param path The path of the file.
   */
  long getFileSize(String path);

  /**
   * Gets the last modified time for a file.
   *
   * @param path The path of the file.
   */
  Date getLastModified(String path);

  /**
   * Returns true if the file exists, false if it doesn't.
   *
   * @param path The path of the file.
   */
  boolean fileExists(String path);

  /**
   * Returns a file for a given path. Throws FileNotFoundException if the file doesn't exist.
   *
   * @param path The path of the file.
   */
  File getFileForPath(String path) throws FileNotFoundException;
}
