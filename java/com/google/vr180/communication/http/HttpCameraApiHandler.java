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
import com.google.protobuf.ExtensionRegistryLite;
import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.api.CameraApiHandler;
import com.google.vr180.common.IoUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

/**
 * Handler that implements the Camera APIs over HTTP.
 */
public class HttpCameraApiHandler implements HttpRequestHandler {
  private static final String TAG = "HttpCameraApiHandler";

  private final CameraApiHandler apiHandler;
  private final AuthorizationValidator authorizationValidator;
  private final ExtensionRegistryLite extensionRegistry;

  public HttpCameraApiHandler(
      CameraApiHandler apiHandler, AuthorizationValidator authorizationValidator) {
    this(apiHandler, authorizationValidator, ExtensionRegistryLite.newInstance());
  }

  public HttpCameraApiHandler(
      CameraApiHandler apiHandler,
      AuthorizationValidator authorizationValidator,
      ExtensionRegistryLite extensionRegistry) {
    this.apiHandler = apiHandler;
    this.authorizationValidator = authorizationValidator;
    this.extensionRegistry = extensionRegistry;
  }

  /** Handle a request. */
  @Override
  public void handle(HttpRequest request, HttpResponse response, HttpContext context) {
    if (!(request instanceof BasicHttpEntityEnclosingRequest)) {
      setErrorStatus(response);
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

      CameraApiRequest cameraRequest = CameraApiRequest.parseFrom(requestBody,
          extensionRegistry);
      CameraApiResponse cameraResponse = apiHandler.handleRequest(cameraRequest);
      BasicHttpEntity entity = new BasicHttpEntity();
      InputStream stream = new ByteArrayInputStream(cameraResponse.toByteArray());
      entity.setContentType("application/octet-stream");
      entity.setContent(stream);
      response.setEntity(entity);
    } catch (IOException e) {
      setErrorStatus(response);
    } finally {
      IoUtils.closeSilently(requestStream);
    }
  }

  private static void setErrorStatus(HttpResponse response) {
    response.setStatusCode(400);
  }
}
