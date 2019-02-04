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

import com.google.common.io.ByteStreams;
import com.google.vr180.api.camerainterfaces.FileProvider;
import com.google.vr180.common.IoUtils;
import com.google.vr180.common.logging.Log;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

/** Hander that implements the file download "/media/[filename]" endpoint. */
public class MediaDownloadHandler implements HttpRequestHandler {
  public static final String PREFIX = "/media/";

  private static final String TAG = "MediaDownloadHandler";
  private static final String CONTENT_RANGE_FORMAT = "bytes %d-%d/%d";
  private static final String RANGE = "Range";
  private static final String CONTENT_RANGE = "Content-Range";

  private final FileProvider fileProvider;
  private final AuthorizationValidator authorizationValidator;

  public MediaDownloadHandler(
      FileProvider fileProvider, AuthorizationValidator authorizationValidator) {
    this.fileProvider = fileProvider;
    this.authorizationValidator = authorizationValidator;
  }

  @Override
  public void handle(HttpRequest request, HttpResponse response, HttpContext context) {
    if (!authorizationValidator.isValidRequest(request, null /* requestBody */)) {
      authorizationValidator.setUnauthorizedResponse(response);
      return;
    }

    String requestPath = request.getRequestLine().getUri();
    if (!requestPath.startsWith(PREFIX)) {
      Log.w(TAG, "Invalid prefix sent to MediaDownloadHandler: " + requestPath);
      response.setStatusCode(400);
      return;
    }

    if ("GET".equalsIgnoreCase(request.getRequestLine().getMethod())) {
      handleGetRequest(request, response);
    } else if ("DELETE".equalsIgnoreCase(request.getRequestLine().getMethod())) {
      handleDeleteRequest(request, response);
    } else {
      response.setStatusCode(400);
    }
  }

  private void handleGetRequest(HttpRequest request, HttpResponse response) {
    String path = getPathFromRequest(request);
    InputStream resultStream;
    try {
      resultStream = fileProvider.openFile(path);
    } catch (FileNotFoundException e) {
      response.setStatusCode(404);
      return;
    }

    // If the client specified a range header, handle it.
    long length = -1;
    Header rangeHeader = request.getFirstHeader(RANGE);
    if (rangeHeader != null && rangeHeader.getValue() != null) {
      ByteRange range;
      try {
        range = ByteRange.parse(rangeHeader.getValue());
      } catch (ByteRange.RangeFormatException e) {
        response.setStatusCode(400);
        response.setReasonPhrase("Invalid range requested");
        IoUtils.closeSilently(resultStream);
        return;
      }
      long fileSize = fileProvider.getFileSize(path);
      long start = range.getStart();
      long end = range.hasEnd() ? Math.min(range.getEnd(), fileSize - 1) : fileSize - 1;
      length = end - start + 1;
      Log.d(TAG, "Range header parsed : " + start + "-" + end + "=" + length);
      if (start >= fileSize) {
        response.setStatusCode(416);
        response.setReasonPhrase("Range not satisfiable");
        IoUtils.closeSilently(resultStream);
        return;
      }
      try {
        skipBytes(resultStream, start);
      } catch (IOException e) {
        Log.e(TAG, "Unhandled error in media download request.", e);
        response.setStatusCode(500);
        IoUtils.closeSilently(resultStream);
        return;
      }
      resultStream = ByteStreams.limit(resultStream, length);
      String contentRange = String.format(CONTENT_RANGE_FORMAT, start, end, fileSize);
      response.setStatusCode(206);
      response.addHeader(CONTENT_RANGE, contentRange);
    }

    InputStream finalResultStream = resultStream;
    InputStreamEntity entity = new InputStreamEntity(finalResultStream, length) {
      @Override
      public void writeTo(OutputStream os) throws IOException {
        try {
          super.writeTo(os);
        } finally {
          finalResultStream.close();
        }
      }
    };
    entity.setContentType("application/octet-stream");
    response.setEntity(entity);
  }

  private void handleDeleteRequest(HttpRequest request, HttpResponse response) {
    String path = getPathFromRequest(request);
    try {
      fileProvider.deleteFile(path);
      response.setStatusCode(204);
      response.setReasonPhrase("No Content");
    } catch (FileNotFoundException e) {
      response.setStatusCode(404);
      response.setReasonPhrase("Not Found");
    }
  }

  private String getPathFromRequest(HttpRequest request) {
    String requestPath = request.getRequestLine().getUri();
    return requestPath.substring(PREFIX.length());
  }

  /** Skips a specified amount of bytes in the {@link InputStream}. */
  private static void skipBytes(InputStream stream, long bytesToSkip) throws IOException {
    if (bytesToSkip <= 0) {
      return;
    }

    while (bytesToSkip > 0) {
      long skipped = stream.skip(bytesToSkip);
      if (skipped > 0) {
        bytesToSkip -= skipped;
        continue;
      }
      // Skip has no specific contract as to what happens when you reach the end of
      // the stream. To differentiate between temporarily not having more data and
      // having finished the stream, we read a single byte when we fail to skip any
      // amount of data.
      int testEofByte = stream.read();
      if (testEofByte == -1) {
        throw new EOFException();
      } else {
        bytesToSkip--;
      }
    }
    if (bytesToSkip != 0) {
      throw new IOException();
    }
  }
}
