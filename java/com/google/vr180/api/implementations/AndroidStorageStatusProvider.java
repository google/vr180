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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Environment;
import android.os.StatFs;
import com.google.common.base.Optional;
import com.google.vr180.CameraApi.CameraStatus.StorageStatus;
import com.google.vr180.CameraApi.CameraStatus.StorageStatus.SdCardStatus;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.common.logging.Log;
import java.io.File;

/** Implements StorageStatusProvider using the android file apis. */
public class AndroidStorageStatusProvider implements StorageStatusProvider {
  private static final String TAG = "AndroidStorageStatusProvider";

  private final StatusNotifier statusNotifier;
  private final String mediaFolderRelativePath;
  private final String internalStoragePath;
  private final Context context;
  private final BroadcastReceiver sdCardReceiver = new BroadcastReceiver(){
    @Override
    public void onReceive(Context arg0, Intent intent) {
      if (Intent.ACTION_MEDIA_MOUNTED.equals(intent.getAction())) {
        onSdCardMounted(intent.getData().getPath());
      } else if (Intent.ACTION_MEDIA_EJECT.equals(intent.getAction())) {
        // We use ACTION_MEDIA_EJECT instead of ACTION_MEDIA_UNMOUNTED because SD card is unusable
        // after EJECT.
        // EJECT happens when the user presses the SD card, usually a few seconds before UNMOUNTED.
        onSdCardEjected();
      }
    }
  };

  private String externalStoragePath;

  /**
   * Constructs a storage status provider that looks at the available and total storage and provides
   * base paths for internal and external storage.
   *
   * @param mediaFolderRelativePath The path to media folder relative to the storage root path.
   */
  public AndroidStorageStatusProvider(
      Context context,
      String mediaFolderRelativePath,
      StatusNotifier statusNotifier) {
    this.context = context;
    this.statusNotifier = statusNotifier;
    this.mediaFolderRelativePath = mediaFolderRelativePath;
    this.internalStoragePath =
        Environment.getExternalStorageDirectory().getAbsolutePath() + mediaFolderRelativePath;
    createFolderIfMissing(internalStoragePath);

    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
    filter.addAction(Intent.ACTION_MEDIA_EJECT);
    filter.addDataScheme("file");
    context.registerReceiver(sdCardReceiver, filter);
    checkExternalSdCard();
  }

  /** Gets the storage status for the camera. */
  @Override
  public StorageStatus getStorageStatus() {
    StorageStatus.Builder storageStatus = StorageStatus.newBuilder();

    StatFs statFs = new StatFs(internalStoragePath);
    storageStatus
        .setCardStatus(SdCardStatus.OK)
        .setInternalFreeSpace(statFs.getAvailableBytes())
        .setInternalTotalSpace(statFs.getTotalBytes());

    if (externalStoragePath != null) {
      try {
        statFs = new StatFs(externalStoragePath);
      } catch (Exception e) {
        Log.d(TAG, "Failed to get SD card status.", e);
      }
    }

    // By design, the free space and total space should be SD card space.
    // However, currently VR180 app doesn't work well if SD card is absent but internal space is
    // available, so we set free and total space to internal space when there is no SD card.
    storageStatus
        .setFreeSpace(statFs.getAvailableBytes())
        .setTotalSpace(statFs.getTotalBytes());

    return storageStatus.build();
  }

  @Override
  public Optional<String> getInternalStoragePath() {
    return Optional.fromNullable(internalStoragePath);
  }

  @Override
  public Optional<String> getExternalStoragePath() {
    return Optional.fromNullable(externalStoragePath);
  }

  /**
   * Gets the path to save photos or video captures.
   *
   * @return If external SD card is available, returns the path to the SD card. Otherwise, returns
   * the path to internal storage.
   */
  @Override
  public Optional<String> getWriteBasePath() {
    // Only system apps (under /system/priv-app/) have write access to external storage.
    // The check is to prevent crash during development when installing the app with adb.
    if (isSystemApp() && externalStoragePath != null) {
      Log.d(TAG, "Using external storage for write: " + externalStoragePath);
      return Optional.of(externalStoragePath);
    } else {
      Log.d(TAG, "Using internal storage for write: " + internalStoragePath);
      return Optional.fromNullable(internalStoragePath);
    }
  }

  @Override
  public boolean isValidPath(String path) {
    if (path == null) {
      return false;
    }

    if (internalStoragePath != null && path.startsWith(internalStoragePath)) {
      return true;
    }

    return externalStoragePath != null && path.startsWith(externalStoragePath);
  }


  /**
   * Checks the initial state of the external SD card.
   *
   * There doesn't seem to be a standard Android API to check the presence of an external SD card.
   * Environment.getExternalStorageDirectory() returns the path to internal SD card only, and the
   * broadcasts are not sticky so we can't get the initial state.
   */
  private void checkExternalSdCard() {
    File storageFolder = new File("/storage/");
    File[] files = storageFolder.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()
          && !file.getName().equals("emulated")
          && !file.getName().equals("self")) {
        onSdCardMounted(file.getAbsolutePath());
        break;
      }
    }
  }

  private void onSdCardMounted(String sdCardPath) {
    Log.d(TAG, "SD card mounted: " + sdCardPath);
    String mediaFolderPath = sdCardPath + mediaFolderRelativePath;
    if (isSystemApp()) {
      createFolderIfMissing(mediaFolderPath);
    } else {
      Log.e(TAG, "App is not a system app so it does not have permission to write sd card.");
    }
    this.externalStoragePath = mediaFolderPath;
    statusNotifier.notifyStatusChanged();
  }

  private void onSdCardEjected() {
    Log.d(TAG, "SD card ejected.");
    this.externalStoragePath = null;
    statusNotifier.notifyStatusChanged();
  }

  private void createFolderIfMissing(String path) {
    File file = new File(path);
    if (!file.exists()) {
      Log.d(TAG, "Creating folder " + path);
      file.mkdirs();
    }
  }

  private boolean isSystemApp() {
    int mask = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
    return (context.getApplicationInfo().flags & mask) != 0;
  }
}
