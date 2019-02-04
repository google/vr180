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

import static org.mockito.Mockito.when;

import android.util.Base64;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.common.crypto.CryptoUtilities;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.apache.http.HttpRequest;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class AuthorizationValidatorTest {

  private byte[] sharedKey;
  private AuthorizationValidator validator;
  private CameraSettings settings;

  @Before
  public void setup() {
    sharedKey = CryptoUtilities.generateRandom(32);
    settings = Mockito.mock(CameraSettings.class);
    when(settings.getSharedKey()).thenReturn(sharedKey);
    when(settings.getSharedKeyIsPending()).thenReturn(false);
    validator = new AuthorizationValidator(settings);
  }

  @Test
  public void testMissingHeader() throws Exception {
    HttpRequest request = new BasicHttpRequest("GET", "/media/0");
    Assert.assertFalse(validator.isValidRequest(request, null));
  }

  @Test
  public void testMissingHeaderValue() throws Exception {
    HttpRequest request = new BasicHttpRequest("GET", "/media/0");
    request.addHeader("Authorization", null);
    Assert.assertFalse(validator.isValidRequest(request, null));
  }

  @Test
  public void testInvalidHeaderValue() throws Exception {
    HttpRequest request = new BasicHttpRequest("GET", "/media/0");
    request.addHeader("Authorization", "daydreamcamera");
    Assert.assertFalse(validator.isValidRequest(request, null));
  }

  @Test
  public void testInvalidAuthScheme() throws Exception {
    HttpRequest request = new BasicHttpRequest("GET", "/media/0");
    request.addHeader("Authorization", "Basic eahytaewrup");
    Assert.assertFalse(validator.isValidRequest(request, null));
  }

  @Test
  public void testWrongHash() throws Exception {
    HttpRequest request = new BasicHttpRequest("GET", "/media/0");
    request.addHeader("Authorization", "daydreamcamera notcorrect");
    Assert.assertFalse(validator.isValidRequest(request, null));
  }

  @Test
  public void testValidRequestNoBody() throws Exception {
    HttpRequest request = new BasicHttpRequest("GET", "/media/0");
    ArrayList<byte[]> messages = new ArrayList<byte[]>();
    messages.add(request.getRequestLine().getMethod().getBytes(StandardCharsets.UTF_8));
    messages.add(request.getRequestLine().getUri().getBytes(StandardCharsets.UTF_8));

    byte[] hmac = CryptoUtilities.generateHMAC(sharedKey, messages);
    request.addHeader(
        "Authorization",
        "daydreamcamera " + Base64.encodeToString(hmac, Base64.NO_WRAP | Base64.URL_SAFE));
    Assert.assertTrue(validator.isValidRequest(request, null));
  }

  @Test
  public void testWrongHashWithBody() throws Exception {
    HttpRequest request = new BasicHttpRequest("GET", "/media/0");
    byte[] testBody = "test".getBytes(StandardCharsets.UTF_8);
    ArrayList<byte[]> messages = new ArrayList<byte[]>();
    messages.add(request.getRequestLine().getMethod().getBytes(StandardCharsets.UTF_8));
    messages.add(request.getRequestLine().getUri().getBytes(StandardCharsets.UTF_8));

    // Calculate the hash that would be correct except for the request body.
    byte[] hmac = CryptoUtilities.generateHMAC(sharedKey, messages);
    request.addHeader(
        "Authorization",
        "daydreamcamera " + Base64.encodeToString(hmac, Base64.NO_WRAP | Base64.URL_SAFE));
    Assert.assertFalse(validator.isValidRequest(request, testBody));
  }

  @Test
  public void testValidRequestWithBody() throws Exception {
    HttpRequest request = new BasicHttpRequest("GET", "/media/0");
    byte[] testBody = "test".getBytes(StandardCharsets.UTF_8);
    ArrayList<byte[]> messages = new ArrayList<byte[]>();
    messages.add(request.getRequestLine().getMethod().getBytes(StandardCharsets.UTF_8));
    messages.add(request.getRequestLine().getUri().getBytes(StandardCharsets.UTF_8));
    messages.add(testBody);

    byte[] hmac = CryptoUtilities.generateHMAC(sharedKey, messages);
    request.addHeader(
        "Authorization",
        "daydreamcamera " + Base64.encodeToString(hmac, Base64.NO_WRAP | Base64.URL_SAFE));
    Assert.assertTrue(validator.isValidRequest(request, testBody));
  }

  @Test
  public void testValidRequestWithPendingKey() throws Exception {
    // If the shared key is pending, the request should fail.
    when(settings.getSharedKeyIsPending()).thenReturn(true);

    HttpRequest request = new BasicHttpRequest("GET", "/media/0");
    byte[] testBody = "test".getBytes(StandardCharsets.UTF_8);
    ArrayList<byte[]> messages = new ArrayList<byte[]>();
    messages.add(request.getRequestLine().getMethod().getBytes(StandardCharsets.UTF_8));
    messages.add(request.getRequestLine().getUri().getBytes(StandardCharsets.UTF_8));
    messages.add(testBody);

    byte[] hmac = CryptoUtilities.generateHMAC(sharedKey, messages);
    request.addHeader(
        "Authorization",
        "daydreamcamera " + Base64.encodeToString(hmac, Base64.NO_WRAP | Base64.URL_SAFE));
    Assert.assertFalse(validator.isValidRequest(request, testBody));

    // If the key is no longer pending, the request should succeed.
    when(settings.getSharedKeyIsPending()).thenReturn(false);
    Assert.assertTrue(validator.isValidRequest(request, testBody));
  }
}
