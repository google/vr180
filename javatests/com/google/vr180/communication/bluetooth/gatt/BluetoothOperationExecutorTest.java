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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.bluetooth.BluetoothGatt;
import com.google.common.util.concurrent.Futures;
import com.google.vr180.communication.bluetooth.BluetoothException;
import com.google.vr180.communication.bluetooth.gatt.BluetoothOperationExecutor.BluetoothOperationTimeoutException;
import com.google.vr180.communication.bluetooth.gatt.BluetoothOperationExecutor.Operation;
import com.google.vr180.communication.bluetooth.gatt.BluetoothOperationExecutor.SynchronousOperation;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link BluetoothOperationExecutor}. */
@RunWith(RobolectricTestRunner.class)
public class BluetoothOperationExecutorTest {
  private static final String OPERATION_RESULT = "result";
  private static final String OPERATION_RESULT2 = "result2";
  private static final String EXCEPTION_REASON = "exception";
  private static final long TIMEOUT = 2345;

  @Mock private Semaphore mockSemaphore;

  @Mock private Operation<String> mockStringOperation;
  private Operation<String> simpleStringOperation;
  private Operation<Void> simpleVoidOperation;

  private BluetoothOperationExecutor mBluetoothOperationExecutor;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    when(mockSemaphore.tryAcquire()).thenReturn(true);

