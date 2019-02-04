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

package com.google.vr180.capture.photo;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import com.google.vr180.common.logging.Log;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

/** Class for queueing photo requests and processing them on a separate io thread. */
public class PhotoCaptureQueue implements ImageReader.OnImageAvailableListener {

  private static final String TAG = "PhotoCaptureQueue";
  private static final int DEFAULT_QUEUE_SIZE = 4;

  /** Data class for storing photo request data. */
  private static class PhotoRequest {
    public final int id;
    public final String path;
    public final long timestampMs;

    public PhotoRequest(int id, String path) {
      this.id = id;
      this.path = path;
      timestampMs = SystemClock.elapsedRealtime();
    }
  }

  private final PhotoCapturer processor;
  private final int size;
  private final ArrayDeque<PhotoRequest> requests;
  private final AtomicInteger processingRequests;
  private final HandlerThread thread = new HandlerThread(TAG);
  private final Handler handler;

  public PhotoCaptureQueue(PhotoCapturer processor) {
    this(processor, DEFAULT_QUEUE_SIZE);
  }

  public PhotoCaptureQueue(PhotoCapturer processor, int size) {
    this.processor = processor;
    this.size = size;
    requests = new ArrayDeque<>();
    processingRequests = new AtomicInteger();
    thread.start();
    handler = new Handler(thread.getLooper());
  }

  public int size() {
    return size;
  }

  public synchronized boolean isFull() {
    return requests.size() + processingRequests.get() >= size;
  }

  public synchronized boolean hasPendingRequests() {
    return !requests.isEmpty() || processingRequests.get() > 0;
  }

  public synchronized void addPhotoRequest(int id, String path) {
    requests.addLast(new PhotoRequest(id, path));
  }

  @Override
  public synchronized void onImageAvailable(ImageReader reader) {
    try {
      final Image img = reader.acquireNextImage();
      if (img == null) {
        return;
      }
      final PhotoRequest request = requests.pollFirst();
      if (request != null) {
        Log.i(TAG, "Process photo #" + request.id + "@" + img.getFormat() + ": " + request.path);
        processingRequests.getAndIncrement();
        handler.post(
            () -> {
              processor.processPhoto(img, request.id, request.path);
              processingRequests.getAndDecrement();
              Log.i(TAG, "Latency=" + (SystemClock.elapsedRealtime() - request.timestampMs) + "ms");
            });
      } else {
        img.close();
      }
    } catch (Exception e) {
      Log.e(TAG, "Exeception trying to aquire image: ", e);
    }
  }

  public synchronized void close() {
    thread.quitSafely();
  }
}
