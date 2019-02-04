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

import java.io.IOException;
import java.io.PipedOutputStream;

/**
 * A modified {@link PipedOutputStream} that overrides the default behavior of byte array writes. It
 * should be much faster when writing byte arrays.
 */
public class FasterPipedOutputStream extends PipedOutputStream {
  private final FasterPipedInputStream target;

  /**
   * Constructs a new {@link FasterPipedOutputStream} connected to the given
   * {@link FasterPipedInputStream}. The {@link FasterPipedInputStream} must always be created
   * first.
   */
  public FasterPipedOutputStream(FasterPipedInputStream target) throws IOException {
    super(target);
    this.target = target;
  }

  @Override
  public void write(byte[] buffer, int offset, int count) throws IOException {
    if ((offset < 0)
        || (count < 0)
        || (buffer.length - offset < count)) {
      throw new ArrayIndexOutOfBoundsException();
    }
    target.receive(buffer, offset, count);
  }
}
