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
import com.google.vr180.api.camerainterfaces.UpdateManager;
import com.google.vr180.common.IoUtils;
import com.google.vr180.common.logging.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

/** Handler that implements receiving an update via the "/update/[updatename]" endpoint. */
public class UpdateUploadHandler implements HttpRequestHandler {
  public static final String PREFIX = "/update/";

  private static final String TAG = "UpdateUploadHandler";
  private static final String CONTENT_RANGE = "Content-Range";
  private static final String PUT_METHOD = "PUT";

  private final UpdateManager updateManager;
  private final AuthorizationValidator authorizationValidator;

  public UpdateUploadHandler(
      UpdateManager updateManager, AuthorizationValidator authorizationValidator) {
    this.updateManager = updateManager;
    this.authorizationValidator = authorizationValidator;
  }

  @Override
  public void handle(HttpRequest request, HttpResponse response, HttpContext context) {
    String requestPath = request.getRequestLine().getUri();
    if (!requestPath.startsWith(PREFIX)) {
      Log.w(TAG, "Invalid prefix sent to UpdateUploadHandler: " + requestPath);
      response.setStatusCode(StatusCode.BAD_REQUEST);
      return;
    }

    if (!PUT_METHOD.equalsIgnoreCase(request.getRequestLine().getMethod())) {
      Log.w(TAG, "Only put requests are supported.");
      response.setStatusCode(StatusCode.BAD_REQUEST);
      return;
    }

    InputStream requestStream = null;
    try {
      requestStream = ((BasicHttpEntityEnclosingRequest) request).getEntity().getContent();
      byte[] requestBody = ByteStreams.toByteArray(requestStream);
      if (!authorizationValidator.isValidRequest(request, requestBody)) {
        authorizationValidator.setUnauthorizedResponse(response);
        return;
      }

      ContentRange range;
      try {
        range = parseRange(request, requestBody.length);
      } catch (ByteRange.RangeFormatException e) {
        response.setStatusCode(StatusCode.BAD_REQUEST);
        response.setReasonPhrase("Invalid range requested");
        return;
      }

      String updateName = requestPath.substring(PREFIX.length());
      File updatePath = new File(updateManager.getUpdateDirectory(), updateName);
      try (RandomAccessFile updateFile = new RandomAccessFile(updatePath, "rw")) {
        updateFile.seek(range.getByteRange().getStart());
        updateFile.write(requestBody);
      }

      if (range.hasLength() && updatePath.length() == range.getLength()) {
        updateManager.handleUpdate(updateName);
      }
    } catch (IOException | UnsupportedOperationException e) {
      response.setStatusCode(StatusCode.BAD_REQUEST);
    } finally {
      IoUtils.closeSilently(requestStream);
    }
  }

  /**
   * Parses the Content-Range header if specifed. Otherwise returns a range representing the entire
   * file.
   *
   * @param request The http request to parse the header out of
   * @param payloadSize The size of the uploaded payload (used if Content-Range isn't specified)
   * @return A content range representing the byte range and total size that the request is pushing.
   */
  private ContentRange parseRange(HttpRequest request, long payloadSize)
      throws ByteRange.RangeFormatException {
    Header rangeHeader = request.getFirstHeader(CONTENT_RANGE);
    if (rangeHeader == null || rangeHeader.getValue() == null) {
      // The payload is the full file.
      return new ContentRange(new ByteRange(0), payloadSize);
    }

    return ContentRange.parse(rangeHeader.getValue());
  }
}
