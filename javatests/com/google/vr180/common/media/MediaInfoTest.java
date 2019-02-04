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

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.media.ExifInterface;
import android.provider.MediaStore;
import com.google.vr180.testhelpers.shadows.ShadowExifInterface;
import java.io.File;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(
    manifest = Config.NONE,
    shadows = {ShadowExifInterface.class})
public final class MediaInfoTest {
  @Before
  public void setUp() {
    ShadowExifInterface.clearAttributes();
    TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+8")); // PST without daylight savings.
  }

  @Test
  public void testExtractMediaStoreMetadataWithVideo() {
    ContentValues values = new ContentValues();
    MediaInfo.extractMediaStoreMetadata(
        new File("/foo/20171117-164454735.mp4"), "video/mpeg", values);
    assertEquals(1510965894735L, values.get(MediaStore.Video.VideoColumns.DATE_TAKEN));
  }

  @Test
  public void testExtractMediaStoreMetadataWithVideoNoDate() {
    ContentValues values = new ContentValues();
    MediaInfo.extractMediaStoreMetadata(
        new File("/foo/20171117164454735.mp4"), "video/mpeg", values);
    assertEquals(null, values.get(MediaStore.Video.VideoColumns.DATE_TAKEN));
  }

  @Test
  public void testExtractMediaStoreMetadataWithImage() {
    ContentValues values = new ContentValues();
    MediaInfo.extractMediaStoreMetadata(
        new File("/foo/20171117-164454735.vr.jpg"), "image/jpg", values);
    assertEquals(1510965894735L, values.get(MediaStore.Images.ImageColumns.DATE_TAKEN));
  }

  @Test
  public void testExtractMediaStoreMetadataWithImageWrongDateFormat() {
    ContentValues values = new ContentValues();
    MediaInfo.extractMediaStoreMetadata(
        new File("/foo/20171117164454735.vr.jpg"), "image/jpg", values);
    assertEquals(null, values.get(MediaStore.Images.ImageColumns.DATE_TAKEN));
  }

  @Test
  public void testExtractMediaStoreMetadataWithExifDateTime() {
    ShadowExifInterface.setAttribute(ExifInterface.TAG_DATETIME, "2017:11:17 16:44:54");
    ContentValues values = new ContentValues();
    MediaInfo.extractMediaStoreMetadata(new File("/foo/does_not_exist.jpg"), "image/jpg", values);
    assertEquals(1510965894000L, values.get(MediaStore.Images.ImageColumns.DATE_TAKEN));
  }

  @Test
  public void testExtractMediaStoreMetadataWithExifDateTimeOriginal() {
    ShadowExifInterface.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2017:11:17 16:44:54");
    ContentValues values = new ContentValues();
    MediaInfo.extractMediaStoreMetadata(new File("/foo/does_not_exist.jpg"), "image/jpg", values);
    assertEquals(1510965894000L, values.get(MediaStore.Images.ImageColumns.DATE_TAKEN));
  }

  @Test
  public void testExtractMediaStoreMetadataWithExifDateTimeDigitized() {
    ShadowExifInterface.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, "2017:11:17 16:44:54");
    ContentValues values = new ContentValues();
    MediaInfo.extractMediaStoreMetadata(new File("/foo/does_not_exist.jpg"), "image/jpg", values);
    assertEquals(1510965894000L, values.get(MediaStore.Images.ImageColumns.DATE_TAKEN));
  }
}
