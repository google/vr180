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

package com.google.vr180.api.implementations;

import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.vr180.CameraApi.CameraApiRequest.ConfigurationRequest.TimeConfiguration;
import com.google.vr180.CameraApi.CameraCalibration;
import com.google.vr180.CameraApi.CameraCapabilities;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.CaptureMode.CaptureType;
import com.google.vr180.CameraApi.IndicatorBrightnessConfiguration;
import com.google.vr180.CameraApi.LiveStreamMode;
import com.google.vr180.CameraApi.PhotoMode;
import com.google.vr180.CameraApi.SleepConfiguration;
import com.google.vr180.CameraApi.VideoMode;
import com.google.vr180.CameraApi.WhiteBalanceMode;
import com.google.vr180.api.camerainterfaces.CameraSettings;
import com.google.vr180.common.SharedPreferenceUtils;
import com.google.vr180.common.logging.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.util.List;
import java.util.TimeZone;

/** A wrapper class for getting and setting values with PreferenceManager. */
public class Settings implements CameraSettings {
  private static final String TAG = "Settings";

  private static final String LOCAL_KEY_PAIR = "local_key_pair";
  private static final String SHARED_KEY_KEY = "shared_key";
  private static final String ACTIVE_CAPTURE_MODE_KEY = "active_capture_mode";
  private static final String SHARED_KEY_IS_PENDING_KEY = "shared_key_is_pending";
  private static final String CAMERA_CALIBRATION_KEY = "camera_calibration";
  private static final boolean SHARED_KEY_IS_PENDING_DEFAULT = true;
  private static final String SLEEP_CONFIG_KEY = "sleep_config";
  private static final SleepConfiguration DEFAULT_SLEEP_CONFIG =
      SleepConfiguration.newBuilder().setWakeTimeSeconds(30).setSleepTimeSeconds(300).build();

  private Context context;
  private final CameraCapabilities capabilities;

  public Settings(Context context, CameraCapabilities capabilities) {
    this.context = context;
    this.capabilities = capabilities;
  }

