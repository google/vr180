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
 * A class that implements the {@link Logger} interface and outputs the logs directly to logcat.
 * This implementation does not store any messages, so format returns an empty string.
 */
public class LogcatLogger implements Logger {

  @Override
  public void d(String tag, String msg) {
    android.util.Log.d(tag, msg);
  }

  @Override
  public void i(String tag, String msg) {
    android.util.Log.i(tag, msg);
  }

  @Override
  public void w(String tag, String msg) {
    android.util.Log.w(tag, msg);
  }

  @Override
  public void v(String tag, String msg) {
    android.util.Log.v(tag, msg);
  }

  @Override
  public void e(String tag, String msg) {
    android.util.Log.e(tag, msg);
  }

  @Override
  public void d(String tag, String msg, Throwable tr) {
    android.util.Log.d(tag, msg, tr);
  }

  @Override
  public void i(String tag, String msg, Throwable tr) {
    android.util.Log.i(tag, msg, tr);
  }

  @Override
  public void w(String tag, String msg, Throwable tr) {
    android.util.Log.w(tag, msg, tr);
  }

  @Override
  public void v(String tag, String msg, Throwable tr) {
    android.util.Log.v(tag, msg, tr);
  }

  @Override
  public void e(String tag, String msg, Throwable tr) {
    android.util.Log.e(tag, msg, tr);
  }
}
