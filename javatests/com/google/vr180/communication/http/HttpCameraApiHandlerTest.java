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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.google.vr180.CameraApi.CameraApiRequest;
import com.google.vr180.CameraApi.CameraApiResponse;
import com.google.vr180.CameraApi.CameraApiResponse.ResponseStatus;
import com.google.vr180.CameraApi.SleepConfiguration;
import com.google.vr180.api.CameraApiHandler;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.testhelpers.TestCameraCalibration;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 21)
public final class HttpCameraApiHandlerTest {
  private HttpCameraApiHandler httpHandler;
  @Mock private AuthorizationValidator mockAuthorizationValidator;
  @Mock private CameraSettings mockCameraSettings;
  @Mock private CameraApiHandler mockApiHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(mockCameraSettings.getCameraCalibration())
        .thenReturn(TestCameraCalibration.TOP_BOTTOM_STEREO);
    when(mockCameraSettings.getSleepConfiguration())
        .thenReturn(SleepConfiguration.newBuilder().setWakeTimeSeconds(30).build());
    doAnswer(invocation -> {
      HttpResponse response = (HttpResponse) invocation.getArguments()[0];
      response.setStatusCode(403);
      return null;
    }).when(mockAuthorizationValidator).setUnauthorizedResponse(Mockito.any());
    // By default, validate all requests.
    Mockito.doReturn(true)
        .when(mockAuthorizationValidator)
        .isValidRequest(Mockito.any(), Mockito.any());

    Mockito.doReturn(
        CameraApiResponse.newBuilder()
            .setResponseStatus(
                ResponseStatus.newBuilder().setStatusCode(ResponseStatus.StatusCode.OK))
            .build())
        .when(mockApiHandler)
        .handleRequest(Mockito.any());
    httpHandler = new HttpCameraApiHandler(mockApiHandler, mockAuthorizationValidator);
  }

  @Test
  public void testHttpCameraApiHandler() throws Exception {
    BasicHttpEntityEnclosingRequest request = buildRequest();
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    httpHandler.handle(request, response, null);

    assertThat(response.getEntity()).isNotNull();
    CameraApiResponse cameraResponse =
        CameraApiResponse.parseFrom(response.getEntity().getContent());
    assertThat(cameraResponse.getResponseStatus().getStatusCode())
        .isEqualTo(CameraApiResponse.ResponseStatus.StatusCode.OK);
  }

  @Test
  public void testInvalidAuthRequest() throws Exception {
    Mockito.doReturn(false)
        .when(mockAuthorizationValidator)
        .isValidRequest(Mockito.any(), Mockito.any());
    BasicHttpEntityEnclosingRequest request = buildRequest();

    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    httpHandler.handle(request, response, null);

    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(403);
  }

  @Test
  public void testNoBodyRequest() throws Exception {
    BasicHttpRequest request = new BasicHttpRequest("GET", "/daydreamcamera");
    HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1), 200, "OK");
    httpHandler.handle(request, response, null);
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(400);
  }

  private BasicHttpEntityEnclosingRequest buildRequest() {
    CameraApiRequest cameraRequest =
        CameraApiRequest.newBuilder()
            .setType(CameraApiRequest.RequestType.STATUS)
            .setHeader(
                CameraApiRequest.RequestHeader.newBuilder()
                    .setRequestId(10)
                    .setExpirationTimestamp(new Date().getTime() + 1000)
                    .build())
            .build();
    BasicHttpEntityEnclosingRequest request =
        new BasicHttpEntityEnclosingRequest("POST", "/daydreamcamera");
    BasicHttpEntity entity = new BasicHttpEntity();
    InputStream stream = new ByteArrayInputStream(cameraRequest.toByteArray());
    entity.setContentType("application/octet-stream");
    entity.setContent(stream);
    request.setEntity(entity);
    return request;
  }
}
