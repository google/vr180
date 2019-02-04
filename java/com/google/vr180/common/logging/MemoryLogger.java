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

import com.google.common.collect.EvictingQueue;
import com.google.vr180.CameraApi.DebugLogMessage;
import com.google.vr180.CameraApi.DebugLogMessage.Level;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A class that implements the {@link Logger} interface by saving the log events in memory.
 * It only keeps the latest log events, up to a maximum number, the rest are discarded.
 */
public class MemoryLogger implements Logger {
  static final int MAX_NUM_ITEMS = 1000;
  final EvictingQueue<DebugLogMessage> queue = EvictingQueue.create(MAX_NUM_ITEMS);

  @Override
  public synchronized void v(String tag, String msg) {
    queue.add(createLogMessage(Level.VERBOSE, tag, msg));
  }

  @Override
  public synchronized void d(String tag, String msg) {
    queue.add(createLogMessage(Level.DEBUG, tag, msg));
  }

  @Override
  public synchronized void i(String tag, String msg) {
    queue.add(createLogMessage(Level.INFO, tag, msg));
  }

  @Override
  public synchronized void w(String tag, String msg) {
    queue.add(createLogMessage(Level.WARN, tag, msg));
  }

  @Override
  public synchronized void e(String tag, String msg) {
    queue.add(createLogMessage(Level.ERROR, tag, msg));
  }

  @Override
  public synchronized void v(String tag, String msg, Throwable tr) {
    queue.add(
        createLogMessage(
            Level.VERBOSE, tag, msg + '\n' + android.util.Log.getStackTraceString(tr)));
  }

  @Override
  public synchronized void d(String tag, String msg, Throwable tr) {
    queue.add(
        createLogMessage(Level.DEBUG, tag, msg + '\n' + android.util.Log.getStackTraceString(tr)));
  }

  @Override
  public synchronized void i(String tag, String msg, Throwable tr) {
    queue.add(
        createLogMessage(Level.INFO, tag, msg + '\n' + android.util.Log.getStackTraceString(tr)));
  }

  @Override
  public synchronized void w(String tag, String msg, Throwable tr) {
    queue.add(
        createLogMessage(Level.WARN, tag, msg + '\n' + android.util.Log.getStackTraceString(tr)));
  }

  @Override
  public synchronized void e(String tag, String msg, Throwable tr) {
    queue.add(
        createLogMessage(Level.ERROR, tag, msg + '\n' + android.util.Log.getStackTraceString(tr)));
  }

  /** Returns all of the queued messages. */
  public List<DebugLogMessage> getMessages() {
    return new ArrayList<DebugLogMessage>(queue);
  }

  private static DebugLogMessage createLogMessage(Level level, String tag, String message) {
    return DebugLogMessage.newBuilder()
        .setTimestamp(new Date().getTime())
        .setLevel(level)
        .setTag(tag)
        .setMessage(message)
        .build();
  }
}
