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

import com.google.common.base.Stopwatch;
import com.google.common.io.ByteStreams;
import com.google.vr180.CameraApi.CameraApiRequest.ConnectionTestRequest;
import com.google.vr180.CameraApi.CameraApiResponse.ConnectionTestResponse;
import com.google.vr180.api.camerainterfaces.ConnectionTester;
import com.google.vr180.common.logging.Log;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/** Implementation of ConnectionTester using HttpURLConnection. */
public class AndroidConnectionTester implements ConnectionTester {
  private static final String TAG = "AndroidConnectionTester";

  @Override
  public ConnectionTestResponse testConnection(ConnectionTestRequest request) {
    int responseCode = 0;
    long receivedBytes = 0;
    Stopwatch stopwatch = Stopwatch.createStarted();
    HttpURLConnection connection = null;
    try {
      URL url = new URL(request.getUrl());
      connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(request.getConnectTimeoutMs());
      connection.setReadTimeout(request.getReadTimeoutMs());
      responseCode = connection.getResponseCode();
      receivedBytes = ByteStreams.copy(connection.getInputStream(), ByteStreams.nullOutputStream());
    } catch (IOException e) {
      Log.e(TAG, "Error fetching connection test url." + e.toString());
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }

    return ConnectionTestResponse.newBuilder()
        .setHttpResponseCode(responseCode)
        .setBytesReceived(receivedBytes)
        .setReceiveTimeMs(stopwatch.elapsed(TimeUnit.MILLISECONDS))
        .build();
  }
}
