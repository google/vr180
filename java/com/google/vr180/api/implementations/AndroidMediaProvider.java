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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.RequestOptions;
import com.google.common.base.Optional;
import com.google.vr180.CameraApi.CameraApiRequest.ThumbnailRequest;
import com.google.vr180.CameraApi.CameraCalibration;
import com.google.vr180.CameraApi.FileChecksum;
import com.google.vr180.CameraApi.Media;
import com.google.vr180.api.camerainterfaces.FileChecksumProvider;
import com.google.vr180.api.camerainterfaces.MediaProvider;
import com.google.vr180.api.camerainterfaces.StatusNotifier;
import com.google.vr180.api.camerainterfaces.StorageStatusProvider;
import com.google.vr180.common.logging.Log;
import com.google.vr180.common.media.BitmapIO;
import com.google.vr180.common.media.St3dBoxParser;
import com.google.vr180.common.media.StereoMode;
import com.google.vr180.common.media.VrVideoCrop;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** A helper class for fetching media on the phone. */
public class AndroidMediaProvider implements MediaProvider {
  private static final String TAG = "MediaProvider";

  private static final String[] COUNT_PROJECTION = {"count(*)"};
  private static final Uri URI = Files.getContentUri("external");
  private static final String SORT_ORDER = Video.VideoColumns.DATE_TAKEN + " DESC";
  private static final String SELECT_DATA = Video.VideoColumns.DATA + " like ? ";
  private static final String SELECT_BOTH = SELECT_DATA + " OR " + SELECT_DATA;
  private static final String SELECT_VIDEO = "%.vr.mp4";
  private static final String SELECT_IMAGE = "%.vr.jpg";

  private final Context context;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final FileChecksumProvider checksumProvider;
  private final int videoStereoMode;
  private final @Nullable StatusNotifier notifier;
  private final StorageStatusProvider storageStatusProvider;
  /**
   * Keep track of the last time we observed a change to the content. Assume content changed on
   * startup.
   */
  private Date lastChangeTime = new Date();

  /**
   * Implementation of MediaProvider that uses the android media store.
   *
   * @param context The application context.
   * @param storageStatusProvider The storage status provider to get internal and external storage
   *     paths.
   * @param checksumProvider An implementation of FileChecksumProvider that can give us the
   *     checksums for media items.
   * @param cameraCalibration The camera calibration data, which is used to determine the stereo
   *     layout of videos (so thumbnails contain only one eye).
   */
  public AndroidMediaProvider(
      Context context,
      StorageStatusProvider storageStatusProvider,
      FileChecksumProvider checksumProvider,
      CameraCalibration cameraCalibration,
      @Nullable StatusNotifier notifier) {
    this.context = context;
    this.storageStatusProvider = storageStatusProvider;
    this.checksumProvider = checksumProvider;
    this.notifier = notifier;
    this.videoStereoMode = parseStereoModeOrDefault(cameraCalibration);
    Log.d(TAG, "StereoMode = " + videoStereoMode);
    registerForContentChanges();
  }

  @Override
  public long getMediaCount() {
    Cursor cursor = queryMedia(URI, COUNT_PROJECTION);
    if (cursor == null) {
      Log.e(TAG, "Unable to get media count cursor.");
      return 0;
    }
    int count = 0;
    if (cursor.moveToFirst()) {
      count = cursor.getInt(0);
    }
    cursor.close();
    return count;
  }

  @Override
  public Date getLastModified() {
    return lastChangeTime;
  }

  @Override
  public List<Media> getMedia(long startIndex, long count) {
    String[] projection = null;
    Uri uri = URI.buildUpon().encodedQuery("limit=" + startIndex + "," + count).build();
    Cursor cursor = queryMedia(uri, projection);
    return buildMediaListFromCursor(cursor);
  }

  @Override
  public Media getMedia(long mediaStoreId) {
    String selection = Video.VideoColumns._ID + " = ?";
    String[] selectionArgs = new String[] {"" + mediaStoreId};
    String[] projection = null;
    Cursor cursor = resolveQuery(URI, projection, selection, selectionArgs);
    List<Media> media = buildMediaListFromCursor(cursor);
    return !media.isEmpty() ? media.get(0) : null;
  }

