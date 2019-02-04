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

package com.google.vr180.testhelpers;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.mockito.Mockito;

/** Helper for mocking http calls. */
public class MockHttpHelper {
  private static final String TEST_URL = "http://example.com/";

  /** Creates an http client that returns the provided response to any request. */
  public static OkHttpClient getHttpClient(byte[] response) throws Exception {
    OkHttpClient mockHttpClient = Mockito.mock(OkHttpClient.class);
    Call mockCall = Mockito.mock(Call.class);
    Mockito.doReturn(mockCall).when(mockHttpClient).newCall(Mockito.any());
    Response mockResponse = createResponse(response);
    Mockito.doReturn(mockResponse).when(mockCall).execute();
    return mockHttpClient;
  }

  /** Creates a mock response with the provided body. */
  public static Response createResponse(byte[] response) throws Exception {
    return new Response.Builder()
        .code(200)
        .request(new Request.Builder().url(TEST_URL).build())
        .protocol(Protocol.HTTP_1_1)
        .body(ResponseBody.create(MediaType.parse("application/octet-stream"), response))
        .build();
  }
}
