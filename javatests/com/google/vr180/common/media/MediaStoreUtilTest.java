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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import com.google.vr180.testhelpers.FakeMediaProvider;
import com.google.vr180.testhelpers.shadows.ShadowMediaMetadataRetriever;
import java.io.File;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Tests for MediastoreUtil. */
@RunWith(RobolectricTestRunner.class)
@Config(
    manifest = Config.NONE,
    shadows = {ShadowMediaMetadataRetriever.class})
public class MediaStoreUtilTest {

  private static final long MS_PER_SEC = 1000;
  private static final long TEST_DATE_TAKEN = 1000000000L;
  private static final int TEST_DURATION = 1000;
  private static final int TEST_WIDTH = 320;
  private static final int TEST_HEIGHT = 240;
  private Context context;

  @Mock private MediaMetadataRetriever mockMediaMetadataRetriever;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    context = RuntimeEnvironment.application.getApplicationContext();
    Robolectric.setupContentProvider(FakeMediaProvider.class, FakeMediaProvider.AUTHORITY);
    shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypMapping("jpg", "image/jpeg");
    shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypMapping("png", "image/png");
    shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypMapping("mp4", "video/mp4");
    ShadowMediaMetadataRetriever.setMockInstance(mockMediaMetadataRetriever);
  }

  @Test
  public void testNewFile() throws Exception {
    long fileSize = 1000;
    File file = createFile(fileSize, ".png");
    Uri uri = MediaStoreUtil.updateFile(context, file);
    Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
    cursor.moveToFirst();

    long dateModifiedMilliSeconds = file.lastModified();
    long dateModifiedSeconds = dateModifiedMilliSeconds / MS_PER_SEC;
    assertEquals(
        file.getAbsolutePath(),
        cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA)));
    assertEquals(
        file.getName(), cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.TITLE)));
    assertEquals(
        file.getName(),
        cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)));
    assertEquals(fileSize, cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)));
    assertEquals(
        dateModifiedSeconds,
        cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)));
    assertEquals(
        "image/png", cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)));
    assertEquals("100", cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)));
    assertEquals("100", cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)));
  }

  @Test
  public void testVideoMetadata() throws Exception {
    long fileSize = 1000;
    File file = createFile(fileSize, ".mp4");
    when(mockMediaMetadataRetriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
        .thenReturn(Integer.toString(TEST_WIDTH));
    when(mockMediaMetadataRetriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
        .thenReturn(Integer.toString(TEST_HEIGHT));
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'.000Z'");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    when(mockMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
        .thenReturn(dateFormat.format(new Date(TEST_DATE_TAKEN)));
    when(mockMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
        .thenReturn(Integer.toString(TEST_DURATION));

    Uri uri = MediaStoreUtil.updateFile(context, file);
    Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
    cursor.moveToFirst();

    assertThat(cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)))
        .isEqualTo(TEST_WIDTH);
    assertThat(cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)))
        .isEqualTo(TEST_HEIGHT);
    assertThat(cursor.getLong(cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)))
        .isEqualTo(TEST_DURATION);
    assertThat(cursor.getLong(cursor.getColumnIndex(MediaStore.Video.VideoColumns.DATE_TAKEN)))
        .isEqualTo(TEST_DATE_TAKEN);
  }

  @Test
  public void testUpdateDate() throws Exception {
    long fileSize = 1000;
    File file = createFile(fileSize, ".jpg");
    Uri uri = MediaStoreUtil.updateFile(context, file);
    Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
    cursor.moveToFirst();

    long dateModifiedMilliSeconds = file.lastModified();
    long dateModifiedSeconds = dateModifiedMilliSeconds / MS_PER_SEC;
    long dateAddedSeconds =
        cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED));
    assertEquals(
        dateModifiedSeconds,
        cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)));
    assertEquals(
        "image/jpeg", cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)));

    file.setLastModified(file.lastModified() + 1500);
    MediaStoreUtil.updateFile(context, file);
    cursor = context.getContentResolver().query(uri, null, null, null, null);
    cursor.moveToFirst();
    assertEquals(
        dateAddedSeconds,
        cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)));
    assertEquals(
        dateModifiedSeconds + 1,
        cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)));
  }

  @Test
  public void testDeleteFile() throws Exception {
    long fileSize = 1000;
    File file = createFile(fileSize, ".jpg");
    Uri uri = MediaStoreUtil.updateFile(context, file);
    Cursor cursor =
        context
            .getContentResolver()
            .query(MediaStore.Files.getContentUri("external"), null, null, null, null);
    assertEquals(1, cursor.getCount());

    assertTrue(MediaStoreUtil.deleteFile(context, file));
    cursor = context.getContentResolver().query(uri, null, null, null, null);
    assertEquals(0, cursor.getCount());
  }

  @Test
  public void testDeleteFiles() throws Exception {
    File file1 = createFile(1000 /* fileSize */, ".jpg");
    Uri uri1 = MediaStoreUtil.updateFile(context, file1);

    File file2 = createFile(1500 /* fileSize */, ".jpg");
    Uri uri2 = MediaStoreUtil.updateFile(context, file2);
    Cursor cursor =
        context
            .getContentResolver()
            .query(MediaStore.Files.getContentUri("external"), null, null, null, null);
    assertEquals(2, cursor.getCount());

    cursor.moveToFirst();
    long file1Id = cursor.getLong(cursor.getColumnIndex("_id"));
    cursor.moveToNext();
    long file2Id = cursor.getLong(cursor.getColumnIndex("_id"));

    assertTrue(MediaStoreUtil.deleteFiles(context, Arrays.asList(file1Id, file2Id)));
    cursor = context.getContentResolver().query(uri1, null, null, null, null);
    assertEquals(0, cursor.getCount());
    cursor = context.getContentResolver().query(uri2, null, null, null, null);
    assertEquals(0, cursor.getCount());
  }

  @Test
  public void testGetMimeType() throws Exception {
    assertEquals("video/mp4", MediaStoreUtil.getMimeType("foo_video.mp4"));
    // Uppercase extension.
    assertEquals("video/mp4", MediaStoreUtil.getMimeType("foo_video.MP4"));
    // Invalid URL that might still be a file with an extension.
    assertEquals("video/mp4", MediaStoreUtil.getMimeType("I'm a.really bad URL,...mp4"));
    // VR images should be recognized as jpegs.
    assertEquals("image/jpeg", MediaStoreUtil.getMimeType("vr_photo.vr.jpg"));

    // URL ending with a . shouldn't crash anything.
    assertNull(MediaStoreUtil.getMimeType("I end with a ."));
    assertNull(MediaStoreUtil.getMimeType("."));
    // URL with no . shouldn't crash anything.
    assertNull(MediaStoreUtil.getMimeType("file_with_no_extension"));
  }

  private static File createFile(long size, String suffix) throws Exception {
    File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    File file = File.createTempFile("MediastoreTest", suffix, path);
    RandomAccessFile randomFile = new RandomAccessFile(file, "rw");
    randomFile.setLength(size);
    randomFile.close();
    return file;
  }
}
