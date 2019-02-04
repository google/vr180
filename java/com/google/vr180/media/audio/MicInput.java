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

package com.google.vr180.media.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.audiofx.AutomaticGainControl;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.common.base.Preconditions;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.MediaConstants;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Implementation of the {@link AudioInput} interface for a microphone source. Executes on its own
 * thread and posts results on a separate thread,
 */
public final class MicInput implements AudioInput {

  private static final String TAG = "MicInput";

  private static final int BYTES_PER_SAMPLE = 2;
  private static final int MOVING_AVG_WINDOW_SIZE = 50;
  private static final long JOIN_WAIT_TIME_MILLIS = 250L;
  private static final String THREAD_NAME = "MicInputThread";
  private static final int MIC_BUFFER_COUNT = 30;
  private static final int AUDIO_THREAD_PRIO_BOOST = 2;
  private static final int MAX_ERROR_DRAIN_COUNT = 30;
  private static final long DRAIN_ON_ERROR_DELAY_MILLIS = 100L;
  private static final int OVERFLOW_WARN_THRESHOLD = 8;
  private static final int OVERFLOW_ERROR_THRESHOLD = 40;
  private static final int OVERFLOW_UNERROR_THRESHOLD = 30;
  private static final int INVALID_BUFFER_ID = -1;

  // Overflow states
  private static final int OVERFLOW_STATE_OK = 0;
  private static final int OVERFLOW_STATE_WARN = 1;
  private static final int OVERFLOW_STATE_ERROR = 2;

  private final Handler clientHandler;
  private final AutomaticGainControl micGainControl;
  private final double microsPerByte;
  private final Object threadLock = new Object();
  private final Runnable overflowOkRunnable =
      () -> sendOverflowStatus(MediaConstants.STATUS_AUDIO_RATE_GOOD);
  private final Runnable overflowWarnRunnable =
      () -> sendOverflowStatus(MediaConstants.STATUS_AUDIO_RATE_LOW);
  private final Runnable overflowErrorRunnable =
      () -> sendOverflowStatus(MediaConstants.STATUS_AUDIO_RATE_POOR);

  private volatile Thread micThread;
  private AudioRecord audioRecorder;
  private MovingAverage driftMicros;
  private boolean isReleased;
  private FillBufferCallback fillBufferCallback;
  private byte[] zeroBuf;
  private ErrorCallback errorCallback;
  private volatile boolean isStarted;
  private volatile boolean shouldStop;
  private volatile boolean isStopped;
  private volatile boolean isEnabled;
  private volatile boolean encounteredError;

  // Accessed only by reader thread
  private int overflowCount;
  private int overflowState;

  private final Runnable processAudioResponsesAction = () -> processAudioInputResponses();

  private static class AudioInputInfo {
    ByteBuffer buffer;
    int bufferId;
    long ptsMicros;
    int byteCount;
    int flags;
  }

  private final ConcurrentLinkedQueue<AudioInputInfo> audioRequestQueue =
      new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<AudioInputInfo> audioResponseQueue =
      new ConcurrentLinkedQueue<>();
  private final LinkedList<AudioInputInfo> audioInputFreeList = new LinkedList<>();
  private final LinkedList<AudioInputInfo> micFreeList = new LinkedList<>();
  private final LinkedList<AudioInputInfo> micFullList = new LinkedList<>();

