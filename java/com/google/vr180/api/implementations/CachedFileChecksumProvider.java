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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.vr180.CameraApi.FileChecksum;
import com.google.vr180.api.camerainterfaces.FileChecksumProvider;
import com.google.vr180.api.camerainterfaces.FileProvider;
import com.google.vr180.common.logging.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Provider for file checksums that computes the checksum on the first request and caches it in a
 * local database.
 */
public class CachedFileChecksumProvider implements FileChecksumProvider, AutoCloseable {
  private static final String TAG = "CachedFileChecksumProvider";

  private static final int VERSION = 1;
  /** The name of the table (and database) containing checksum caches. */
  private static final String TABLE_NAME = "ChecksumDatabase";
  /** Name of the id column. */
  private static final String ROW_ID = "_id";
  /** Name of the path column, containing the media path for the cached checksum. */
  private static final String PATH = "path";
  /** Name of the checksum column, which contains a protobuf of data about the checksum. */
  private static final String CHECKSUM = "checksum";
  /**
   * Name of the last_modified column, which contains the last modified time of the file when we
   * computed the checksum.
   */
  private static final String LAST_MODIFIED = "last_modified";

  /** Selection query to find a specific checksum. */
  private static final String SELECT_PATH_AND_DATE = PATH + " like ? AND " + LAST_MODIFIED + " = ?";

  private final FileProvider fileProvider;
  private final SQLiteDatabase db;
  private Executor computationExecutor = Executors.newSingleThreadExecutor();

  /**
   * Constructs a new FileChecksumProvider using the specified fileProvider for opening the file to
   * compute a checksum initially.
   *
   * @param context The android context for accessing MediaStore.
   * @param fileProvider A FileProvider instance to open the file.
   */
  public CachedFileChecksumProvider(Context context, FileProvider fileProvider) {
    this.fileProvider = fileProvider;
    ChecksumDatabaseHelper dbHelper = new ChecksumDatabaseHelper(context);
    db = dbHelper.getWritableDatabase();
  }

  /** Returns the SHA256 checksum of the file, caching it in a database if not already computed. */
  @Override
  public FileChecksum getFileChecksum(String path) throws IOException {
    long lastModified = fileProvider.getLastModified(path).getTime();
    FileChecksum result = getCachedChecksum(path, lastModified);
    if (result != null) {
      return result;
    }

    computationExecutor.execute(() -> updateCachedChecksum(path, lastModified));
    return null;
  }

  /** Releases the database. */
  @Override
  public void close() {
    db.close();
  }

  /**
   * Overrides the executor used for computing file checksums. Used in unit tests to ensure
   * synchronous execution.
   *
   * @param executor The executor to use
   */
  @VisibleForTesting
  void setComputationExecutor(Executor executor) {
    computationExecutor = executor;
  }

  /** Returns the cached file checksum, or null if no cache is available. */
  private FileChecksum getCachedChecksum(String path, long lastModified) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    qb.setTables(TABLE_NAME);
    Cursor cursor = null;
    try {
      cursor =
          qb.query(
              db,
              null,
              SELECT_PATH_AND_DATE,
              new String[] {path, Long.toString(lastModified)},
              null,
              null,
              null);
    } catch (Exception e) {
      Log.e(TAG, "Error querying cached checksums.", e);
      return null;
    }

    Preconditions.checkState(cursor.getCount() <= 1);
    int checksumIndex = cursor.getColumnIndex(CHECKSUM);
    while (cursor.moveToNext()) {
      try {
        return FileChecksum.parseFrom(cursor.getBlob(checksumIndex));
      } catch (InvalidProtocolBufferException e) {
        Log.e(TAG, "Unable to parse file checksum.", e);
        continue;
      }
    }

    return null;
  }

  /** Computes the checkusm and saves it to the cache. */
  private void updateCachedChecksum(String path, long lastModified) {
    try {
      ContentValues values = new ContentValues();
      values.put(PATH, path);
      values.put(CHECKSUM, computeChecksum(path).toByteArray());
      values.put(LAST_MODIFIED, lastModified);
      db.insertWithOnConflict(TABLE_NAME, "", values, SQLiteDatabase.CONFLICT_REPLACE);
    } catch (IOException e) {
      Log.e(TAG, "Unable to compute checksum: " + path, e);
    }
  }

  /** Compute the file checksum. */
  private FileChecksum computeChecksum(String path) throws IOException {
    byte[] hash = byteSourceForFile(path).hash(Hashing.sha1()).asBytes();
    return FileChecksum.newBuilder()
        .setChecksumType(FileChecksum.ChecksumType.SHA1)
        .setChecksum(ByteString.copyFrom(hash))
        .build();
  }

  private ByteSource byteSourceForFile(String path) {
    return new ByteSource() {
      @Override
      public InputStream openStream() throws IOException {
        return fileProvider.openFile(path);
      }
    };
  }

  private static class ChecksumDatabaseHelper extends SQLiteOpenHelper {
    /** Instantiates an open helper for the provider's SQLite data repository. */
    ChecksumDatabaseHelper(Context context) {
      super(context, TABLE_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(
          "CREATE TABLE "
              + TABLE_NAME
              + " ("
              + ROW_ID
              + " INTEGER PRIMARY KEY,"
              + PATH
              + " TEXT UNIQUE,"
              + CHECKSUM
              + " BLOB NOT NULL,"
              + LAST_MODIFIED
              + " INTEGER NOT NULL"
              + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      throw new UnsupportedOperationException("Upgrade should not be called.");
    }
  }
}
