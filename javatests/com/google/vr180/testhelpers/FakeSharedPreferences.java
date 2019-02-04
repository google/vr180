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

package com.google.vr180.testhelpers;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Fake implementation of {@link SharedPreferences}. */
public class FakeSharedPreferences implements SharedPreferences {

  private final HashMap<String, Object> values;
  private final HashSet<OnSharedPreferenceChangeListener> changeListeners;

  /** Fake implementation of {@link SharedPreferences.Editor} for {@link FakeSharedPreferences} */
  public class Editor implements SharedPreferences.Editor {

    private final HashMap<String, Object> editableValues;

    public Editor() {
      editableValues = new HashMap<>(values);
    }

    @Override
    public Editor clear() {
      editableValues.clear();
      return this;
    }

    public void startCommit() {
      // Empty
    }

    @Override
    public boolean commit() {
      values.clear();
      values.putAll(editableValues);
      for (OnSharedPreferenceChangeListener listener : changeListeners) {
        for (String key : editableValues.keySet()) {
          listener.onSharedPreferenceChanged(FakeSharedPreferences.this, key);
        }
      }

      return true;
    }

    @Override
    public Editor putBoolean(String key, boolean value) {
      editableValues.put(key, value);
      return this;
    }

    @Override
    public Editor putFloat(String key, float value) {
      editableValues.put(key, value);
      return this;
    }

    @Override
    public Editor putInt(String key, int value) {
      editableValues.put(key, value);
      return this;
    }

    @Override
    public Editor putLong(String key, long value) {
      editableValues.put(key, value);
      return this;
    }

    @Override
    public Editor putString(String key, String value) {
      editableValues.put(key, value);
      return this;
    }

    @Override
    public Editor remove(String key) {
      editableValues.remove(key);
      return this;
    }

    @Override
    public Editor putStringSet(String key, Set<String> value) {
      editableValues.put(key, value);
      return this;
    }

    @Override
    public void apply() {
      commit();
    }
  }

  public FakeSharedPreferences() {
    values = new HashMap<>();
    changeListeners = new HashSet<>();
  }

  @Override
  public boolean contains(String key) {
    return values.containsKey(key);
  }

  public int size() {
    return values.size();
  }

  @Override
  public SharedPreferences.Editor edit() {
    return new Editor();
  }

  @Override
  public Map<String, ?> getAll() {
    return Collections.unmodifiableMap(values);
  }

  @Override
  public boolean getBoolean(String key, boolean defValue) {
    if (values.containsKey(key)) {
      return (Boolean) values.get(key);
    } else {
      return defValue;
    }
  }

  @Override
  public float getFloat(String key, float defValue) {
    if (values.containsKey(key)) {
      return (Float) values.get(key);
    } else {
      return defValue;
    }
  }

  @Override
  public int getInt(String key, int defValue) {
    if (values.containsKey(key)) {
      return (Integer) values.get(key);
    } else {
      return defValue;
    }
  }

  @Override
  public long getLong(String key, long defValue) {
    if (values.containsKey(key)) {
      return (Long) values.get(key);
    } else {
      return defValue;
    }
  }

  @Override
  public String getString(String key, String defValue) {
    if (values.containsKey(key) && values.get(key) != null) {
      return (String) values.get(key);
    } else {
      return defValue;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<String> getStringSet(String key, Set<String> defValue) {
    if (values.containsKey(key)) {
      return (Set<String>) values.get(key);
    } else {
      return defValue;
    }
  }

  @Override
  public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    changeListeners.add(listener);
  }

  @Override
  public void unregisterOnSharedPreferenceChangeListener(
      OnSharedPreferenceChangeListener listener) {
    changeListeners.remove(listener);
  }
}
