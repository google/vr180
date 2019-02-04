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

package com.google.vr180.common.media;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;
import com.google.vr180.common.logging.Log;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Helper functions to access and update the MediaStore.Files database.
 */
public class MediaStoreUtil {
  private static final String TAG = "MediaStoreUtil";
  private static final Uri EXTERNAL_FILE_CONTENT_URI = MediaStore.Files.getContentUri("external");
  private static final long MS_PER_SEC = 1000;

  /**
   * Updates the MediaStore.Files file database with the updated info of the given file, and sets
   * the description of the mediastore entry to our custom description.
   * <p>
   * The function is not asynchronous as the one in MediaScanner, but it is pretty fast,
   * less than 100 ms in a HTC One.
   */
  public static Uri updateFile(Context context, File file) {
    String mime = getMimeType(file.getName());
    long dateModifiedMilliSeconds = file.lastModified();
    long dateModifiedSeconds = dateModifiedMilliSeconds / MS_PER_SEC;

    ContentValues values = new ContentValues();
    values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
    values.put(MediaStore.MediaColumns.TITLE, file.getName());
    values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
    values.put(MediaStore.MediaColumns.SIZE, file.length());
    values.put(MediaStore.MediaColumns.DATE_MODIFIED, dateModifiedSeconds);
    if (mime != null) {
      values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
    }
    MediaInfo.extractMediaStoreMetadata(file, mime, values);

    ContentResolver contentResolver = context.getContentResolver();
    // Check if the file is already in the database.
    Uri uri = getFileContentUri(context, file.getAbsolutePath());
    if (uri == null) {
      // The file is not in the database, so insert it.
      long dateAddedSeconds = System.currentTimeMillis() / MS_PER_SEC;
      values.put(MediaStore.MediaColumns.DATE_ADDED, dateAddedSeconds);
      return contentResolver.insert(EXTERNAL_FILE_CONTENT_URI, values);
    }

    // The file is in the database, so update its entry.
    try {
      contentResolver.update(uri, values, null, null);
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
    }
    return uri;
  }

  /**
   * Deletes a file from the MediaStore database. It returns true on success.
   */
  public static boolean deleteFile(Context context, File file) {
    // Check if the file is already in the database.
    Uri uri = getFileContentUri(context, file.getAbsolutePath());
    if (uri == null) {
      Log.e(TAG, "Cannot find the given file in the mediastore");
      return false;
    }
    return deleteFile(context, uri);
  }

  /**
   * Deletes a file from the MediaStore database. It returns true on success.
   *
   * @param context The android context
   * @param uri The media store content uri for the file
   * @return true if the file was deleted, false if an error occurred
   */
  public static boolean deleteFile(Context context, Uri uri) {
    ContentResolver contentResolver = context.getContentResolver();
    if (contentResolver.delete(uri, null, null) == 0) {
      Log.e(TAG, "Could not delete uri " + uri.toString());
      return false;
    }
    return true;
  }

  /**
   * Delete files from the MediaStore database by ID. It returns true on success.
   */
  public static boolean deleteFiles(Context context, List<Long> ids) {
    ContentResolver contentResolver = context.getContentResolver();
    String idsString = TextUtils.join(", ", ids);
    String where = MediaStore.MediaColumns._ID + " IN (" + idsString + ")";
    if (contentResolver.delete(EXTERNAL_FILE_CONTENT_URI, where, null) == 0) {
      Log.e(TAG, "Could not delete file ids: " + idsString);
      return false;
    }
    return true;
  }

  /**
   * Returns the MediaStore ID given the URI path to the file, or -1 if the path is null.
   */
  public static long getMediastoreId(Context context, String uriPath) {
    long mediastoreId = -1;
    Uri mediastoreUri = MediaStoreUtil.getFileContentUri(context, uriPath);
    if (mediastoreUri == null) {
      Log.e(TAG, "No MediaStore ID found; MediaStore URI was null");
      return mediastoreId;
    }

    try {
      mediastoreId = ContentUris.parseId(mediastoreUri);
    } catch (NumberFormatException | UnsupportedOperationException e) {
      Log.e(TAG, "Could not parse URI ID: ", e);
    }
    return mediastoreId;
  }

  /**
   * Returns the MediaStore {@link Uri} given the id of the given item.
   * 
   * Does not validate it's existence (or query media store at all).
   **/
  public static Uri getFileContentUri(long mediaId) {
    return ContentUris.withAppendedId(EXTERNAL_FILE_CONTENT_URI, mediaId);
  }

  /**
   * Returns the MediaStore {@link Uri} given the path to the file, or null if it cannot be found.
   */
  public static Uri getFileContentUri(Context context, String url) {
    // Prevent an IllegalArgumentException from android.database.DatabaseUtils if the url is null.
    if (url == null) {
      return null;
    }

    String[] projection = {MediaStore.MediaColumns._ID};
    String selection = MediaStore.MediaColumns.DATA + " LIKE ?";
    Cursor cursor;
    try {
      cursor = context.getContentResolver().query(
          EXTERNAL_FILE_CONTENT_URI,
          projection, selection, new String[]{url}, null);
    } catch (java.lang.SecurityException e) {
      cursor = null;
    }
    if (cursor == null) {
      return null;
    }
    if (!cursor.moveToFirst()) {
      cursor.close();
      return null;
    }
    Uri uri = Uri.withAppendedPath(EXTERNAL_FILE_CONTENT_URI,
        cursor.getString(cursor.getColumnIndex(projection[0])));
    cursor.close();
    return uri;
  }

  /**
   * Returns the path to a file from its MediaStore {@link Uri}, or null if it cannot be found.
   */
  public static String getFilePath(Context context, Uri contentUri) {
    String[] projection = {MediaStore.MediaColumns.DATA};
    Cursor cursor;
    try {
      cursor = context.getContentResolver().query(contentUri, projection, null, null, null);
    } catch (java.lang.SecurityException e) {
      cursor = null;
    }
    if (cursor == null) {
      return null;
    }

    try {
      if (!cursor.moveToFirst()) {
        return null;
      }
      return cursor.getString(0);
    } finally {
      cursor.close();
    }
  }

  /**
   * Returns the mime type of the given url based on its file extension.
   */
  public static String getMimeType(String url) {
    String type = null;
    String extension = MimeTypeMap.getFileExtensionFromUrl(url);

    if ((extension == null || extension.isEmpty()) && url != null) {
      // WebKit's MimeTypeMap didn't like the url, so do a last-ditch manual find of the extension.
      // Don't count the last character as a possible location for the dot.
      int dotIndex = url.lastIndexOf(".", url.length() - 2);
      if (dotIndex >= 0) {
        // Take everything after the dot.
        extension = url.substring(dotIndex + 1);
      }
    }

    if (extension != null) {
      type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.US));
    }

    return type;
  }
}
