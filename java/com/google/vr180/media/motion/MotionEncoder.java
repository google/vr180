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

package com.google.vr180.media.motion;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.os.Handler;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.BaseEncoder;
import com.google.vr180.media.MediaEncoder;
import com.google.vr180.media.SlowmoFormat;
import com.google.vr180.media.muxer.MediaMux;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;

/** An synchronous implementation of {@link MediaEncoder} for encoding motion data. */
public class MotionEncoder extends BaseEncoder {
  private static final String TAG = "MotionEncoder";
  // https://github.com/google/spatial-media/blob/master/docs/vr180.md
  private static final int BUFFER_SIZE = 16;
  // Key for indicating whether to save extra camm data.
  public static final String KEY_EXTRA_CAMM_DATA = "extra_camm_data";

  private final Handler handler;
  private final int speedFactor;
  private final boolean isExtraCammDataEnabled;
  // Output buffers.
  private final ArrayList<ByteBuffer> buffers = new ArrayList<>();
  private final ArrayDeque<Integer> recycle = new ArrayDeque<>();

  public MotionEncoder(MediaFormat format, MediaMux muxer, Handler handler) throws IOException {
    super(format, muxer, /*useMediaCodec=*/ false);
    this.handler = handler;
    speedFactor = SlowmoFormat.getSpeedFactor(format);
    onOutputFormatChanged(null, format);
    isExtraCammDataEnabled =
        format.containsKey(KEY_EXTRA_CAMM_DATA) && format.getInteger(KEY_EXTRA_CAMM_DATA) != 0;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public void setTargetBitrate(int bitrate) {
    Log.w(TAG, "Changing bitrate for motion not supported.");
  }

  @Override
  protected void signalEndOfStream() {
    handler.post(() -> signalEndOfStreamBuffer());
  }

  private void signalEndOfStreamBuffer() {
    BufferInfo eosBufferInfo = new BufferInfo();
    eosBufferInfo.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

    int bufferIndex = getNextOutputBufferIndex();
    ByteBuffer buffer = getOutputBuffer(bufferIndex);
    buffer.clear();
    onOutputBufferAvailable(null, bufferIndex, eosBufferInfo);
  }

  public void onMotionEvent(MotionEvent e) {
    switch (e.type) {
      case ACCELEROMETER:
        if (isExtraCammDataEnabled) {
          sendCammData(e.type.getType(), e.values[0], e.values[1], e.values[2], e.timestamp);
        }
        break;
      case GYROSCOPE:
        if (isExtraCammDataEnabled) {
          sendCammData(
              e.type.getType(),
              e.values[0] - e.values[3],
              e.values[1] - e.values[4],
              e.values[2] - e.values[5],
              e.timestamp);
        }
        break;
      case ORIENTATION:
        sendCammData(e.type.getType(), e.values[0], e.values[1], e.values[2], e.timestamp);
        break;
    }
  }

  private void sendCammData(int type, float x, float y, float z, long timestampNs) {
    int bufferIndex = getNextOutputBufferIndex();
    ByteBuffer buffer = getOutputBuffer(bufferIndex);

    buffer.clear();
    buffer.putInt(type);
    buffer.putFloat(x);
    buffer.putFloat(y);
    buffer.putFloat(z);
    buffer.rewind();

    BufferInfo bufferInfo = new BufferInfo();
    bufferInfo.set(0, BUFFER_SIZE, timestampNs * speedFactor / 1000, 0);
    onOutputBufferAvailable(null, bufferIndex, bufferInfo);
  }

  @Override
  public void onInputBufferAvailable(MediaCodec mediaCodec, int bufferId) {
    // This muxer should not call this routine.
    Log.e(TAG, "Motion codec unexpectedly provided an input buffer");
  }

  @Override
  public synchronized ByteBuffer getOutputBuffer(int bufferIndex) {
    return buffers.get(bufferIndex);
  }

  @Override
  public synchronized void releaseOutputBuffer(int bufferIndex) {
    recycle.add(bufferIndex);
  }

  private synchronized int getNextOutputBufferIndex() {
    if (!recycle.isEmpty()) {
      return recycle.poll();
    }
    buffers.add(ByteBuffer.allocate(BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN));
    return buffers.size() - 1;
  }
}