  @Override
  public List<Long> getMediaIds(long startIndex, long count) {
    ArrayList<Long> mediaIds = new ArrayList<>();
    Uri uri = URI.buildUpon().encodedQuery("limit=" + startIndex + "," + count).build();
    String[] projection = {Video.VideoColumns._ID, Video.VideoColumns.DATA};
    Cursor cursor = queryMedia(uri, projection);
    if (cursor == null) {
      Log.e(TAG, "Unable to get media cursor.");
      return mediaIds;
    }

    int dataIndex = cursor.getColumnIndex(Video.VideoColumns.DATA);
    int index = cursor.getColumnIndex(Video.VideoColumns._ID);
    while (cursor.moveToNext()) {
      String path = cursor.getString(dataIndex);
      if (!storageStatusProvider.isValidPath(path)) {
        continue;
      }
      mediaIds.add(cursor.getLong(index));
    }
    cursor.close();
    return mediaIds;
  }

  @Override
  public long getMediaStoreId(String filename) {
    long mediaStoreId = -1;
    String selection = SELECT_DATA;
    String[] selectionArgs = new String[]{filename};
    String[] projection = {Video.VideoColumns._ID};
    Cursor cursor = resolveQuery(URI, projection, selection, selectionArgs);
    if (cursor == null) {
      Log.e(TAG, "Unable to get media cursor.");
      return mediaStoreId;
    }

    int index = cursor.getColumnIndex(Video.VideoColumns._ID);
    if (cursor.moveToFirst()) {
      mediaStoreId = cursor.getLong(index);
      cursor.close();
    }
    return mediaStoreId;
  }

  /** Gets a webp encoded thumbnail of a media item based on it's path. */
  @Override
  public byte[] getThumbnail(ThumbnailRequest request) {
    Bitmap thumbnail = null;
    try {
      File filePath = new File(request.getFilename());
      RequestBuilder<Bitmap> glideRequest = Glide.with(context).asBitmap().load(filePath);
      // When creating a thumbnail for a video, only use the left eye.
      if (com.google.common.io.Files.getFileExtension(request.getFilename()).equals("mp4")) {
        glideRequest.apply(
            new RequestOptions().transform(new VrVideoCrop(videoStereoMode)));
      } else {
        glideRequest.apply(new RequestOptions().transform(new CenterCrop()));
      }
      thumbnail = glideRequest.submit(request.getWidth(), request.getHeight()).get();
    } catch (ExecutionException | InterruptedException e) {
      Log.e(TAG, "Failed to create thumbnail with glide");
      thumbnail = null;
    }
    return BitmapIO.toWebpByteArray(thumbnail, request.getQuality());
  }

  @Override
  public void onMediaStateChanged() {
    lastChangeTime = new Date();
    if (notifier != null) {
      notifier.notifyStatusChanged();
    }
  }

  protected boolean buildMediaItem(Media.Builder mediaBuilder, long id, Cursor cursor) {
    int dataIndex = cursor.getColumnIndex(Video.VideoColumns.DATA);
    int sizeIndex = cursor.getColumnIndex(Video.VideoColumns.SIZE);
    int dateTakenIndex = cursor.getColumnIndex(Video.VideoColumns.DATE_TAKEN);
    int durationIndex = cursor.getColumnIndex(Video.VideoColumns.DURATION);
    int heightIndex = cursor.getColumnIndex(Video.VideoColumns.HEIGHT);
    int widthIndex = cursor.getColumnIndex(Video.VideoColumns.WIDTH);
    String path = cursor.getString(dataIndex);
    if (!storageStatusProvider.isValidPath(path)) {
      return false;
    }
    long size = cursor.getLong(sizeIndex);
    long dateTaken = cursor.getLong(dateTakenIndex);
    mediaBuilder
        .setFilename(path)
        .setSize(size)
        .setTimestamp(dateTaken);
    if (!cursor.isNull(durationIndex)) {
      mediaBuilder.setDuration(cursor.getLong(durationIndex));
    }
    if (!cursor.isNull(heightIndex)) {
      mediaBuilder.setHeight(cursor.getLong(heightIndex));
    }
    if (!cursor.isNull(widthIndex)) {
      mediaBuilder.setWidth(cursor.getLong(widthIndex));
    }

    try {
      FileChecksum checksum = checksumProvider.getFileChecksum(path);
      if (checksum != null) {
        mediaBuilder.addChecksum(checksum);
      }
    } catch (IOException e) {
      Log.e(TAG, "Unable to get checksum for " + path, e);
    }
    return true;
  }

