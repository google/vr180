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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.provider.MediaStore.Video;
import com.google.common.base.Optional;
import com.google.protobuf.ByteString;
import com.google.vr180.CameraApi.FileChecksum;
import com.google.vr180.CameraApi.Media;
import com.google.vr180.api.camerainterfaces.FileChecksumProvider;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.testhelpers.TestCameraCalibration;
import java.util.Date;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class AndroidMediaProviderTest {

  private static final String BASE_PATH = "/sdcard/DCIM/Camera";

  private Context mockContext;
  private ContentResolver mockContentResolver;
  private Cursor mockCursor;
  private FileChecksumProvider mockChecksumProvider;
  private StatusNotifier mockStatusNotifier;
  private AndroidMediaProvider mediaProvider;

  @Before
  public void setUp() throws Exception {
    mockContext = Mockito.spy(RuntimeEnvironment.application);
    mockContentResolver = Mockito.mock(ContentResolver.class);
    when(mockContext.getContentResolver()).thenReturn(mockContentResolver);
    mockCursor = Mockito.mock(Cursor.class);
    when(mockContentResolver.query(any(), any(), any(), any(), any())).thenReturn(mockCursor);

    mockChecksumProvider = Mockito.mock(FileChecksumProvider.class);
    mockStatusNotifier = Mockito.mock(StatusNotifier.class);
    StorageStatusProvider mockStorageStatusProvider = Mockito.mock(StorageStatusProvider.class);
    Mockito.when(
        mockStorageStatusProvider.getInternalStoragePath()).thenReturn(Optional.of(BASE_PATH));
    Mockito.when(
        mockStorageStatusProvider.getExternalStoragePath()).thenReturn(Optional.absent());
    Mockito.when(mockStorageStatusProvider.getWriteBasePath()).thenReturn(Optional.of(BASE_PATH));
    Mockito.when(mockStorageStatusProvider.isValidPath(Mockito.anyString())).thenReturn(true);
    mediaProvider =
        new AndroidMediaProvider(
            mockContext,
            mockStorageStatusProvider,
            mockChecksumProvider,
            TestCameraCalibration.TOP_BOTTOM_STEREO,
            mockStatusNotifier);
  }

  @Test
  public void testGetMediaCount() throws Exception {
    when(mockCursor.moveToFirst()).thenReturn(true);
    when(mockCursor.getInt(eq(0))).thenReturn(10);
    assertThat(mediaProvider.getMediaCount()).isEqualTo(10);
  }

  @Test
  public void testGetMedia_range() throws Exception {
    // Return 2 items.
    when(mockCursor.moveToNext()).thenReturn(true).thenReturn(true).thenReturn(false);

    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.DATA))).thenReturn(0);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.SIZE))).thenReturn(1);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.DATE_TAKEN))).thenReturn(2);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.DURATION))).thenReturn(3);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.WIDTH))).thenReturn(4);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.HEIGHT))).thenReturn(5);

    when(mockCursor.getString(eq(0))).thenReturn(BASE_PATH + "/test.jpg");
    when(mockCursor.getLong(eq(1))).thenReturn(1000L);
    when(mockCursor.getLong(eq(2))).thenReturn(1001L);
    when(mockCursor.getLong(eq(3))).thenReturn(1002L);
    when(mockCursor.getLong(eq(4))).thenReturn(1003L);
    when(mockCursor.getLong(eq(5))).thenReturn(1004L);

    when(mockChecksumProvider.getFileChecksum(any()))
        .thenReturn(
            FileChecksum.newBuilder()
                .setChecksum(ByteString.copyFrom(new byte[] {1, 2, 3}))
                .build());

    List<Media> media = mediaProvider.getMedia(0, 10);
    assertThat(media.size()).isEqualTo(2);

    assertThat(media.get(0).getFilename()).isEqualTo(BASE_PATH + "/test.jpg");
    assertThat(media.get(0).getSize()).isEqualTo(1000L);
    assertThat(media.get(0).getTimestamp()).isEqualTo(1001L);
    assertThat(media.get(0).getDuration()).isEqualTo(1002L);
    assertThat(media.get(0).getWidth()).isEqualTo(1003L);
    assertThat(media.get(0).getHeight()).isEqualTo(1004L);
    assertThat(media.get(1)).isEqualTo(media.get(0));
  }

  @Test
  public void testGetMedia_mediaStoreId() throws Exception {
    when(mockCursor.moveToNext()).thenReturn(true).thenReturn(false);

    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.DATA))).thenReturn(0);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.SIZE))).thenReturn(1);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.DATE_TAKEN))).thenReturn(2);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.DURATION))).thenReturn(3);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.WIDTH))).thenReturn(4);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns.HEIGHT))).thenReturn(5);
    when(mockCursor.getColumnIndex(eq(Video.VideoColumns._ID))).thenReturn(6);

    when(mockCursor.getString(eq(0))).thenReturn(BASE_PATH + "/test.jpg");
    when(mockCursor.getLong(eq(1))).thenReturn(1000L);
    when(mockCursor.getLong(eq(2))).thenReturn(1001L);
    when(mockCursor.getLong(eq(3))).thenReturn(1002L);
    when(mockCursor.getLong(eq(4))).thenReturn(1003L);
    when(mockCursor.getLong(eq(5))).thenReturn(1004L);
    when(mockCursor.getLong(eq(6))).thenReturn(1234L);

    when(mockChecksumProvider.getFileChecksum(any()))
        .thenReturn(
            FileChecksum.newBuilder()
                .setChecksum(ByteString.copyFrom(new byte[] {1, 2, 3}))
                .build());

    Media media = mediaProvider.getMedia(1234L);

    assertThat(media.getFilename()).isEqualTo(BASE_PATH + "/test.jpg");
    assertThat(media.getSize()).isEqualTo(1000L);
    assertThat(media.getTimestamp()).isEqualTo(1001L);
    assertThat(media.getDuration()).isEqualTo(1002L);
    assertThat(media.getWidth()).isEqualTo(1003L);
    assertThat(media.getHeight()).isEqualTo(1004L);
  }

  @Test
  public void testGetMedia_mediaStoreId_notFound() throws Exception {
    when(mockCursor.moveToNext()).thenReturn(false);
    Media media = mediaProvider.getMedia(1234L);
    assertThat(media).isNull();
  }

  @Test
  public void testLastModifiedAndNotification() throws Exception {
    ArgumentCaptor<ContentObserver> contentObserverCaptor =
        ArgumentCaptor.forClass(ContentObserver.class);
    Mockito.verify(mockContentResolver, times(2))
        .registerContentObserver(any(), anyBoolean(), contentObserverCaptor.capture());

    Date lastModified1 = mediaProvider.getLastModified();

    ContentObserver contentObserver = contentObserverCaptor.getValue();
    contentObserver.onChange(false);

    Date lastModified2 = mediaProvider.getLastModified();
    Assert.assertTrue(lastModified2.after(lastModified1));
    Mockito.verify(mockStatusNotifier).notifyStatusChanged();
  }

  @Test
  public void testOnMediaStateChanged() throws Exception {
    mediaProvider.onMediaStateChanged();
    Mockito.verify(mockStatusNotifier).notifyStatusChanged();
  }
}