    mBluetoothOperationExecutor = new BluetoothOperationExecutor(mockSemaphore);
    simpleStringOperation = new Operation<String>() {
      @Override
      public void run() throws BluetoothException {
        mBluetoothOperationExecutor.notifySuccess(this, OPERATION_RESULT);
      }
    };
    simpleVoidOperation = new Operation<Void>() {
      @Override
      public void run() throws BluetoothException {}
    };
  }

  @Test
  public void testExecute() throws Exception {
    String result = mBluetoothOperationExecutor.execute(simpleStringOperation);
    assertThat(result).isEqualTo(OPERATION_RESULT);
  }

  @Test
  public void testExecuteWithTimeout() throws Exception {
    String result = mBluetoothOperationExecutor.execute(simpleStringOperation, TIMEOUT);
    assertThat(result).isEqualTo(OPERATION_RESULT);
  }

  @Test
  public void testSchedule() throws Exception {
    Future<String> result = mBluetoothOperationExecutor.schedule(simpleStringOperation);
    assertThat(result.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT);
  }

  @Test
  public void testScheduleOtherOperationInProgress() throws Exception {
    Mockito.doAnswer((invocationOnMock) -> {
      mBluetoothOperationExecutor.notifySuccess(mockStringOperation, OPERATION_RESULT);
      return null;
    }).when(mockStringOperation).execute(mBluetoothOperationExecutor);

    when(mockSemaphore.tryAcquire()).thenReturn(false);
    Future<String> result = mBluetoothOperationExecutor.schedule(mockStringOperation);
    verify(mockStringOperation, never()).run();

    when(mockSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)).thenReturn(true);
    assertThat(result.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT);
    verify(mockStringOperation).execute(mBluetoothOperationExecutor);
  }

  @Test
  public void testScheduleSameTypeOperationsTwice() throws Exception {
    Operation<String> operation1 = new Operation<String>() {
      @Override
      public void run() throws BluetoothException {
        assertThat(mBluetoothOperationExecutor.mOperationResultQueues.size()).isEqualTo(1);
        mBluetoothOperationExecutor.notifySuccess(this, OPERATION_RESULT);
        assertThat(mBluetoothOperationExecutor.mOperationResultQueues.size()).isEqualTo(0);
      }
    };
    Operation<String> operation2 = new Operation<String>() {
      @Override
      public void run() throws BluetoothException {
        assertThat(mBluetoothOperationExecutor.mOperationResultQueues.size()).isEqualTo(1);
        mBluetoothOperationExecutor.notifySuccess(this, OPERATION_RESULT2);
        assertThat(mBluetoothOperationExecutor.mOperationResultQueues.size()).isEqualTo(0);
      }
    };
    // mOperationResultQueues uses Operation<> as hash key, and here we want to make sure that we
    // properly handle the situation when the keys conflict.
    assertThat(operation2.hashCode()).isEqualTo(operation1.hashCode());

    when(mockSemaphore.tryAcquire()).thenReturn(false);
    Future<String> future1 = mBluetoothOperationExecutor.schedule(operation1);
    Future<String> future2 = mBluetoothOperationExecutor.schedule(operation2);

    when(mockSemaphore.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(true);
    assertThat(future1.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT);
    assertThat(future2.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT2);
  }

  @Test
  public void testNotifySuccessWithResult() throws Exception {
    Future<String> future = mBluetoothOperationExecutor.schedule(simpleStringOperation);

    mBluetoothOperationExecutor.notifySuccess(simpleStringOperation, OPERATION_RESULT);

    assertThat(future.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT);
  }

  @Test
  public void testNotifySuccessTwice() throws Exception {
    BlockingQueue<Object> resultQueue = new LinkedBlockingDeque<Object>();
    Future<String> future = mBluetoothOperationExecutor.schedule(simpleStringOperation);

    mBluetoothOperationExecutor.notifySuccess(simpleStringOperation, OPERATION_RESULT);

    assertThat(future.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT);
    assertThat(resultQueue.isEmpty()).isTrue();

    // the second notification should be ignored
    mBluetoothOperationExecutor.notifySuccess(simpleStringOperation, OPERATION_RESULT);
    assertThat(resultQueue.isEmpty()).isTrue();
  }

  @Test
  public void testNotifySuccessWithNullResult() throws Exception {
    simpleStringOperation = new Operation<String>() {
      @Override
      public void run() throws BluetoothException {}
    };
    Future<String> future = mBluetoothOperationExecutor.schedule(simpleStringOperation);
    mBluetoothOperationExecutor.notifySuccess(simpleStringOperation, null);
    assertThat(future.get(TIMEOUT, TimeUnit.MILLISECONDS)).isNull();
  }

  @Test
  public void testNotifySuccess() throws Exception {
    Future<Void> future = mBluetoothOperationExecutor.schedule(simpleVoidOperation);

    mBluetoothOperationExecutor.notifySuccess(simpleVoidOperation);

    future.get(TIMEOUT, TimeUnit.MILLISECONDS);
  }

  @Test
  public void testNotifyCompletionSuccess() throws Exception {
    Future<Void> future = mBluetoothOperationExecutor.schedule(simpleVoidOperation);

    mBluetoothOperationExecutor.notifyCompletion(simpleVoidOperation, BluetoothGatt.GATT_SUCCESS);

    future.get(TIMEOUT, TimeUnit.MILLISECONDS);
  }

  @Test
  public void testNotifyCompletionFailure() throws Exception {
    Future<Void> future = mBluetoothOperationExecutor.schedule(simpleVoidOperation);

    mBluetoothOperationExecutor.notifyCompletion(simpleVoidOperation, BluetoothGatt.GATT_FAILURE);

    try {
      BluetoothOperationExecutor.getResult(future, TIMEOUT);
      Assert.fail("Expected BluetoothException");
    } catch (BluetoothException e) {
      //expected
    }
  }

  @Test
  public void testNotifyFailure() throws Exception {
    Future<Void> future = mBluetoothOperationExecutor.schedule(simpleVoidOperation);

    mBluetoothOperationExecutor.notifyFailure(simpleVoidOperation, new BluetoothException("test"));

    try {
      BluetoothOperationExecutor.getResult(future, TIMEOUT);
      Assert.fail("Expected BluetoothException");
    } catch (BluetoothException e) {
      //expected
    }
  }

  @Test
  public void testGetResult() throws Exception {
    Future<Object> mockFuture = Futures.immediateFuture(OPERATION_RESULT);
    Object result = BluetoothOperationExecutor.getResult(mockFuture);

    assertThat(result).isEqualTo(OPERATION_RESULT);
  }

  @Test
  public void testGetResultWithTimeout() throws Exception {
    Future<Object> mockFuture =
        Futures.immediateFailedFuture(new BluetoothOperationTimeoutException("Timeout"));
    try {
      BluetoothOperationExecutor.getResult(mockFuture, TIMEOUT);
      Assert.fail("Expected BluetoothOperationTimeoutException");
    } catch (BluetoothOperationTimeoutException e) {
      //expected
    }
  }

  @Test
  public void test_SynchronousOperation_execute() throws Exception {
    SynchronousOperation<String> synchronousOperation = new SynchronousOperation<String>() {
      @Override
      public String call() throws BluetoothException {
        return OPERATION_RESULT;
      }};

    Future<?> result = mBluetoothOperationExecutor.schedule(synchronousOperation);
    assertThat(result.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(OPERATION_RESULT);
    verify(mockSemaphore).release();
  }

  @Test
  public void test_SynchronousOperation_exception() throws Exception {
    final BluetoothException exception = new BluetoothException(EXCEPTION_REASON);
    SynchronousOperation<String> synchronousOperation = new SynchronousOperation<String>() {
      @Override
      public String call() throws BluetoothException {
        throw exception;
      }};

    Future<?> result = mBluetoothOperationExecutor.schedule(synchronousOperation);
    try {
      result.get(TIMEOUT, TimeUnit.MILLISECONDS);
      Assert.fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      //expected
    }
    verify(mockSemaphore).release();
  }

  @Test
  public void test_AsynchronousOperation_exception() throws Exception {
    final BluetoothException exception = new BluetoothException(EXCEPTION_REASON);
    Operation<String> operation = new Operation<String>() {
      @Override
      public void run() throws BluetoothException {
        throw exception;
      }
    };

    Future<?> result = mBluetoothOperationExecutor.schedule(operation);
    try {
      result.get(TIMEOUT, TimeUnit.MILLISECONDS);
      Assert.fail("Expected ExecutionException");
    } catch (ExecutionException e) {
      //expected
    }
    verify(mockSemaphore).release();
  }
}
