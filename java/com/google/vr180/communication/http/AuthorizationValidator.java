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

import android.util.Base64;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.common.communication.HttpConstants;
import com.google.vr180.common.crypto.CryptoUtilities;
import com.google.vr180.common.logging.Log;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

/**
 * Class that checks the Authorization header of a request to see that it is properly signed by the
 * client.
 *
 * <p>This is done by checking the HMAC of the request.
 */
public class AuthorizationValidator {
  private static final String TAG = "AuthorizationValidator";

  private final CameraSettings settings;

  public AuthorizationValidator(CameraSettings settings) {
    this.settings = settings;
  }

  /**
   * Checks that an http request has a valid authorization header.
   *
   * @param request The http request.
   * @param requestBody The content of the HTTP request (or null if no body is provided). This is
   *     provided separately in case the content stream isn't seekable.
   * @return Whether the request has valid authorization.
   */
  public boolean isValidRequest(HttpRequest request, byte[] requestBody) {
    if (settings.getSharedKey() == null || settings.getSharedKeyIsPending()) {
      // If we are not fully paired, no request is valid.
      return false;
    }

    Header authorizationHeader = request.getFirstHeader(HttpConstants.AUTHORIZATION_HEADER);
    if (authorizationHeader == null) {
      return false;
    }

    String authorizationString = authorizationHeader.getValue();
    if (authorizationString == null) {
      return false;
    }

    String[] authorizationParts = authorizationString.split(" ");
    if (authorizationParts.length != 2
        || !HttpConstants.AUTHORIZATION_SCHEME_NAME.equals(authorizationParts[0])) {
      return false;
    }

    byte[] hmac;
    try {
      hmac = Base64.decode(authorizationParts[1], Base64.URL_SAFE);
    } catch (IllegalArgumentException e) {
      return false;
    }

    byte[] expectedHmac;
    try {
      expectedHmac = computeRequestHmac(request, requestBody);
    } catch (CryptoUtilities.CryptoException e) {
      Log.e(TAG, "Error computing request HMAC.", e);
      return false;
    }
    return MessageDigest.isEqual(expectedHmac, hmac);
  }

  byte[] computeRequestHmac(HttpRequest request, byte[] requestBody)
      throws CryptoUtilities.CryptoException {
    ArrayList<byte[]> messages = new ArrayList<byte[]>();
    messages.add(request.getRequestLine().getMethod().getBytes(StandardCharsets.UTF_8));
    messages.add(request.getRequestLine().getUri().getBytes(StandardCharsets.UTF_8));
    if (requestBody != null) {
      messages.add(requestBody);
    }
    return CryptoUtilities.generateHMAC(settings.getSharedKey(), messages);
  }

  /**
   * Sets the http response to indicate that the request was unauthorized.
   *
   * @param response The response object for the request.
   */
  public void setUnauthorizedResponse(HttpResponse response) {
    response.setStatusCode(403);
  }
}
