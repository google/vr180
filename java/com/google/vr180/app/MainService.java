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

package com.google.vr180.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.content.LocalBroadcastManager;
import com.google.vr180.CameraApi.CameraStatus.RecordingStatus.RecordingState;
import com.google.vr180.CameraApi.CaptureMode.CaptureType;
import com.google.vr180.CameraInternalApi.CameraInternalStatus.PairingStatus;
import com.google.vr180.CameraInternalApi.CameraState;
import com.google.vr180.api.CameraApiClient;
import com.google.vr180.api.CameraApiClient.CameraApiException;
import com.google.vr180.api.internal.CameraInternalApiClient;
import com.google.vr180.common.InstanceMap;
import com.google.vr180.common.logging.Log;
import com.google.vr180.device.DeviceInfo;
import com.google.vr180.device.Hardware;
import com.google.vr180.device.Hardware.Button;
import com.google.vr180.device.Hardware.HardwareListener;
import com.google.vr180.device.Hardware.Mode;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

/** The top-level service which runs the camera app in background mode. */
public class MainService extends Service implements HardwareListener {
  private static final String TAG = "MainService";
  private static final String CAPTURE_WAKE_LOCK = "MainService:CAPTURE_WAKE_LOCK";
  private static final String INIT_WAKE_LOCK = "MainService:INIT_WAKE_LOCK";

  private static final String POWER_OFF_SOUND_PATH =
      "/system/media/audio/notifications/pizzicato.ogg";
  private static final String ACTION_AUTO_POWER_OFF = "PowerOff";
  private static final int AUTO_POWER_OFF_REQUEST_CODE = 1;

