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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.google.vr180.CameraApi.CameraStatus;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.LiveStreamMode;
import com.google.vr180.CameraApi.PhotoMode;
import com.google.vr180.CameraApi.ProjectionType;
import com.google.vr180.CameraApi.VideoMode;
import com.google.vr180.CameraApi.WhiteBalanceMode;
import com.google.vr180.api.CameraApiClient.CameraApiException;
import com.google.vr180.common.InstanceMap;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Manages settings for the camera. */
public class CameraSettingsActivity extends AppCompatActivity {
  private static final String TAG = "CameraSettingsActivity";
  private final CompositeDisposable cameraSubscriptions = new CompositeDisposable();
  private Camera camera;
  private ProgressDialog spinningWheel;

  /**
   * Launches the camera settings activity.
   *
   * @param context The context for launching the activity.
   */
  public static void start(Context context) {
    Intent cameraSettingsIntent = new Intent(context, CameraSettingsActivity.class);
    context.startActivity(cameraSettingsIntent);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    camera = InstanceMap.get(Camera.class);
    if (camera.getCameraCapabilities() == null) {
      finish();
      return;
    }

    setContentView(R.layout.settings_activity);
    configureActionBar();
    setupControls();
  }

  @Override
  protected void onDestroy() {
    cameraSubscriptions.clear();
    super.onDestroy();
  }

  private void configureActionBar() {
    Toolbar toolbar = (Toolbar) findViewById(R.id.app_settings_toolbar);
    toolbar.setTitle(R.string.camera_settings_title);
    setSupportActionBar(toolbar);
  }

