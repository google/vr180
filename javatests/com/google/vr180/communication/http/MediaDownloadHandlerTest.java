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

package com.google.vr180.communication.http;

import static com.google.common.truth.Truth.assertThat;

import android.os.Environment;
import com.google.common.base.Optional;
import com.google.common.io.ByteStreams;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.api.camerainterfaces.FileProvider;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.api.implementations.AndroidFileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class MediaDownloadHandlerTest {
  private static final byte[] testData = "test".getBytes(StandardCharsets.UTF_8);
  private final MediaDownloadHandler donwloadHandler;
  private final FileProvider mockFileProvider;
  private final String basePath = Environment.getExternalStorageDirectory().getAbsolutePath();
  private final String filePath = basePath + "/test.txt";
  private final String missingPath = basePath + "/missing.txt";

  public MediaDownloadHandlerTest() throws Exception {
    File file = new File(filePath);
    FileOutputStream outputStream = new FileOutputStream(file);
    outputStream.write(testData);
    outputStream.close();

    // Create the handler.
    AuthorizationValidator mockAuthValidator =
        Mockito.spy(new AuthorizationValidator(Mockito.mock(CameraSettings.class)));
    Mockito.doReturn(true).when(mockAuthValidator).isValidRequest(Mockito.any(), Mockito.any());
    StorageStatusProvider mockStorageStatusProvider = Mockito.mock(StorageStatusProvider.class);
    Mockito.when(
        mockStorageStatusProvider.getInternalStoragePath()).thenReturn(Optional.of(basePath));
    Mockito.when(
        mockStorageStatusProvider.getExternalStoragePath()).thenReturn(Optional.absent());
    Mockito.when(mockStorageStatusProvider.getWriteBasePath()).thenReturn(Optional.of(basePath));
    Mockito.when(mockStorageStatusProvider.isValidPath(Mockito.anyString())).thenReturn(true);
    mockFileProvider =
        Mockito.spy(
            new AndroidFileProvider(RuntimeEnvironment.application, mockStorageStatusProvider));
    donwloadHandler = new MediaDownloadHandler(mockFileProvider, mockAuthValidator);
  }

  @Test
  public void testSimpleDownload() throws Exception {
    BasicHttpRequest request = new BasicHttpRequest("GET", "/media/" + filePath);
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);

    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
    byte[] body = ByteStreams.toByteArray(response.getEntity().getContent());
    assertThat(testData).isEqualTo(body);
  }

  @Test
  public void testRangeDownloadStart() throws Exception {
    BasicHttpRequest request = new BasicHttpRequest("GET", "/media/" + filePath);
    request.addHeader("Range", "bytes=0-1");
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);

    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(206);
    byte[] body = ByteStreams.toByteArray(response.getEntity().getContent());
    assertThat(Arrays.copyOfRange(testData, 0, 2)).isEqualTo(body);
    assertThat(response.getFirstHeader("Content-Range").getValue()).isEqualTo("bytes 0-1/4");
  }

  @Test
  public void testRangeDownloadEnd() throws Exception {
    BasicHttpRequest request = new BasicHttpRequest("GET", "/media/" + filePath);
    request.addHeader("Range", "bytes=2-10");
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);

    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(206);
    byte[] body = ByteStreams.toByteArray(response.getEntity().getContent());
    assertThat(Arrays.copyOfRange(testData, 2, 4)).isEqualTo(body);
    assertThat(response.getFirstHeader("Content-Range").getValue()).isEqualTo("bytes 2-3/4");
  }

  @Test
  public void testUnboundedRangeHeader() throws Exception {
    BasicHttpRequest request = new BasicHttpRequest("GET", "/media/" + filePath);
    request.addHeader("Range", "bytes=1-");
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);

    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(206);
    byte[] body = ByteStreams.toByteArray(response.getEntity().getContent());
    assertThat(Arrays.copyOfRange(testData, 1, 4)).isEqualTo(body);
    assertThat(response.getFirstHeader("Content-Range").getValue()).isEqualTo("bytes 1-3/4");
  }

  @Test
  public void testInvalidRangeHeader() throws Exception {
    BasicHttpRequest request = new BasicHttpRequest("GET", "/media/" + filePath);
    request.addHeader("Range", "bytes=adfh");
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);

    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(400);
  }

  @Test
  public void testUnsatisfiableRangeHeader() throws Exception {
    BasicHttpRequest request = new BasicHttpRequest("GET", "/media/" + filePath);
    request.addHeader("Range", "bytes=200-300");
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(416);
  }

  @Test
  public void testRangeStartAtLastByteHeader() throws Exception {
    String satisfiableRange = String.format("bytes=%d-300", testData.length - 1);
    BasicHttpRequest request = new BasicHttpRequest("GET", "/media/" + filePath);
    request.addHeader("Range", satisfiableRange);
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(206);
  }

  @Test
  public void testRangeAfterLastByteHeader() throws Exception {
    String unsatisfiableRange = String.format("bytes=%d-300", testData.length);
    BasicHttpRequest request = new BasicHttpRequest("GET", "/media/" + filePath);
    request.addHeader("Range", unsatisfiableRange);
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(416);
  }

  @Test
  public void testMissingFile() throws Exception {
    BasicHttpRequest request = new BasicHttpRequest("GET", "/media/" + missingPath);
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);

    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(404);
  }

  @Test
  public void testDelete() throws Exception {
    // Delete the file.
    BasicHttpRequest request = new BasicHttpRequest("DELETE", "/media/" + filePath);
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(204);
    // Check that delete was called. Unfortunately the file doesn't actually get deleted because
    // ContentResolver is a stub on robolectric.
    Mockito.verify(mockFileProvider).deleteFile(filePath);
  }

  @Test
  public void testDeleteMissingFile() throws Exception {
    BasicHttpRequest request = new BasicHttpRequest("DELETE", "/media/" + missingPath);
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    donwloadHandler.handle(request, response, null);
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(404);
  }
}