  private List<Media> buildMediaListFromCursor(Cursor cursor) {
    ArrayList<Media> media = new ArrayList<>();
    if (cursor == null) {
      Log.e(TAG, "Unable to get media cursor.");
      return media;
    }

    int index = cursor.getColumnIndex(Video.VideoColumns._ID);
    while (cursor.moveToNext()) {
      long id = cursor.getLong(index);
      Media.Builder mediaBuilder = Media.newBuilder();
      boolean isValid = buildMediaItem(mediaBuilder, id, cursor);
      if (!isValid) {
        return media;
      }
      media.add(mediaBuilder.build());
    }
    cursor.close();
    return media;
  }

  private Cursor resolveQuery(
      Uri uri, String[] projection, String selection, String[] selectionArgs) {
    ContentResolver resolver = context.getContentResolver();
    Cursor cursor = null;
    try {
      cursor = resolver.query(uri, projection, selection, selectionArgs, SORT_ORDER);
    } catch (Exception e) {
      Log.e(TAG, "Error resolving query.", e);
      return null;
    }
    return cursor;
  }

  private void registerForContentChanges() {
    ContentResolver resolver = context.getContentResolver();
    MediaContentObserver mediaContentObserver = new MediaContentObserver();
    resolver.registerContentObserver(Images.Media.EXTERNAL_CONTENT_URI, true, mediaContentObserver);
    resolver.registerContentObserver(Video.Media.EXTERNAL_CONTENT_URI, true, mediaContentObserver);
  }

  private static int parseStereoModeOrDefault(CameraCalibration cameraCalibration) {
    try {
      return St3dBoxParser.parseStereoMode(cameraCalibration.getSt3DBox().toByteArray());
    } catch (ParseException e) {
      // Default to mono if we can't parse the box.
      return StereoMode.MONO;
    }
  }

  /** Content observer that listens for changes to the media. */
  private class MediaContentObserver extends ContentObserver {
    public MediaContentObserver() {
      super(mainHandler);
    }

    @Override
    public void onChange(boolean selfChange) {
      onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
      Log.d(TAG, "Media content changed: " + uri);
      onMediaStateChanged();
      // Notify that there was a media store change.
      context.sendBroadcast(new Intent(MediaProvider.ACTION_NEW_MEDIA));
    }
  }

  private Cursor queryMedia(Uri uri, String[] projection) {
    Optional<String> internalStoragePath = storageStatusProvider.getInternalStoragePath();
    Optional<String> externalStoragePath = storageStatusProvider.getExternalStoragePath();

    List<String> paths = new ArrayList<>();
    if (internalStoragePath.isPresent()) {
      paths.add(internalStoragePath.get() + SELECT_VIDEO);
      paths.add(internalStoragePath.get() + SELECT_IMAGE);
    }

    if (externalStoragePath.isPresent()) {
      paths.add(externalStoragePath.get() + SELECT_VIDEO);
      paths.add(externalStoragePath.get() + SELECT_IMAGE);
    }

    String selection;
    switch (paths.size()) {
      case 2:
        selection = SELECT_BOTH;
        break;
      case 4:
        selection = SELECT_BOTH + " OR " + SELECT_BOTH;
        break;
      default:
        selection = "";
        break;
    }

    return resolveQuery(
        uri,
        projection,
        selection,
        paths.toArray(new String[0]));
  }
}