  /** Saves the local key pair to settings. */
  @Override
  public void setLocalKeyPair(KeyPair localKeyPair) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String value;
    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
      objectOutputStream.writeObject(localKeyPair);
      value = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
      objectOutputStream.close();
      outputStream.close();
    } catch (IOException e) {
      throw new RuntimeException("Error saving local key pair.", e);
    }
    prefs.edit().putString(LOCAL_KEY_PAIR, value).apply();
  }

  /** Gets the local key pair. Returns null if no key pair is available. */
  @Override
  public KeyPair getLocalKeyPair() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String keyPairString = prefs.getString(LOCAL_KEY_PAIR, null);
    if (keyPairString == null) {
      return null;
    }

    KeyPair result;
    try {
      ByteArrayInputStream inputStream =
          new ByteArrayInputStream(Base64.decode(keyPairString, Base64.DEFAULT));
      ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
      result = (KeyPair) objectInputStream.readObject();
      objectInputStream.close();
      inputStream.close();
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException("Error reading local key pair.", e);
    }
    return result;
  }

  /** Gets the shared AES key to use for bluetooth communication. */
  @Override
  public byte[] getSharedKey() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String sharedKeyString = prefs.getString(SHARED_KEY_KEY, null);
    if (sharedKeyString == null) {
      return null;
    }

    return Base64.decode(sharedKeyString, Base64.DEFAULT);
  }

  /** Sets the shared AES key to use for bluetooth communication. */
  @Override
  public void setSharedKey(byte[] sharedKey) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String value = Base64.encodeToString(sharedKey, Base64.DEFAULT);
    prefs.edit().putString(SHARED_KEY_KEY, value).apply();
  }

  /**
   * Sets whether the shared key is temporary. If set to true, the api funcionality will not be
   * fully available.
   */
  @Override
  public boolean getSharedKeyIsPending() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    return prefs.getBoolean(SHARED_KEY_IS_PENDING_KEY, SHARED_KEY_IS_PENDING_DEFAULT);
  }

  /** Sets whether the shared key is temporary. */
  @Override
  public void setSharedKeyIsPending(boolean pending) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putBoolean(SHARED_KEY_IS_PENDING_KEY, pending).apply();
  }

  @Override
  public void setTime(TimeConfiguration timeConfiguration) {
    Log.d(TAG, "Time update received!");
    try {
      AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
      alarmManager.setTime(timeConfiguration.getTimestamp());
      if (!TimeZone.getDefault().getID().equals(timeConfiguration.getTimezone())) {
        Log.d(
            TAG,
            "Timezone update received! "
                + TimeZone.getDefault().getID()
                + " -> "
                + timeConfiguration.getTimezone());
        alarmManager.setTimeZone(timeConfiguration.getTimezone());
      }
    } catch (SecurityException e) {
      Log.e(TAG, "Failed to update time.", e);
    }
  }

  @Override
  public void setCameraCalibration(CameraCalibration cameraCalibration) {
    Preconditions.checkNotNull(cameraCalibration);
    SharedPreferenceUtils.writeProtoSetting(context, CAMERA_CALIBRATION_KEY, cameraCalibration);
  }

  @Override
  public CameraCalibration getCameraCalibration() {
    CameraCalibration calibration =
        SharedPreferenceUtils.readProtoSetting(
            context, TAG, CAMERA_CALIBRATION_KEY, CameraCalibration.parser(), null);
    if (calibration == null) {
      return CameraCalibration.newBuilder()
          .setSt3DBox(ByteString.EMPTY)
          .setSv3DBox(ByteString.EMPTY)
          .build();
    }
    return calibration;
  }

  @Override
  public void factoryReset() {
    // We reset the user preferences to clear any pairing info.
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().clear().apply();
  }

  @Override
  public void formatStorage() {
    // Not implemented.
  }

  @Override
  public void setIndicatorBrightness(IndicatorBrightnessConfiguration brightness) {
    // Not implemented.
  }

  @Override
  public IndicatorBrightnessConfiguration getIndicatorBrightness() {
    return null;
  }

  @Override
  public void setSleepConfiguration(SleepConfiguration sleepConfiguration) {
    SharedPreferenceUtils.writeProtoSetting(context, SLEEP_CONFIG_KEY, sleepConfiguration);
  }

  @Override
  public SleepConfiguration getSleepConfiguration() {
    return SharedPreferenceUtils.readProtoSetting(
        context, TAG, SLEEP_CONFIG_KEY, SleepConfiguration.parser(), DEFAULT_SLEEP_CONFIG);
  }

  @Override
  public CaptureMode getActiveCaptureMode() {
    CaptureMode captureMode =
        SharedPreferenceUtils.readProtoSetting(
            context, TAG, ACTIVE_CAPTURE_MODE_KEY, CaptureMode.parser(), null);
    List<VideoMode> supportedVideoModeList = capabilities.getSupportedVideoModesList();
    List<PhotoMode> supportedPhotoModeList = capabilities.getSupportedPhotoModesList();

    // Reset if the previously saved mode is removed.
    if (captureMode != null
        && captureMode.getActiveCaptureType() == CaptureType.VIDEO
        && !supportedVideoModeList.contains(captureMode.getConfiguredVideoMode())) {
      Log.d(TAG, "Saved video mode is no longer supported");
      captureMode =
          captureMode.toBuilder().setConfiguredVideoMode(supportedVideoModeList.get(0)).build();
    }
    if (captureMode != null
        && captureMode.getActiveCaptureType() == CaptureType.PHOTO
        && !supportedPhotoModeList.contains(captureMode.getConfiguredPhotoMode())) {
      Log.d(TAG, "Saved photo mode is no longer supported");
      captureMode =
          captureMode.toBuilder().setConfiguredPhotoMode(supportedPhotoModeList.get(0)).build();
    }

    // Default to the first video mode for now. Note this should be switch to photo when supported.
    if (captureMode == null) {
      captureMode =
          CaptureMode.newBuilder()
              .setConfiguredVideoMode(supportedVideoModeList.get(0))
              .setConfiguredPhotoMode(supportedPhotoModeList.get(0))
              .setConfiguredLiveMode(
                  LiveStreamMode.newBuilder()
                      .setVideoMode(capabilities.getSupportedLiveModesList().get(0)))
              .setActiveCaptureType(CaptureType.VIDEO)
              .setWhiteBalanceMode(WhiteBalanceMode.AUTO)
              .build();
      setActiveCaptureMode(captureMode);
    }
    return captureMode;
  }

  @Override
  public void clearLiveEndPoint() {
    CaptureMode mode = getActiveCaptureMode();
    if (mode.getActiveCaptureType() != CaptureType.LIVE) {
      return;
    }
    setActiveCaptureMode(
        mode.toBuilder()
            .setConfiguredLiveMode(
                LiveStreamMode.newBuilder()
                    .setVideoMode(mode.getConfiguredLiveMode().getVideoMode()))
            .build());
  }

  @Override
  public void setSphericalMetadata(byte[] st3d, byte[] sv3d) {
    setCameraCalibration(
        CameraCalibration.newBuilder()
            .setSt3DBox(st3d == null ? ByteString.EMPTY : ByteString.copyFrom(st3d))
            .setSv3DBox(sv3d == null ? ByteString.EMPTY : ByteString.copyFrom(sv3d))
            .build());
  }

  @Override
  public void setActiveCaptureMode(CaptureMode captureMode) {
    SharedPreferenceUtils.writeProtoSetting(context, ACTIVE_CAPTURE_MODE_KEY, captureMode);
  }
}
