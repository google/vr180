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

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.MediaStore;

/**
 * A fake implementation of the content provider in MediaStore. The provider is functional
 * and can be used to populate the android media store in tests.
 * Example usage:
 *    ProviderInfo info = new ProviderInfo();
 *    info.authority = FakeMediaProvider.AUTHORITY;
 *    Robolectric.buildContentProvider(FakeMediaProvider.class).create(info);
 *    shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypMapping("jpg", "image/jpeg");
 */
public class FakeMediaProvider extends ContentProvider {
  public static final String AUTHORITY = MediaStore.AUTHORITY;
  private static final String TABLE_NAME = "external";
  private static final int MATCH_MEDIA_ID = 1;
  private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
  static {
    uriMatcher.addURI(AUTHORITY, TABLE_NAME + "/files/media/#", MATCH_MEDIA_ID);
  }

  private SQLiteDatabase db;

  @Override
  public boolean onCreate() {
    db = SQLiteDatabase.create(null);
    db.execSQL(createTableStatement());
    return true;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
      String sortOrder) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    qb.setTables(TABLE_NAME);
    int uriType = uriMatcher.match(uri);
    if (uriType == MATCH_MEDIA_ID) {
      qb.appendWhere(MediaStore.MediaColumns._ID + "=" + uri.getLastPathSegment());
    }
    Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
    cursor.setNotificationUri(getContext().getContentResolver(), uri);
    return cursor;
  }

  @Override
  public String getType(Uri uri) {
    return "image/jpeg";
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    long id = db.insert(TABLE_NAME, "", values);
    Uri result = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id);
    getContext().getContentResolver().notifyChange(uri, null);
    return result;
  }

  @Override
  public int delete(Uri uri, String where, String[] whereArgs) {
    int uriType = uriMatcher.match(uri);
    if (uriType == MATCH_MEDIA_ID) {
      if (where == null && whereArgs == null) {
        where = MediaStore.MediaColumns._ID + " = ?";
        whereArgs = new String[]{uri.getLastPathSegment()};
      } else {
        // Not implemented.
      }
    }

    int count = db.delete(TABLE_NAME, where, whereArgs);
    getContext().getContentResolver().notifyChange(uri, null);
    return count;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    int count = db.update(TABLE_NAME, values, selection, selectionArgs);
    getContext().getContentResolver().notifyChange(uri, null);
    return count;
  }

  private static String createTableStatement() {
    return "CREATE TABLE " + TABLE_NAME + " ("
        + MediaStore.MediaColumns._ID + " INTEGER PRIMARY KEY, "
        + MediaStore.MediaColumns.DATA + " TEXT NOT NULL,"
        + MediaStore.MediaColumns.TITLE + " TEXT NOT NULL,"
        + MediaStore.MediaColumns.DISPLAY_NAME + " TEXT NOT NULL,"
        + MediaStore.MediaColumns.MIME_TYPE + " TEXT NOT NULL,"
        + MediaStore.MediaColumns.WIDTH + " INTEGER NOT NULL, "
        + MediaStore.MediaColumns.HEIGHT + " INTEGER NOT NULL, "
        + MediaStore.MediaColumns.SIZE + " INTEGER NOT NULL, "
        + MediaStore.MediaColumns.DATE_MODIFIED + " INTEGER NOT NULL, "
        + MediaStore.MediaColumns.DATE_ADDED + " INTEGER NOT NULL, "
        + MediaStore.Video.VideoColumns.DATE_TAKEN + " INTEGER, "
        + MediaStore.Video.VideoColumns.DURATION + " INTEGER "
        + ")";
  }
}
