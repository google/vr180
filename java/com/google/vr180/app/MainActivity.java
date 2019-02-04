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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.google.vr180.api.internal.CameraInternalApiClient;
import com.google.vr180.app.stubs.Emulator;
import com.google.vr180.common.InstanceMap;
import com.google.vr180.common.logging.Log;
import com.google.vr180.device.DeviceInfo;
import com.google.vr180.device.Hardware;
import io.reactivex.disposables.CompositeDisposable;

/** The main activity for the Camera app. */
public class MainActivity extends AppCompatActivity {
  // Broadcast action used to stop the activity.
  public static final String STOP_ACTIVITY_ACTION = "STOP_ACTIVITY_ACTION";
  // An intent extra to indicate whether the activity should receive stop action.
  // If it is started from Service, then yes; if it is started from the launcher, then no.
  public static final String EXTRA_RECEIVE_STOP_ACTION = "EXTRA_RECEIVE_STOP_ACTION";

  private static final String TAG = "MainActivity";

  private final CompositeDisposable subscriptions = new CompositeDisposable();

  private final ServiceConnection serviceConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
          Log.d(TAG, "MainService connected.");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
          Log.d(TAG, "MainService disconnected.");
        }
      };

  private final BroadcastReceiver stopCommandReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          finish();
        }
      };

  private Camera camera;
  private CameraInternalApiClient cameraInternalApiClient;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.camera_main_activity);
    camera = InstanceMap.get(Camera.class);

    cameraInternalApiClient = camera.getInternalApiClient();
    GLSurfaceView captureView = (GLSurfaceView) findViewById(R.id.capture_view);
    cameraInternalApiClient.setViewfinderView(captureView);
    captureView.setVisibility(View.VISIBLE);

    // MainService will notify the activity to stop when HDMI is disconnected.
    if (getIntent().getBooleanExtra(EXTRA_RECEIVE_STOP_ACTION, false)) {
      LocalBroadcastManager.getInstance(this)
          .registerReceiver(stopCommandReceiver, new IntentFilter(STOP_ACTIVITY_ACTION));
    }

    if (isEmulator()) {
      // For emulator, bind to the service so that it will stop upon exiting the activity.
      bindService(new Intent(this, MainService.class), serviceConnection, BIND_AUTO_CREATE);
      ((Emulator) InstanceMap.get(Hardware.class))
          .setViews(
              (Button) findViewById(R.id.shutter_button),
              (Button) findViewById(R.id.mode_button),
              (TextView) findViewById(R.id.mode_view));
    } else {
      // For an actual camera, make sure the MainService is started and always running.
      startService(new Intent(this, MainService.class));
      findViewById(R.id.shutter_button).setVisibility(View.INVISIBLE);
      findViewById(R.id.mode_button).setVisibility(View.INVISIBLE);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    hideStatusBar(this);
  }

  public void onSettingsButtonClick(View view) {
    CameraSettingsActivity.start(this);
  }

  @Override
  protected void onDestroy() {
    subscriptions.dispose();
    if (cameraInternalApiClient != null) {
      cameraInternalApiClient.setViewfinderView(null);
    }
    if (isEmulator()) {
      ((Emulator) InstanceMap.get(Hardware.class)).setViews(null, null, null);
      unbindService(serviceConnection);
    }
    if (getIntent().getBooleanExtra(EXTRA_RECEIVE_STOP_ACTION, false)) {
      LocalBroadcastManager.getInstance(this).unregisterReceiver(stopCommandReceiver);
    }
    super.onDestroy();
  }

  /** Hide the status bar. */
  private static void hideStatusBar(AppCompatActivity activity) {
    View decorView = activity.getWindow().getDecorView();
    decorView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | (isEmulator() ? 0 : View.SYSTEM_UI_FLAG_HIDE_NAVIGATION));
  }

  private static boolean isEmulator() {
    return InstanceMap.get(DeviceInfo.class).isEmulator();
  }
}