  private static final long AUTO_SHUTDOWN_TIME_MS = 30 * 60 * 1000; // 30 minutes

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final CompositeDisposable subscriptions = new CompositeDisposable();
  private final BroadcastReceiver sleepModeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (Intent.ACTION_SCREEN_OFF.equals(action)) {
        onPause();
      } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
        onResume();
      }
    }
  };
  private final ToneGenerator toneGenerator =
      new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME);

  private WifiManager wifiManager;
  private Camera camera;
  private CameraApiClient cameraApiClient;
  private CameraInternalApiClient cameraInternalApiClient;
  private PairingStatus pairingStatus = PairingStatus.DEFAULT_NOT_ADVERTISING;
  private boolean isRecording;
  private Hardware hardware;
  private CaptureType captureType = CaptureType.VIDEO;
  private WakeLock wakeLock;

  @Override
  public void onCreate() {
    super.onCreate();

    wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    camera = InstanceMap.get(Camera.class);
    cameraApiClient = camera.getCameraApiClient();
    cameraInternalApiClient = camera.getInternalApiClient();
    PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
    wakeLock =
        powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, CAPTURE_WAKE_LOCK);

    subscriptions.add(
        camera.getCameraStatusObservable()
            .map(status -> status.getActiveCaptureMode().getActiveCaptureType())
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::updateCaptureType));

    subscriptions.add(
        camera.getCameraStatusObservable()
            .map(
                status ->
                    status.getRecordingStatus().getRecordingState() == RecordingState.RECORDING)
            .distinctUntilChanged()
            .skip(1) // ignore the initial (false) value
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::updateRecordingState));

    subscriptions.add(
        camera.getInternalStatusObservable()
            .map(status -> status.getPairingStatus())
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(pairingStatus -> updatePairingStatus(pairingStatus)));

    subscriptions.add(
        camera.getInternalErrorNotificationObservable().subscribe(unused -> notifyInternalError()));

    hardware = InstanceMap.get(Hardware.class);
    hardware.start(this);

    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_SCREEN_ON);
    filter.addAction(Intent.ACTION_SCREEN_OFF);
    registerReceiver(sleepModeReceiver, filter);

    // Keep the service running as long as possible
    startForeground(1, new Notification.Builder(this).setContentTitle(TAG).build());

    notifyServiceCreation();

    // Acquire the wake lock for 1 ms to wake the device.
    WakeLock tmpWakeLock =
        powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, INIT_WAKE_LOCK);
    tmpWakeLock.acquire(1L);
  }

  @Override
  public void onButtonShortPress(Button button) {
    Log.d(TAG, "onButtonShortPress:" + button);
    if (cameraApiClient == null || camera.getCameraState() != CameraState.DEFAULT_ACTIVE) {
      return;
    }

    switch (button){
      case SHUTTER:
        switch (pairingStatus) {
          case WAITING_FOR_USER_CONFIRMATION:
            cameraInternalApiClient.confirmPairing();
            break;
          case PAIRED:
          case DEFAULT_NOT_ADVERTISING:
          case USER_CONFIRMATION_TIMEOUT:
            try {
              if (isRecording) {
                cameraApiClient.stopCapture();
              } else {
                notifyStartCapture();
                cameraApiClient.startCapture();
              }
            } catch (IOException | CameraApiException e) {
              Log.e(TAG, "Failed to start/stop capture", e);
            }
            break;
          case ADVERTISING:
            cameraInternalApiClient.cancelPairing();
            break;
        }
        break;
      case MODE:
        switchMode();
        break;
      default:
        Log.e(TAG, "Unhandled short press.");
        break;
    }
  }

  @Override
  public void onButtonLongPress(Button button) {
    Log.d(TAG, "onButtonLongPress:" + button);
    if (cameraApiClient == null || camera.getCameraState() != CameraState.DEFAULT_ACTIVE) {
      return;
    }

    switch (button) {
      case SHUTTER:
        if (isRecording) {
          try {
            cameraApiClient.stopCapture();
          } catch (IOException | CameraApiException e) {
            Log.e(TAG, "Failed to stop capture", e);
          }
        }
        switch (pairingStatus) {
          case ADVERTISING:
            cameraInternalApiClient.cancelPairing();
            break;
          case WAITING_FOR_USER_CONFIRMATION:
            cameraInternalApiClient.confirmPairing();
            break;
          case USER_CONFIRMATION_TIMEOUT:
          case DEFAULT_NOT_ADVERTISING:
          case PAIRED:
            cameraInternalApiClient.startPairing();
            break;
        }
        break;
      case MODE:
        switchMode();
        break;
      case POWER:
        powerOff(true);
        break;
      default:
        Log.e(TAG, "Unhandled long press.");
        break;
    }
  }

  @Override
  public void onHdmiStateChanged(boolean connected) {
    Log.d(TAG, "HDMI connected: " + connected);
    if (connected) {
      Intent intent = new Intent(this, MainActivity.class);
      intent.putExtra(MainActivity.EXTRA_RECEIVE_STOP_ACTION, true);
      startActivity(intent);
    } else {
      LocalBroadcastManager.getInstance(this)
          .sendBroadcast(new Intent(MainActivity.STOP_ACTIVITY_ACTION));
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && ACTION_AUTO_POWER_OFF.equals(intent.getAction())) {
      if (((BatteryManager) getSystemService(Context.BATTERY_SERVICE)).isCharging()) {
        // If the device is charging, we do not power off. Instead, we schedule another check
        // after 30 min.
        scheduleAutoPowerOff();
      } else {
        powerOff(false);
      }
      return START_STICKY;
    }
    onResume();
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    onPause();
    unregisterReceiver(sleepModeReceiver);
    subscriptions.dispose();
    hardware.stop();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    onResume();
    return new Binder();
  }

  private void switchMode() {
    try {
      switch (captureType) {
        case VIDEO:
          cameraApiClient.setCaptureType(CaptureType.PHOTO);
          break;
        case PHOTO:
        case LIVE:
          cameraApiClient.setCaptureType(CaptureType.VIDEO);
          break;
        default:
          Log.e(TAG, "Failed to switch capture type: unknown capture type.");
          break;
      }
    } catch (CameraApiException | IOException e) {
      Log.e(TAG, "Failed to switch capture type", e);
    }
  }

  private void onResume() {
    camera.onResume();
    updateHardwareMode();
    cancelAutoPowerOff();
    // Do not control WiFi/BT when running on an emulator (phone).
    if (!InstanceMap.get(DeviceInfo.class).isEmulator()) {
      wifiManager.setWifiEnabled(true);
      BluetoothAdapter.getDefaultAdapter().enable();
    }
  }

  private void onPause() {
    if (pairingStatus == PairingStatus.ADVERTISING
        || pairingStatus == PairingStatus.WAITING_FOR_USER_CONFIRMATION) {
      cameraInternalApiClient.cancelPairing();
    }
    camera.onPause();
    updateHardwareMode();
    // Do not control WiFi/BT/Power when running on an emulator (phone).
    if (!InstanceMap.get(DeviceInfo.class).isEmulator()) {
      wifiManager.setWifiEnabled(false);
      BluetoothAdapter.getDefaultAdapter().disable();
      scheduleAutoPowerOff();
    }
  }

  private void updateRecordingState(boolean isRecording) {
    this.isRecording = isRecording;
    updateWakeLock();
    updateHardwareMode();
  }

  private void updateWakeLock() {
    if (isRecording) {
      wakeLock.acquire();
    } else {
      wakeLock.release();
    }
  }

  private void updateCaptureType(CaptureType captureType) {
    this.captureType = captureType;
    updateHardwareMode();
  }

  private void updatePairingStatus(PairingStatus pairingStatus) {
    this.pairingStatus = pairingStatus;
    updateHardwareMode();
  }

  private void updateHardwareMode() {
    if (camera.getCameraState() != CameraState.DEFAULT_ACTIVE) {
      hardware.setMode(Mode.OFF);
      return;
    }

    switch (pairingStatus) {
      case ADVERTISING:
        hardware.setMode(Mode.PAIRING_SEARCHING);
        break;
      case WAITING_FOR_USER_CONFIRMATION:
        hardware.setMode(Mode.PAIRING_WAITING_CONFIRMATION);
        break;
      case USER_CONFIRMATION_TIMEOUT:
      case DEFAULT_NOT_ADVERTISING:
      case PAIRED:
        switch (captureType) {
          case PHOTO:
            hardware.setMode(Mode.IDLE_PHOTO);
            break;
          case VIDEO:
            hardware.setMode(isRecording ? Mode.VIDEO_RECORDING : Mode.IDLE_VIDEO);
            break;
          case LIVE:
            hardware.setMode(isRecording ? Mode.LIVE_STREAMING : Mode.IDLE_LIVE);
            break;
          default:
            Log.e(TAG, "Unsupported capture type");
            hardware.setMode(Mode.IDLE_VIDEO);
            break;
        }
        break;
    }
  }

  private void notifyServiceCreation() {
    Ringtone ringtone =
        RingtoneManager.getRingtone(
            getApplicationContext(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
    ringtone.setVolume(0.25f);
    ringtone.play();
  }

  private void notifyStartCapture() {
    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100 /* ms */);
    if (captureType == CaptureType.PHOTO) {
      // Add a blink for photo capture
      hardware.setMode(Mode.OFF);
      mainHandler.postDelayed(this::updateHardwareMode, 300 /* ms */);
    }
  }

  private void notifyInternalError() {
    Log.i(TAG, "Internal error");
    // Use a 3-time beep sound to indicate an error.
    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 750 /* ms */);
  }

  private void notifyPowerOff() {
    RingtoneManager
        .getRingtone(
            getApplicationContext(),
            Uri.fromFile(new File(POWER_OFF_SOUND_PATH)))
        .play();
  }

  private void powerOff(boolean shouldNotify) {
    Log.d(TAG, "Powering off");
    PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
    try {
      if (shouldNotify) {
        notifyPowerOff();
      }
      Method method =
          PowerManager.class.getMethod("shutdown", Boolean.TYPE, String.class, Boolean.TYPE);
      method.invoke(
          powerManager,
          /* confirm = */ false,
          /* reason = */ "Power button long press",
          /* wait = */ false);
    } catch (Exception e) {
      Log.e(TAG, "Failed to power off the device.", e);
    }
  }

  private void scheduleAutoPowerOff() {
    Log.d(TAG, "Auto power off scheduled after " + AUTO_SHUTDOWN_TIME_MS + "ms");
    PendingIntent pendingIntent = getAutoPowerOffPendingIntent();
    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        System.currentTimeMillis() + AUTO_SHUTDOWN_TIME_MS,
        pendingIntent);
  }

  private void cancelAutoPowerOff() {
    Log.d(TAG, "Auto power off canceled");
    PendingIntent pendingIntent = getAutoPowerOffPendingIntent();
    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    alarmManager.cancel(pendingIntent);
  }

  private PendingIntent getAutoPowerOffPendingIntent() {
    Intent intent = new Intent(ACTION_AUTO_POWER_OFF, null, this, MainService.class);
    return
        PendingIntent.getService(
            this, AUTO_POWER_OFF_REQUEST_CODE, intent, PendingIntent.FLAG_CANCEL_CURRENT);
  }
}