  MicInput(
      @NonNull AudioRecord audioRecorder,
      int channelIn,
      int audioSampleRate,
      int bufferSize,
      Handler clientHandler) {
    this.clientHandler = Preconditions.checkNotNull(clientHandler);
    this.audioRecorder = Preconditions.checkNotNull(audioRecorder);

    int channelCount = (channelIn == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1);
    microsPerByte = 1000000.0 / ((double) BYTES_PER_SAMPLE * audioSampleRate * channelCount);
    // Try to add automatic gain control to the mic
    if (AutomaticGainControl.isAvailable()) {
      micGainControl = AutomaticGainControl.create(audioRecorder.getAudioSessionId());
      micGainControl.setEnabled(true);
    } else {
      micGainControl = null;
    }

    // Create some buffers to use when the client gets behind the capture
    for (int i = 0; i < MIC_BUFFER_COUNT; i++) {
      AudioInputInfo bufferInfo = new AudioInputInfo();
      bufferInfo.bufferId = INVALID_BUFFER_ID;
      bufferInfo.buffer = ByteBuffer.allocateDirect(bufferSize);
      micFreeList.add(bufferInfo);
    }
  }

  @Override
  public void setErrorCallback(@Nullable ErrorCallback errorCallback) {
    this.errorCallback = errorCallback;
  }

  @Override
  public void fillBufferRequest(int bufferId, ByteBuffer buffer) {
    AudioInputInfo request =
        (audioInputFreeList.isEmpty() ? new AudioInputInfo() : audioInputFreeList.removeFirst());
    request.buffer = buffer;
    request.bufferId = bufferId;
    request.byteCount = 0;
    request.flags = 0;

    if (encounteredError) {
      // An error was encountered.  Return the buffer immediately with a bad count value
      Log.w(TAG, "Received buffer fill request with pending error: bufferId=" + bufferId);
      request.byteCount = -1;
      sendAudioInputResponse(request);
    } else if (isStopped) {
      // When the input stops, set the EOS flag and just return the request.
      Log.d(TAG, "Sending end of stream audio response: bufferIndex=" + request.bufferId);
      request.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
      sendAudioInputResponse(request);
    } else if (isStarted) {
      // Send a request to fill the buffer to the mic thread
      audioRequestQueue.add(request);
    } else {
      // Not started.  Just send buffer back to client.  This should not happen in a
      // properly operating system.  Could throw an exception here, but try to keep
      // things running.
      Log.w(TAG, "Received buffer fill request before recorder started: bufferId=" + bufferId);
      sendAudioInputResponse(request);
    }
  }

  private synchronized void sendAudioInputResponse(AudioInputInfo response) {
    audioResponseQueue.add(response);
    clientHandler.post(processAudioResponsesAction);
  }

  // Runs on the codec thread and processes audio input responses received from the mic thread, i.e.
  // sends the data to the audio encoder.
  private void processAudioInputResponses() {
    Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());

