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

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.provider.MediaStore;
import com.google.vr180.common.logging.Log;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** Helper class to retrieve metadata of media files. */
public class MediaInfo {
  private static final String TAG = "MediaInfo";
  private static final String IMAGE_MIME = "image/";
  private static final String VIDEO_MIME = "video/";
  private static final String EXIF_TAG_DATETIME_DIGITIZED = "DateTimeDigitized";
  private static final String EXIF_TAG_DATETIME_ORIGINAL = "DateTimeOriginal";

  private static final ThreadLocal<SimpleDateFormat> METADATA_DATE_FORMATTER =
      new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
          return createFormatWithUtcTimeZone("yyyyMMdd'T'HHmmss'.000Z'");
        }
      };
  private static final ThreadLocal<SimpleDateFormat> FILENAME_DATE_FORMATTER =
      new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
          return createFormatWithLocalTimeZone("yyyyMMdd'-'HHmmssSSS");
        }
      };
  private static final ThreadLocal<SimpleDateFormat> EXIF_DATE_FORMATTER =
      new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
          return createFormatWithLocalTimeZone("yyyy:MM:dd HH:mm:ss");
        }
      };

  /**
   * Extracts metadata from the provided file and places it into the correct MediaStore columns.
   *
   * <p>This currently attempts to extract WIDTH, HEIGHT, DATE_TAKEN, and DURATION.
   *
   * @param file The file to extract info from.
   * @param mime The mime type of the file.
   * @param values A ContentValues structure which receives the metadata.
   */
  public static void extractMediaStoreMetadata(File file, String mime, ContentValues values) {
    if (file == null) {
      return;
    }

    if (mime != null) {
      if (mime.startsWith(IMAGE_MIME)) {
        extractBitmapMetadata(file, values);
        return;
      }
      if (mime.startsWith(VIDEO_MIME)) {
        extractVideoMetadata(file, values);
        return;
      }
    }

    // Try both image and video mime types.
    extractBitmapMetadata(file, values);
    extractVideoMetadata(file, values);
  }

  /** Populate the values with the size and date of the video file, if available. */
  private static void extractVideoMetadata(File file, ContentValues values) {
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      retriever.setDataSource(file.getPath());

      // Extract frame size.
      String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
      String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
      if (width != null && height != null) {
        values.put(MediaStore.MediaColumns.WIDTH, Integer.valueOf(width));
        values.put(MediaStore.MediaColumns.HEIGHT, Integer.valueOf(height));
      }

      Long dateTaken =
          parseDateWithFormatter(
              retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE),
              METADATA_DATE_FORMATTER.get());
      if (dateTaken == null) {
        dateTaken = parseDateWithFormatter(file.getName(), FILENAME_DATE_FORMATTER.get());
      }
      if (dateTaken != null) {
        values.put(MediaStore.Video.VideoColumns.DATE_TAKEN, dateTaken);
      }

      String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      if (duration != null) {
        values.put(MediaStore.Video.VideoColumns.DURATION, Integer.parseInt(duration));
      }
    } catch (Exception e) {
      Log.e(TAG, "Unable to parse video metadata from: " + file.getAbsolutePath(), e);
    } finally {
      retriever.release();
    }
  }

  /** Populate the values with the size and date of the {@link Bitmap}, if available. */
  private static void extractBitmapMetadata(File file, ContentValues values) {
    if (file == null) {
      return;
    }

    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), options);

    if (options.outWidth < 1 || options.outHeight < 1) {
      return;
    }
    values.put(MediaStore.MediaColumns.WIDTH, options.outWidth);
    values.put(MediaStore.MediaColumns.HEIGHT, options.outHeight);

    Long dateTaken = null;
    try {
      ExifInterface exif = new ExifInterface(file.getAbsolutePath());
      dateTaken =
          parseDateWithFormatter(
              exif.getAttribute(ExifInterface.TAG_DATETIME), EXIF_DATE_FORMATTER.get());
      if (dateTaken == null) {
        dateTaken =
            parseDateWithFormatter(
                exif.getAttribute(EXIF_TAG_DATETIME_ORIGINAL), EXIF_DATE_FORMATTER.get());
      }
      if (dateTaken == null) {
        dateTaken =
            parseDateWithFormatter(
                exif.getAttribute(EXIF_TAG_DATETIME_DIGITIZED), EXIF_DATE_FORMATTER.get());
      }
    } catch (Exception e) {
      Log.e(TAG, "Unable to parse exif data from: " + file.getAbsolutePath(), e);
    }
    if (dateTaken == null) {
      dateTaken = parseDateWithFormatter(file.getName(), FILENAME_DATE_FORMATTER.get());
    }
    if (dateTaken != null) {
      values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, dateTaken);
    }
  }

  private static Long parseDateWithFormatter(String name, SimpleDateFormat formatter) {
    if (name == null) {
      return null;
    }
    try {
      return formatter.parse(name).getTime();
    } catch (ParseException e) {
      Log.e(TAG, "Unable to parse date from name: " + name, e);
      return null;
    }
  }

  private static SimpleDateFormat createFormatWithUtcTimeZone(String dateFormat) {
    SimpleDateFormat format = new SimpleDateFormat(dateFormat, Locale.US);
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    return format;
  }

  private static SimpleDateFormat createFormatWithLocalTimeZone(String dateFormat) {
    return new SimpleDateFormat(dateFormat, Locale.US);
  }
}
