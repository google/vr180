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

import java.util.Map;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;

/**
 * Helper class for constructing an Apache HttpService instance for handling the Camera APIs.
 */
public class HttpServiceFactory {
  private static final int SOCKET_BUFFER_SIZE = 4 * 1024;
  private static final int SOCKET_DATA_TIMEOUT = 6000;
  private static final int SOCKET_CONNECTION_TIMEOUT = 3000;

  public static HttpService constructHttpService(Map<String, HttpRequestHandler> handlerMap) {
    BasicHttpProcessor httpProcessor = new BasicHttpProcessor();
    httpProcessor.addInterceptor(new ResponseDate());
    httpProcessor.addInterceptor(new ResponseContent());
    httpProcessor.addInterceptor(new ResponseConnControl());
    HttpRequestHandlerRegistry httpRegistry = new HttpRequestHandlerRegistry();
    httpRegistry.setHandlers(handlerMap);
    HttpService httpService =
        new HttpService(
            httpProcessor, new DefaultConnectionReuseStrategy(), new DefaultHttpResponseFactory());
    httpService.setHandlerResolver(httpRegistry);
    httpService.setParams(constructHttpParams());
    return httpService;
  }

  /** Configure the http params that the server will use. */
  private static BasicHttpParams constructHttpParams() {
    BasicHttpParams params = new BasicHttpParams();
    params
        .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, SOCKET_DATA_TIMEOUT)
        .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, SOCKET_CONNECTION_TIMEOUT)
        .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, SOCKET_BUFFER_SIZE)
        .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
        .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true);
    return params;
  }
}
