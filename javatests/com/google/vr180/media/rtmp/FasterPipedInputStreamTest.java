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

import static org.junit.Assert.assertThrows;

import com.google.common.truth.Truth;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Test for {@link FasterPipedInputStream} */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class FasterPipedInputStreamTest {

  private static final int PIPE_SIZE = 10;
  private static final byte[] TEST_DATA = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

  private FasterPipedInputStream fasterPipedInputStream;
  private ExecutorService executorService;

  @Before
  public void setUp() throws InterruptedException, IOException {
    executorService = Executors.newCachedThreadPool();
    fasterPipedInputStream = new FasterPipedInputStream(PIPE_SIZE);
    // Attach up the FasterPipedOutputStream.
    new FasterPipedOutputStream(fasterPipedInputStream);
  }

  @Test
  public void testRead() throws IOException {
    fasterPipedInputStream.receive(TEST_DATA, 0, 4 /* count */);

    byte[] returnedByteArray = new byte[PIPE_SIZE];
    fasterPipedInputStream.read(returnedByteArray, 0 /* offset */, 1 /* readLength */);
    Truth.assertThat(returnedByteArray[0]).isEqualTo('0');
    fasterPipedInputStream.read(returnedByteArray, 1 /* offset */, 3 /* readLength */);
    byte[] expectedByteArray = {'0', '1', '2', '3', 0, 0, 0, 0, 0, 0};
    Truth.assertThat(returnedByteArray).isEqualTo(expectedByteArray);
  }

  @Test
  public void testRead_emptyBlocks() throws ExecutionException, InterruptedException {
    byte[] readArray = new byte[2];

    Future<Integer> read =
        executorService.submit(
            new ReadThread(fasterPipedInputStream, readArray, 0 /* offset */, 2 /* readLength */));
    assertThreadBlocked(read);
  }

  @Test
  public void testReceive_bufferLimit_blocksForSpace()
      throws IOException, InterruptedException, ExecutionException {
    fasterPipedInputStream.setBufferLimit(7);
    Future<Integer> receive;

    receive =
        executorService.submit(
            new ReceiveThread(fasterPipedInputStream, TEST_DATA, 0 /* offset */, 8 /* count */));
    Truth.assertThat(receive.get()).isEqualTo(8);
    Truth.assertThat(fasterPipedInputStream.available()).isEqualTo(8);

    // Attempt to put more bytes in and should block.
    receive =
        executorService.submit(
            new ReceiveThread(fasterPipedInputStream, TEST_DATA, 0 /* offset */, 2 /* count */));
    assertThreadBlocked(receive);
    Truth.assertThat(fasterPipedInputStream.available()).isEqualTo(8);
    // Read a byte to bring it below the buffer limit.
    fasterPipedInputStream.read();
    // Verify unblocked with two bytes added and one byte removed.
    Truth.assertThat(receive.get()).isEqualTo(2);
    Truth.assertThat(fasterPipedInputStream.available()).isEqualTo(9);
  }

  @Test
  public void testReceive_pipeSizeLimit_blocksForSpace()
      throws IOException, InterruptedException, ExecutionException {
    Future<Integer> receive;

    receive =
        executorService.submit(
            new ReceiveThread(fasterPipedInputStream, TEST_DATA, 0 /* offset */, PIPE_SIZE));
    Truth.assertThat(receive.get()).isEqualTo(PIPE_SIZE);
    Truth.assertThat(fasterPipedInputStream.available()).isEqualTo(PIPE_SIZE);

    // Attempt to put more bytes in and should block.
    receive =
        executorService.submit(
            new ReceiveThread(fasterPipedInputStream, TEST_DATA, 0 /* offset */, 2 /* count */));
    assertThreadBlocked(receive);
    Truth.assertThat(fasterPipedInputStream.available()).isEqualTo(PIPE_SIZE);
    // Read a byte to bring it below the buffer limit.
    fasterPipedInputStream.read();
    assertThreadBlocked(receive);
    fasterPipedInputStream.read();
    // Verify unblocked.
    Truth.assertThat(receive.get()).isEqualTo(2);
    Truth.assertThat(fasterPipedInputStream.available()).isEqualTo(PIPE_SIZE);
  }

  @Test
  public void testBufferLimit_blocksWhenReduced()
      throws IOException, InterruptedException, ExecutionException {
    fasterPipedInputStream.setBufferLimit(PIPE_SIZE);
    Future<Integer> receive;

    receive =
        executorService.submit(
            new ReceiveThread(fasterPipedInputStream, TEST_DATA, 0 /* offset */, 6 /* count */));
    Truth.assertThat(receive.get()).isEqualTo(6);
    Truth.assertThat(fasterPipedInputStream.available()).isEqualTo(6);

    fasterPipedInputStream.setBufferLimit(3);

    // Attempt to put more bytes in and should block.
    receive =
        executorService.submit(
            new ReceiveThread(fasterPipedInputStream, TEST_DATA, 0 /* offset */, 2 /* count */));
    assertThreadBlocked(receive);
    Truth.assertThat(fasterPipedInputStream.available()).isEqualTo(6);

    // Increase buffer limit and verify unblocked.
    fasterPipedInputStream.setBufferLimit(10);
    Truth.assertThat(receive.get()).isEqualTo(2);
    Truth.assertThat(fasterPipedInputStream.available()).isEqualTo(8);

    byte[] readBytes = new byte[8];
    fasterPipedInputStream.read(readBytes, 0, 8);
    byte[] expectedBytes = {'0', '1', '2', '3', '4', '5', '0', '1'};
    Truth.assertThat(readBytes).isEqualTo(expectedBytes);

    Truth.assertThat(fasterPipedInputStream.available()).isEqualTo(0);
  }

  @Test
  public void testRead_blocksForData()
      throws IOException, ExecutionException, InterruptedException {
    byte[] readArray = new byte[4];

    // Attempt to read more bytes in and should block.
    Future<Integer> read =
        executorService.submit(
            new ReadThread(fasterPipedInputStream, readArray, 1 /* offset */, 2 /* readLength */));
    // Should be blocked.
    assertThreadBlocked(read);

    fasterPipedInputStream.receive(TEST_DATA, 2 /* offset */, 4 /* count */);
    // Should now be unblocked.
    Truth.assertThat(read.get()).isEqualTo(2 /* count */);
    byte[] expectedBytes = {0, '2', '3', 0};
    Truth.assertThat(readArray).isEqualTo(expectedBytes);
  }

  private void assertThreadBlocked(Future<?> future)
      throws InterruptedException, ExecutionException {
    assertThrows(TimeoutException.class, () -> future.get(200, TimeUnit.MILLISECONDS));
  }

  private static final class ReadThread implements Callable<Integer> {

    private final byte[] buffer;
    private final int offset;
    private final int readLength;
    private final FasterPipedInputStream fasterPipedInputStream;

    ReadThread(
        FasterPipedInputStream fasterPipedInputStream, byte[] buffer, int offset, int readLength) {
      this.fasterPipedInputStream = fasterPipedInputStream;
      this.buffer = buffer;
      this.offset = offset;
      this.readLength = readLength;
    }

    @Override
    public Integer call() {
      try {
        return fasterPipedInputStream.read(buffer, offset, readLength);
      } catch (IOException e) {
        Assert.fail();
      }
      return -1;
    }
  }

  private static final class ReceiveThread implements Callable<Integer> {

    private final byte[] buffer;
    private final int offset;
    private final int count;
    private final FasterPipedInputStream fasterPipedInputStream;

    ReceiveThread(
        FasterPipedInputStream fasterPipedInputStream, byte[] buffer, int offset, int count) {
      this.fasterPipedInputStream = fasterPipedInputStream;
      this.buffer = buffer;
      this.offset = offset;
      this.count = count;
    }

    @Override
    public Integer call() {
      try {
        fasterPipedInputStream.receive(buffer, offset, count);
      } catch (IOException e) {
        Assert.fail();
      }
      return count;
    }
  }
}
