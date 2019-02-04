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

package com.google.vr180.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import com.google.vr180.common.logging.Log;

/**
 * Utility methods for handling protos stored in shared preferences.
 */
public class SharedPreferenceUtils {

  /**
   * Reads and parses a proto value from the SharedPreferences with the specified key.
   *
   * @param context The context
   * @param tag The tag used for error logging
   * @param key The shared preference key
   * @param parser The protocol buffer parser
   * @param defaultValue The default value to return if there is no preference setting or the
   *     setting cannot be parsed.
   * @return The parsed preference, or default if none is available.
   */
  public static <T> T readProtoSetting(
      Context context, String tag, String key, Parser<T> parser, T defaultValue) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String protoString = prefs.getString(key, null);
    T result = defaultValue;
    if (protoString != null) {
      try {
        result = parser.parseFrom(Base64.decode(protoString, Base64.DEFAULT));
      } catch (InvalidProtocolBufferException e) {
        Log.i(tag, "Error reading " + key, e);
      }
    }

    return result;
  }

  /**
   * Writes a proto value to SharedPreferences with the specified key.
   *
   * @param context The context
   * @param key The shared preference key
   * @param proto The proto message
   */
  public static void writeProtoSetting(Context context, String key, MessageLite proto) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String value = Base64.encodeToString(proto.toByteArray(), Base64.DEFAULT);
    prefs.edit().putString(key, value).apply();
  }

}
