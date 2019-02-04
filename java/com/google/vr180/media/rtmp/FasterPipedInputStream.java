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

package com.google.vr180.media.rtmp;

import com.google.vr180.common.logging.Log;
import java.io.IOException;
import java.io.PipedInputStream;

/**
 * A modified {@link PipedInputStream} that is able to receive entire byte arrays at once.
 */
public class FasterPipedInputStream extends PipedInputStream {
  private static final String TAG = "FasterPipedInputStream";

  private static final int WAIT_TIME_MS = 1000;

  private final int pipeSize;
  private int bufferLimit = Integer.MAX_VALUE;

  public FasterPipedInputStream(int pipeSize) {
    super(pipeSize);
    this.pipeSize = pipeSize;
  }

  /**
   * Limits the amount of data that can be stored in the buffer. Attempts to write more than this
   * will cause the writer to block until the buffer is at or below the limit.
   */
  public synchronized void setBufferLimit(int size) {
    if (size > pipeSize) {
      Log.w(TAG, "Attempted to set buffer limit to " + size + " when the pipe size is " + pipeSize);
      bufferLimit = pipeSize;
    } else {
      bufferLimit = size;
    }
    notifyAll();
  }

  /**
   * Receives an array of bytes and stores it in this stream's buffer. This will be called by
   * {@link FasterPipedOutputStream#write(byte[], int, int)}. This will block if the buffer is full.
   */
  synchronized void receive(byte[] incomingBuffer, int offset, int count)
      throws IOException {

    // Block until the buffer is below the bufferLimit.
    notifyAll();
    while (available() > bufferLimit) {
      try {
        wait(WAIT_TIME_MS);
      } catch (InterruptedException e) {
        throw new java.io.InterruptedIOException();
      }
    }

    while (count > 0) {
      // We'll first call the one byte version of receive() as it will manage the last writer
      // thread and do the appropriate waiting if the receiving buffer is full. This will also
      // ensure that {@link in} is not -1. receive() doesn't call notifyAll() for Android N+ except
      // when the buffer is full, so need to call it here.
      receive(incomingBuffer[offset++]);
      notifyAll();
      --count;

      if (in > out) {
        // Write to end of buffer and wrap if necessary
        int bytesToCopy = Math.min(count, buffer.length - in);
        System.arraycopy(incomingBuffer, offset, buffer, in, bytesToCopy);
        in += bytesToCopy;
        offset += bytesToCopy;
        count -= bytesToCopy;
        if (in == buffer.length) {
          in = 0;
        }
        if (count == 0) {
          return;
        }
      }

      // No need to wrap now
      int bytesToCopy = Math.min(count, out - in);
      System.arraycopy(incomingBuffer, offset, buffer, in, bytesToCopy);
      in += bytesToCopy;
      offset += bytesToCopy;
      count -= bytesToCopy;
    }

  }

  @Override
  public synchronized int read() throws IOException {
    int result = super.read();
    notifyAll();
    return result;
  }

  @Override
  public synchronized int read(byte[] b, int off, int len) throws IOException {
    int result = super.read(b, off, len);
    notifyAll();
    return result;
  }
}
