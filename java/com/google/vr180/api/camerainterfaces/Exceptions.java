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

package com.google.vr180.api.camerainterfaces;

/**
 * Exception types that represent errors returned from the api.
 */
public class Exceptions {

  /** The operation failed because the current state doesn't allow it. */
  public static class InvalidRequestException extends Exception {}

  /** The camera doesn't support this kind of operation. */
  public static class NotSupportedException extends Exception {}

  /** The battery is too low to perform the requested action. */
  public static class CriticallyLowBatteryException extends Exception {}

  /** There isn't enough storage on the camera to perform the requested action. */
  public static class InsufficientStorageException extends Exception {}

  /** The camera got too overheated to perform the request. */
  public static class ThermalException extends Exception {}

  /** The operation failed because user has not authorized it, or the credentials have expired. */
  public static class UnauthorizedException extends Exception {}
}
