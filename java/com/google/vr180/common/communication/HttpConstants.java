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

package com.google.vr180.common.communication;

/**
 * Constants related to HTTP for camera API.
 */
public class HttpConstants {
  /** The name of the Authorization header. */
  public static final String AUTHORIZATION_HEADER = "Authorization";

  /**
   * The authorization schema name.
   * Used in the header like "Authorization: daydreamcamera [hash]".
   */
  public static final String AUTHORIZATION_SCHEME_NAME = "daydreamcamera";

  /** The name of the content type header. */
  public static final String CONTENT_TYPE_HEADER = "Content-Type";

  /** The value of a JSON content type. */
  public static final String CONTENT_TYPE_JSON_VALUE = "application/json";
}