    clientHandler.removeCallbacks(processAudioResponsesAction);
    while (!audioResponseQueue.isEmpty()) {
      AudioInputInfo response;
      try {
        response = audioResponseQueue.remove();
      } catch (NoSuchElementException e) {
        Log.e(TAG, "Audio response queue unexpectedly empty");
        return;
      }

      if (fillBufferCallback != null) {
        fillBufferCallback.onBufferFilled(
            response.bufferId,
            response.buffer,
            response.flags,
            0, // offset
            response.byteCount,
            response.ptsMicros);
      }

      response.buffer = null;
      response.byteCount = 0;
      response.ptsMicros = 0;
      response.bufferId = INVALID_BUFFER_ID;
      audioInputFreeList.addLast(response);
    }
  }

  @Override
  public void setFillBufferResponseHandler(FillBufferCallback callback) {
    fillBufferCallback = callback;
  }

  @Override
  public void setIsEnabled(boolean isEnabled) {
    this.isEnabled = isEnabled;
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public boolean start() {
    if (isReleased) {
      Log.e(TAG, "Cannot start once released");
      return false;
    }
    if (isStopped) {
      Log.e(TAG, "Cannot restart once stopped");
      return false;
    }
    if (isStarted) {
      // Allow multiple calls without error
      return true;
    }
    if (micThread != null) {
      Log.e(TAG, "Mic capture thread already exists");
      return false;
    }

    try {
      audioRecorder.startRecording();
    } catch (IllegalStateException e) {
      Log.e(TAG, "Could not start audio recorder", e);
      return false;
    }

    encounteredError = false;
    shouldStop = false;
    isStarted = true;
    micThread =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  // Give a little boost to the audio thread
                  int priority =
                      Process.THREAD_PRIORITY_DEFAULT
                          + AUDIO_THREAD_PRIO_BOOST * Process.THREAD_PRIORITY_MORE_FAVORABLE;
                  Process.setThreadPriority(priority);
                  mainLoop();
                } catch (Throwable t) {
                  if (!shouldStop) {
                    Log.e(TAG, "Unexpected throwable in mic main loop", t);
                    encounteredError = true;
                  }
                } finally {
                  synchronized (threadLock) {
                    micThread = null;
                    isStopped = true;
                  }
                }
              }
            },
            THREAD_NAME);
    micThread.start();

    return isStarted;
  }

  @Override
  public boolean stop() {
    if (isReleased) {
      Log.e(TAG, "Cannot stop once released");
      return false;
    }
    if (!isStarted) {
      Log.e(TAG, "Encoder not started");
      return false;
    }
    if (isStopped) {
      // Allow multiple calls without error
      return true;
    }
    synchronized (threadLock) {
      if (micThread == null) {
        return true;
      }

      shouldStop = true;
      while (true) {
        try {
          micThread.join(JOIN_WAIT_TIME_MILLIS);
          break;
        } catch (InterruptedException e) {
          // Ignore
        }
      }

      if (micThread != null && micThread.isAlive()) {
        micThread.interrupt();
        while (true) {
          try {
            micThread.join(JOIN_WAIT_TIME_MILLIS);
            break;
          } catch (InterruptedException e) {
            // Ignore
          }
        }
        if (micThread != null && !micThread.isAlive()) {
          micThread = null;
        }
      }
    }

    isStopped = (micThread == null);
    return isStopped;
  }

  @Override
  public boolean release() {
    if (isReleased) {
      // Allow multiple calls without error.
      return true;
    }

    // Shouldn't be necessary to stop, but for safety, ensure that the device is stopped
    stop();

    try {
      if (micGainControl != null) {
        micGainControl.release();
      }
      audioRecorder.release();
      isReleased = true;
    } catch (Exception e) {
      Log.d(TAG, "Releasing audio recorder failed", e);
    }

    return isReleased;
  }

  // Runs on handler thread and sends overflow state back to client
  private void sendOverflowStatus(int statusCode) {
    if (errorCallback != null) {
      errorCallback.onError(statusCode);
    }
  }

  // Runs on the mic thread and reads the mic data as fast as possible.  Results are posted
  // to incoming buffers from the client when available and to back buffers when not.
  private void mainLoop() {
    Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
    Preconditions.checkState(isStarted);

    long bytesTransmitted = 0;
    long streamStartTimeMicros = 0;
    driftMicros = new MovingAverage(MOVING_AVG_WINDOW_SIZE);
    int errorDrainCount = 0;
    overflowCount = 0;
    overflowState = OVERFLOW_STATE_OK;

    while (true) {
      // Stop when there is a pending request to do so
      if (shouldStop) {
        stopRecorder();
        return;
      }

      // When an error occurs, simply drain any pending requests
      if (encounteredError) {
        while (!audioRequestQueue.isEmpty()) {
          try {
            AudioInputInfo request = audioRequestQueue.remove();
            request.byteCount = -1;
            sendAudioInputResponse(request);
          } catch (NoSuchElementException e) {
            Log.e(TAG, "Audio request queue unexpectedly empty while draining on error", e);
            return;
          }
        }
        if (errorDrainCount >= MAX_ERROR_DRAIN_COUNT) {
          // If the mic thread spins too long after an error, just go ahead and exit
          Log.e(TAG, "Waited too long for cleanup after error");
          return;
        }
        if (errorDrainCount > 0) {
          // After draining the first time, delay a little before spinning again.
          try {
            Thread.sleep(DRAIN_ON_ERROR_DELAY_MILLIS, 0);
          } catch (InterruptedException e) {
            // Ignore interruption
            Log.w(TAG, "Interrupted while draining buffer queue on error.", e);
          }
        }
        errorDrainCount++;
        continue;
      }

      // If there is pending data and an available client buffer, copy out the results
      while (!audioRequestQueue.isEmpty() && !micFullList.isEmpty() && !encounteredError) {
        AudioInputInfo request;
        try {
          request = audioRequestQueue.remove();
        } catch (NoSuchElementException e) {
          Log.e(TAG, "Audio request queue unexpectedly empty while copying results", e);
          encounteredError = true;
          continue;
        }

        AudioInputInfo micData;
        try {
          micData = micFullList.removeFirst();
        } catch (NoSuchElementException e) {
          Log.e(TAG, "Mic queue unexpectedly empty while copying results", e);
          encounteredError = true;
          request.byteCount = -1;
          sendAudioInputResponse(request);
          continue;
        }

        request.ptsMicros = micData.ptsMicros;
        request.byteCount = micData.byteCount;
        try {
          request.buffer.put(micData.buffer);
        } catch (Exception e) {
          Log.e(TAG, "Error copying mic data to client buffer", e);
          encounteredError = true;
          request.byteCount = -1;
        }

        micData.buffer.position(0);
        micFreeList.add(micData);
        sendAudioInputResponse(request);
      }
      if (encounteredError) {
        continue;
      }

      if (audioRecorder == null) {
        Log.e(TAG, "Skipping audio input request due to missing recorder");
        encounteredError = true;
        continue;
      }

      // Select a buffer in which to store the result
      boolean usingRequestBuffer = !audioRequestQueue.isEmpty();
      long now = SystemClock.elapsedRealtimeNanos() / 1000;
      final AudioInputInfo inputInfo;
      if (usingRequestBuffer) {
        // Read directly into a request buffer
        try {
          inputInfo = audioRequestQueue.remove();
        } catch (NoSuchElementException e) {
          Log.e(TAG, "Audio request queue unexpectedly empty");
          encounteredError = true;
          continue;
        }

        // Buffer overflow is decreasing.
        reduceOverflow();
      } else if (micFreeList.isEmpty()) {
        // Nothing on the free list.  Pull the head of the full list
        try {
          inputInfo = micFullList.removeFirst();
        } catch (NoSuchElementException e) {
          Log.e(TAG, "Mic queue unexpectedly empty in overflow", e);
          encounteredError = true;
          continue;
        }

        // Buffer overflow is increasing.
        increaseOverflow();
      } else {
        try {
          inputInfo = micFreeList.removeFirst();
        } catch (NoSuchElementException e) {
          Log.e(TAG, "Mic free list unexpectedly empty", e);
          encounteredError = true;
          continue;
        }

        // Buffer overflow is decreasing.
        reduceOverflow();
      }

      // Start off with nothing in the response
      inputInfo.byteCount = 0;
      ByteBuffer buffer = inputInfo.buffer;

      if (streamStartTimeMicros <= 0) {
        streamStartTimeMicros = now;
        driftMicros.reset();
      }
      inputInfo.ptsMicros = streamStartTimeMicros + (long) (bytesTransmitted * microsPerByte);

      // Track the sync slippage
      if (bytesTransmitted > 0) {
        long deltaMicros = inputInfo.ptsMicros - now;
        driftMicros.addSample(deltaMicros);
      }

      // Read audio samples, catching any exceptions and turning them into a generic error
      try {
        inputInfo.byteCount = audioRecorder.read(buffer, buffer.capacity());
      } catch (Exception e) {
        Log.e(TAG, "Error reading audio data", e);
        inputInfo.byteCount = -1;
      }

      if (inputInfo.byteCount < 0) {
        Log.e(TAG, "Error reading audio sample data: " + inputInfo.byteCount);
        encounteredError = true;
        if (usingRequestBuffer) {
          sendAudioInputResponse(inputInfo);
        } else {
          micFreeList.add(inputInfo);
        }
        continue;
      }

      if (!isEnabled) {
        // Zero out the mic data when it's disabled.  Easier to do this than manage the timing
        // since input buffers are delivered as fast as possible
        if (zeroBuf == null || zeroBuf.length < buffer.capacity()) {
          zeroBuf = new byte[buffer.capacity()];
        }
        buffer.position(0);
        buffer.put(zeroBuf, 0, inputInfo.byteCount);
      }

      // Send the final result to the codec thread with a valid byte count,
      // or put it on the full list
      bytesTransmitted += inputInfo.byteCount;
      if (usingRequestBuffer) {
        sendAudioInputResponse(inputInfo);
      } else {
        micFullList.addLast(inputInfo);
      }
    }
  }

  private void increaseOverflow() {
    overflowCount++;
    if (overflowCount == OVERFLOW_WARN_THRESHOLD && overflowState != OVERFLOW_STATE_WARN) {
      Log.w(TAG, "Audio buffer overflow.  Entering warning state");
      overflowState = OVERFLOW_STATE_WARN;
      clientHandler.post(overflowWarnRunnable);
    } else if (overflowCount == OVERFLOW_ERROR_THRESHOLD && overflowState != OVERFLOW_STATE_ERROR) {
      Log.e(TAG, "Audio buffer overflow.  Entering error state");
      overflowState = OVERFLOW_STATE_ERROR;
      clientHandler.post(overflowErrorRunnable);
    }
  }

  private void reduceOverflow() {
    if (overflowCount <= 0) {
      return;
    }

    overflowCount--;
    if (overflowCount == 0 && overflowState != OVERFLOW_STATE_OK) {
      Log.d(TAG, "Audio buffer overflow condition ended");
      overflowState = OVERFLOW_STATE_OK;
      clientHandler.post(overflowOkRunnable);
    } else if (overflowCount == OVERFLOW_UNERROR_THRESHOLD
        && overflowState != OVERFLOW_STATE_WARN) {
      Log.w(TAG, "Audio buffer overflow improved.  Re-entering warning state");
      overflowState = OVERFLOW_STATE_WARN;
      clientHandler.post(overflowWarnRunnable);
    }
  }

  private void stopRecorder() {
    try {
      if (micGainControl != null && micGainControl.getEnabled()) {
        micGainControl.setEnabled(false);
      }
    } catch (Exception e) {
      Log.d(TAG, "Error stopping auto gain control", e);
      encounteredError = true;
    }
    try {
      if (audioRecorder.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED) {
        audioRecorder.stop();
      }
    } catch (Exception e) {
      Log.d(TAG, "Error stopping audio recorder", e);
      encounteredError = true;
    }
  }

  // Visible for testing.
  LinkedList<AudioInputInfo> getAudioInputFreeList() {
    return audioInputFreeList;
  }

  // Visible for testing.
  ConcurrentLinkedQueue<AudioInputInfo> getAudioRequestQueue() {
    return audioRequestQueue;
  }

  // Visible for testing.
  ConcurrentLinkedQueue<AudioInputInfo> getAudioResponseQueue() {
    return audioResponseQueue;
  }

  // Visible for testing.
  void setEncounteredError(boolean encounteredError) {
    this.encounteredError = encounteredError;
  }

  // Visible for testing.
  void setIsStopped(boolean isStopped) {
    this.isStopped = isStopped;
  }

  // Visible for testing.
  void setIsStarted(boolean isStarted) {
    this.isStarted = isStarted;
  }

  // Visible for testing.
  void setIsReleased(boolean isReleased) {
    this.isReleased = isReleased;
  }
}
