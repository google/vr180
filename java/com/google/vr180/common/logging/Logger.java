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

package com.google.vr180.common.logging;

/**
 * An interface that simulates the one in android.util.Log. We can then provide different
 * implementations, and switch between them in different build types.
 */
public interface Logger {
  // Log a verbose message.
  void v(String tag, String msg);

  // Log a debug message.
  void d(String tag, String msg);

  // Log an info message.
  void i(String tag, String msg);

  // Log a warning message.
  void w(String tag, String msg);

  // Log an error message.
  void e(String tag, String msg);

  // Log a verbose message with and a {@link Throwable}.
  void v(String tag, String msg, Throwable tr);

  // Log a debug message with and a {@link Throwable}.
  void d(String tag, String msg, Throwable tr);

  // Log an info message with and a {@link Throwable}.
  void i(String tag, String msg, Throwable tr);

  // Log a warning message with and a {@link Throwable}.
  void w(String tag, String msg, Throwable tr);

  // Log an error message with and a {@link Throwable}.
  void e(String tag, String msg, Throwable tr);
}
