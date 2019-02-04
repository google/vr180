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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A wrapper around androiud.util.Log that allows for easier customization, e.g. prefixing
 * all the tags with a common prefix for easier parsing of the logs.
 */
public class Log {
  private static final String SEPARATOR = ": ";

  private static String tag = "VR";
  private static List<Logger> loggers = new ArrayList<Logger>(Arrays.asList(new LogcatLogger()));

  /**
   * Adds a logger to receive log data.
   *
   * <p>This method is not thread safe.
   */
  public static void addLogger(Logger logger) {
    loggers.add(logger);
  }

  /**
   * Clears all loggers receiving log data.
   *
   * <p>This method is not thread safe.
   */
  public static void clearLoggers() {
    loggers.clear();
  }

  /**
   * Sets the tag that will be added to all logs.
   *
   * <p>This method is not thread safe.
   */
  public static void setTag(String tag) {
    Log.tag = tag;
  }

  /**
   * Get the tag that will be used for all logs.
   *
   * <p>This method is not thread safe.
   */
  public static String getTag() {
    return tag;
  }

  public static void d(String subTag, String msg) {
    for (Logger logger : loggers) {
      logger.d(tag, subTag + SEPARATOR + msg);
    }
  }

  public static void i(String subTag, String msg) {
    for (Logger logger : loggers) {
      logger.i(tag, subTag + SEPARATOR + msg);
    }
  }

  public static void w(String subTag, String msg) {
    for (Logger logger : loggers) {
      logger.w(tag, subTag + SEPARATOR + msg);
    }
  }

  public static void v(String subTag, String msg) {
    for (Logger logger : loggers) {
      logger.v(tag, subTag + SEPARATOR + msg);
    }
  }

  public static void e(String subTag, String msg) {
    for (Logger logger : loggers) {
      logger.e(tag, subTag + SEPARATOR + msg);
    }
  }

  public static void d(String subTag, String msg, Throwable tr) {
    for (Logger logger : loggers) {
      logger.d(tag, subTag + SEPARATOR + msg, tr);
    }
  }

  public static void i(String subTag, String msg, Throwable tr) {
    for (Logger logger : loggers) {
      logger.i(tag, subTag + SEPARATOR + msg, tr);
    }
  }

  public static void w(String subTag, String msg, Throwable tr) {
    for (Logger logger : loggers) {
      logger.w(tag, subTag + SEPARATOR + msg, tr);
    }
  }

  public static void v(String subTag, String msg, Throwable tr) {
    for (Logger logger : loggers) {
      logger.v(tag, subTag + SEPARATOR + msg, tr);
    }
  }

  public static void e(String subTag, String msg, Throwable tr) {
    for (Logger logger : loggers) {
      logger.e(tag, subTag + SEPARATOR + msg, tr);
    }
  }
}