  private void setupControls() {
    if (camera.getCameraCapabilities().getSupportedVideoModesCount() > 0) {
      View changeVideoModeView = findViewById(R.id.camera_settings_change_video_mode);
      changeVideoModeView.setVisibility(View.VISIBLE);
      changeVideoModeView.setOnClickListener(v -> showVideoModeSelect());
    }
    if (camera.getCameraCapabilities().getSupportedPhotoModesCount() > 0) {
      View changePhotoModeView = findViewById(R.id.camera_settings_change_photo_mode);
      changePhotoModeView.setVisibility(View.VISIBLE);
      changePhotoModeView.setOnClickListener(v -> showPhotoModeSelect());
    }
    if (camera.getCameraCapabilities().getSupportedLiveModesCount() > 0) {
      View changeLiveStreamModeView = findViewById(R.id.camera_settings_change_live_stream_mode);
      changeLiveStreamModeView.setVisibility(View.VISIBLE);
      changeLiveStreamModeView.setOnClickListener(v -> showLiveStreamModeSelect());
    }
    if (camera.getCameraCapabilities().getSupportedIsoLevelsCount() > 0) {
      View changeIsoView = findViewById(R.id.camera_settings_change_iso_level);
      changeIsoView.setVisibility(View.VISIBLE);
      changeIsoView.setOnClickListener(v -> showIsoSelect());
    }
    if (camera.getCameraCapabilities().getSupportedWhiteBalanceModesCount() > 0) {
      View changeIsoView = findViewById(R.id.camera_settings_change_white_balance_mode);
      changeIsoView.setVisibility(View.VISIBLE);
      changeIsoView.setOnClickListener(v -> showWhiteBalanceSelect());
    }
    if (Boolean.TRUE.equals(camera.getCameraCapabilities().getSupportsFlatColor())) {
      View changeFlatColorView = findViewById(R.id.camera_settings_change_flat_color);
      changeFlatColorView.setVisibility(View.VISIBLE);
      changeFlatColorView.setOnClickListener(v -> showFlatColorModeSelect());
    } else {
      View changeFlatColorView = findViewById(R.id.camera_settings_change_flat_color);
      changeFlatColorView.setVisibility(View.GONE);
    }

    spinningWheel = new ProgressDialog(this);
    spinningWheel.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    spinningWheel.setMessage(this.getString(R.string.camera_settings_change_updating));
    spinningWheel.setIndeterminate(true);
    spinningWheel.setCanceledOnTouchOutside(false);
    cameraSubscriptions.add(
        camera
            .getCameraStatusObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::onCameraStatusChanged));
    // Initialize status
    onCameraStatusChanged(camera.getCameraStatus());
  }

  private void onCameraStatusChanged(CameraStatus status) {
    if (status == null) {
      return;
    }

    updateCaptureModeSettings(status.getActiveCaptureMode());
  }

  /**
   * Updates the UI elements that reflect the active capture mode configuration on the camera.
   *
   * @param activeCaptureMode The configured capture mode
   */
  private void updateCaptureModeSettings(CaptureMode activeCaptureMode) {
    // Update the configured resolution descriptions.
    ((TextView) findViewById(R.id.video_mode))
        .setText(formatVideoMode(activeCaptureMode.getConfiguredVideoMode()));
    ((TextView) findViewById(R.id.photo_mode))
        .setText(formatPhotoMode(activeCaptureMode.getConfiguredPhotoMode()));
    ((TextView) findViewById(R.id.live_mode))
        .setText(formatVideoMode(activeCaptureMode.getConfiguredLiveMode().getVideoMode()));
  }

  private void showVideoModeSelect() {
    showSingleChoiceDialog(
        camera.getCameraCapabilities().getSupportedVideoModesList(),
        camera.getCameraStatus().getActiveCaptureMode().getConfiguredVideoMode(),
        item -> formatVideoMode(item),
        item -> camera.getCameraApiClient().setVideoMode(item),
        R.string.camera_settings_change_video_mode);
  }

  private void showPhotoModeSelect() {
    showSingleChoiceDialog(
        camera.getCameraCapabilities().getSupportedPhotoModesList(),
        camera.getCameraStatus().getActiveCaptureMode().getConfiguredPhotoMode(),
        item -> formatPhotoMode(item),
        item -> camera.getCameraApiClient().setPhotoMode(item),
        R.string.camera_settings_change_photo_mode);
  }

  private void showLiveStreamModeSelect() {
    LiveStreamMode mode =
        camera.getCameraStatus().getActiveCaptureMode().getConfiguredLiveMode();
    showSingleChoiceDialog(
        camera.getCameraCapabilities().getSupportedLiveModesList(),
        mode.getVideoMode(),
        item -> formatVideoMode(item),
        item ->
            camera.getCameraApiClient()
                .setLiveStreamMode(mode.toBuilder().setVideoMode(item).build()),
        R.string.camera_settings_change_live_stream_mode);
  }

  private void showIsoSelect() {
    List<Integer> isos = camera.getCameraCapabilities().getSupportedIsoLevelsList();
    Integer current = null;
    if (camera.getCameraStatus() != null
        && camera.getCameraStatus().hasActiveCaptureMode()) {
      current = camera.getCameraStatus().getActiveCaptureMode().getIsoLevel();
    }
    showSingleChoiceDialog(
        isos,
        current,
        item -> isoNameMapper(item),
        item -> camera.getCameraApiClient().setIsoLevel(item),
        R.string.camera_settings_change_iso_level);
  }

  private void showFlatColorModeSelect() {
    List<Boolean> modes = Arrays.asList(Boolean.TRUE, Boolean.FALSE);
    Boolean current = null;
    if (camera.getCameraStatus() != null
        && camera.getCameraStatus().hasActiveCaptureMode()) {
      current = camera.getCameraStatus().getActiveCaptureMode().getFlatColor();
    }
    showSingleChoiceDialog(
        modes,
        current,
        item -> item.toString(),
        item -> camera.getCameraApiClient().setFlatColor(item),
        R.string.camera_settings_change_flat_color);
  }

  private void showWhiteBalanceSelect() {
    List<WhiteBalanceMode> wbs = camera.getCameraCapabilities().getSupportedWhiteBalanceModesList();
    WhiteBalanceMode current = null;
    if (camera.getCameraStatus() != null
        && camera.getCameraStatus().hasActiveCaptureMode()) {
      current = camera.getCameraStatus().getActiveCaptureMode().getWhiteBalanceMode();
    }
    showSingleChoiceDialog(
        wbs,
        current,
        item -> item.toString(),
        item -> camera.getCameraApiClient().setWhiteBalanceMode(item),
        R.string.camera_settings_change_white_balance_mode);
  }

  private void showSpinningWheel() {
    this.runOnUiThread(() -> spinningWheel.show());
  }

  private void dismissSpinningWheel() {
    this.runOnUiThread(() -> spinningWheel.dismiss());
  }

  private interface ItemAction<T> {
    void onItemSelected(T item) throws CameraApiException, IOException;
  }

  private interface ItemNameMapper<T> {
    String getItemName(T item);
  }

  private <T> void showSingleChoiceDialog(
      List<T> items,
      T currentItem,
      ItemNameMapper<T> nameMapper,
      ItemAction<T> action,
      int titleResourceId) {
    int[] currentItemIndex = new int[] {0};
    CharSequence[] itemNames = new String[items.size()];
    for (int i = 0; i < items.size(); i++) {
      itemNames[i] = nameMapper.getItemName(items.get(i));
      if (items.get(i).equals(currentItem)) {
        currentItemIndex[0] = i;
      }
    }
    int[] selected = new int[] {currentItemIndex[0]};
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder
        .setTitle(titleResourceId)
        .setSingleChoiceItems(itemNames, currentItemIndex[0], (dialog, id) -> selected[0] = id)
        .setPositiveButton(
            android.R.string.ok,
            (dialog, id) -> {
              if (selected[0] != currentItemIndex[0]) {
                showSpinningWheel();
                AsyncTask.execute(
                    () -> {
                      try {
                        action.onItemSelected(items.get(selected[0]));
                        showToast(R.string.camera_settings_change_success);
                      } catch (Exception e) {
                        showToast(R.string.camera_settings_change_failure);
                      } finally {
                        dismissSpinningWheel();
                      }
                    });
              }
            })
        .setNegativeButton(android.R.string.cancel, (dialog, id) -> {})
        .show();
  }

  private void showToast(int msgResourceId) {
    this.runOnUiThread(() -> Toast.makeText(this, msgResourceId, Toast.LENGTH_SHORT).show());
  }

  private String isoNameMapper(Integer iso) {
    if (iso == 0) {
      return getString(R.string.camera_settings_iso_auto);
    }
    return iso.toString();
  }

  private String formatVideoMode(VideoMode videoMode) {
    return
        videoMode.getFrameSize().getFrameWidth()
            + " x " + videoMode.getFrameSize().getFrameHeight()
            + " @" + videoMode.getFramesPerSecond() + "fps"
            + (videoMode.getProjectionType() == ProjectionType.EQUIRECT ? " (E)" : "");
  }

  private String formatPhotoMode(PhotoMode photoMode) {
    return
        photoMode.getFrameSize().getFrameWidth()
            + " x " + photoMode.getFrameSize().getFrameHeight();
  }
}
