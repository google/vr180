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

import android.content.ContentResolver;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Video;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.vr180.CameraApi.FileChecksum;
import com.google.vr180.api.camerainterfaces.FileProvider;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.Date;
import java.util.GregorianCalendar;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.fakes.RoboCursor;

@RunWith(RobolectricTestRunner.class)
public final class CachedFileChecksumProviderTest {

  private static final String TEST_PATH = "file.jpg";
  private static final byte[] TEST_DATA = new byte[] {1, 2, 3, 4, 5};
  private static final byte[] TEST_DATA2 = new byte[] {0, 0, 0};

  private static final Date DATE_MODIFIED_1 = new GregorianCalendar(2017, 5, 8).getTime();
  private static final Date DATE_MODIFIED_2 = new GregorianCalendar(2017, 5, 9).getTime();

  private FileProvider mockFileProvider;

  @Before
  public void setUp() throws Exception {
    mockFileProvider = Mockito.mock(FileProvider.class);
    // Return a new input stream instance for each openFile call.
    Mockito.when(mockFileProvider.openFile(Mockito.eq(TEST_PATH)))
        .thenAnswer((invocation) -> new ByteArrayInputStream(TEST_DATA));
    Mockito.when(mockFileProvider.getLastModified(Mockito.eq(TEST_PATH)))
        .thenReturn(DATE_MODIFIED_1);

    createFakeMediaStoreEntry("123", TEST_PATH);
  }

  private void createFakeMediaStoreEntry(String id, String filename) {
    ContentResolver contentResolver = RuntimeEnvironment.application.getContentResolver();
    RoboCursor mediaStoreCursor = new RoboCursor();
    mediaStoreCursor.setColumnNames(
        Lists.asList(Video.Media.DATA, Video.Media.DESCRIPTION, new String[] {}));
    mediaStoreCursor.setResults(new Object[][] {new String[] {filename, null}});
    Shadows.shadowOf(contentResolver)
        .setCursor(
            Files.getContentUri("external").buildUpon().appendPath(id).build(), mediaStoreCursor);
  }

  @Test
  public void testChecksumCorrect() throws Exception {
    CachedFileChecksumProvider checksumProvider =
        new CachedFileChecksumProvider(RuntimeEnvironment.application, mockFileProvider);
    checksumProvider.setComputationExecutor(MoreExecutors.directExecutor());

    // First call is non-blocking (since checksum isn't cached).
    assertThat(checksumProvider.getFileChecksum(TEST_PATH)).isNull();

    FileChecksum checksum = checksumProvider.getFileChecksum(TEST_PATH);
    assertThat(checksum.getChecksumType()).isEqualTo(FileChecksum.ChecksumType.SHA1);

    MessageDigest digest = MessageDigest.getInstance("SHA");
    Assert.assertArrayEquals(digest.digest(TEST_DATA), checksum.getChecksum().toByteArray());
    Mockito.verify(mockFileProvider, Mockito.times(1)).openFile(Mockito.any());
  }

  @Test
  public void testFileModified() throws Exception {
    CachedFileChecksumProvider checksumProvider =
        new CachedFileChecksumProvider(RuntimeEnvironment.application, mockFileProvider);
    checksumProvider.setComputationExecutor(MoreExecutors.directExecutor());

    // Compute once to initialize the cache.
    checksumProvider.getFileChecksum(TEST_PATH);
    FileChecksum checksum = checksumProvider.getFileChecksum(TEST_PATH);

    // "Modify" the file.
    Mockito.when(mockFileProvider.openFile(Mockito.eq(TEST_PATH)))
        .thenReturn(new ByteArrayInputStream(TEST_DATA2));
    Mockito.when(mockFileProvider.getLastModified(Mockito.eq(TEST_PATH)))
        .thenReturn(DATE_MODIFIED_2);

    checksumProvider.getFileChecksum(TEST_PATH);
    FileChecksum checksum2 = checksumProvider.getFileChecksum(TEST_PATH);
    Assert.assertNotEquals(checksum, checksum2);
    MessageDigest digest = MessageDigest.getInstance("SHA");
    Assert.assertArrayEquals(digest.digest(TEST_DATA), checksum.getChecksum().toByteArray());
    Assert.assertArrayEquals(digest.digest(TEST_DATA2), checksum2.getChecksum().toByteArray());
  }
}
