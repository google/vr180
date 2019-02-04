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

package com.google.vr180.communication.bluetooth.gatt;

import android.bluetooth.BluetoothGatt;
import com.google.protobuf.ByteString;
import com.google.vr180.communication.bluetooth.BluetoothGattException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Maintains pending data for a "prepared" write to a characteristic.
 *
 * This class is NOT thread-safe, access to the class should be synchronized.
 **/
public class PreparedWriteState {

  /**
   * A map of the pending writes that are queued. Keys in this map represent offsets, while the
   * values contain the data written at that offset.
   */
  private final SortedMap<Integer, ByteString> preparedWrites = new TreeMap<Integer, ByteString>();

  /** The associated bluetooth service config. */
  private final BluetoothServiceConfig serviceConfig;

  /**
   * Constructs a new state representing a prepared write to the request characteristic of a
   * service.
   *
   * @param serviceConfig The service config for the prepared write (this is needed since that data
   *     is not available when the executeWrite call happens).
   */
  public PreparedWriteState(BluetoothServiceConfig serviceConfig) {
    this.serviceConfig = serviceConfig;
  }

  /** Returns the service configuration that this pending write is associated with. */
  public BluetoothServiceConfig getServiceConfig() {
    return this.serviceConfig;
  }

  /** Adds data to the pending queue of writes. */
  public void prepareWrite(int offset, ByteString data) {
    preparedWrites.put(offset, data);
  }

  /**
   * Combines together the prepared writes and returns the data. The data prepared must form a
   * complete block.
   *
   * @return The combined data written to the characteristic
   * @throws BluetoothGattException if the data prepared is not a single contiguous block
   */
  public ByteString assembleWrite() throws BluetoothGattException {
    ByteString result = ByteString.EMPTY;
    for (Map.Entry<Integer, ByteString> chunk : preparedWrites.entrySet()) {
      if (chunk.getKey() != result.size()) {
        throw new BluetoothGattException(
            String.format(
                "Next chunk has offset %d, while previous ended at %d",
                chunk.getKey(), result.size()),
            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED);
      }

      result = result.concat(chunk.getValue());
    }

    return result;
  }
}
