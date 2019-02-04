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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.vr180.common.logging.Log;
import com.google.vr180.communication.bluetooth.BluetoothException;
import com.google.vr180.communication.bluetooth.BluetoothGattException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

/**
 * Scheduler to coordinate parallel bluetooth operations.
 */
public class BluetoothOperationExecutor {
  private static final String TAG = BluetoothOperationExecutor.class.getSimpleName();

  /**
   * Special value to indicate that the result is null (since {@link BlockingQueue} doesn't allow
   * null elements).
   */
  private static final Object NULL_RESULT = new Object();

  /** Special value to indicate that there should be no timeout on the operation. */
  private static final long NO_TIMEOUT = -1;

  @VisibleForTesting
  final Map<Operation<?>, Queue<Object>> mOperationResultQueues = new HashMap<>();
  private final Semaphore mOperationSemaphore;

  /** New instance that limits concurrent operations to maxConcurrentOperations. */
  public BluetoothOperationExecutor(int maxConcurrentOperations) {
    this(new Semaphore(maxConcurrentOperations, true));
  }

  /** Constructor for unit tests. */
  @VisibleForTesting
  BluetoothOperationExecutor(Semaphore operationSemaphore) {
    mOperationSemaphore = operationSemaphore;
  }

  /** Executes the operation and waits for its completion. */
  @Nullable
  public <T> T execute(Operation<T> operation) throws BluetoothException {
    return getResult(schedule(operation));
  }

  /** Executes the operation and waits for its completion and returns a non-null result. */
  public <T> T executeNonnull(Operation<T> operation) throws BluetoothException {
    T result = getResult(schedule(operation));
    if (result == null) {
      throw new BluetoothException(
          String.format("Operation %s returned a null result.", operation));
    }
    return result;
  }

  /** Executes the operation and waits for its completion with a timeout. */
  @Nullable
  public <T> T execute(Operation<T> bluetoothOperation, long timeoutMilis)
      throws BluetoothException, BluetoothOperationTimeoutException {
    return getResult(schedule(bluetoothOperation), timeoutMilis);
  }

  /**
   * Executes the operation and waits for its completion with a timeout and returns a non-null
   * result.
   */
  public <T> T executeNonnull(Operation<T> bluetoothOperation, long timeoutMilis)
      throws BluetoothException {
    T result = getResult(schedule(bluetoothOperation), timeoutMilis);
    if (result == null) {
      throw new BluetoothException(
          String.format("Operation %s returned a null result.", bluetoothOperation));
    }
    return result;
  }

  /**
   * Schedules an operation and returns a {@link Future} that waits on operation completion and gets
   * its result.
   */
  public <T> Future<T> schedule(Operation<T> bluetoothOperation) {
    BlockingQueue<Object> resultQueue = new LinkedBlockingDeque<Object>();
    // TODO: differentiate the same operations if they are invoked twice in a row. Otherwise
    // the latter one's result will override the previous one.
    // This prevents us from running two operations of the same type in parallel. If this happens,
    // we'll throw an exception for the second operation.

    // TODO: wait for previous identical operations to finish.
    if (mOperationResultQueues.get(bluetoothOperation) != null) {
      return new BluetoothOperationFuture<T>(resultQueue, bluetoothOperation, false);
    }
    boolean semaphoreAcquired = mOperationSemaphore.tryAcquire();
    Log.d(TAG, String.format(
        "Scheduling operation %s; %d permits available; Semaphore acquired: %b",
        bluetoothOperation,
        mOperationSemaphore.availablePermits(),
        semaphoreAcquired));

    if (semaphoreAcquired) {
      mOperationResultQueues.put(bluetoothOperation, resultQueue);
      bluetoothOperation.execute(this);
    }
    return new BluetoothOperationFuture<T>(resultQueue, bluetoothOperation, semaphoreAcquired);
  }

  /** Notifies that this operation has completed with success. */
  public void notifySuccess(Operation<Void> bluetoothOperation) {
    postResult(bluetoothOperation, null);
  }

  /** Notifies that this operation has completed with success and with a result. */
  public <T> void notifySuccess(Operation<T> bluetoothOperation, T result) {
    postResult(bluetoothOperation, result);
  }

  /**
   * Notifies that this operation has completed with the given BluetoothGatt status code (which may
   * indicate success or failure).
   */
  public void notifyCompletion(Operation<Void> bluetoothOperation, int status) {
    notifyCompletion(bluetoothOperation, status, null);
  }

  /**
   * Notifies that this operation has completed with the given BluetoothGatt status code (which may
   * indicate success or failure) and with a result.
   */
  public <T> void notifyCompletion(Operation<T> bluetoothOperation, int status,
      @Nullable T result) {
    if (status != BluetoothGatt.GATT_SUCCESS) {
      notifyFailure(bluetoothOperation, new BluetoothGattException(
          String.format(
              "Operation %s failed: %d - %s.", bluetoothOperation, status,
              BluetoothGattUtils.getMessageForStatusCode(status)),
          status));
      return;
    }
    postResult(bluetoothOperation, result);
  }

  /** Notifies that this operation has completed with failure. */
  public void notifyFailure(Operation<?> bluetoothOperation, BluetoothException exception) {
    postResult(bluetoothOperation, exception);
  }

  private void postResult(Operation<?> bluetoothOperation, @Nullable Object result) {
    Queue<Object> resultQueue = mOperationResultQueues.get(bluetoothOperation);
    if (resultQueue == null) {
      Log.e(TAG,
          String.format("Receive completion for unexpected operation: %s.", bluetoothOperation));
      return;
    }
    resultQueue.add(result == null ? NULL_RESULT : result);
    mOperationResultQueues.remove(bluetoothOperation);
    mOperationSemaphore.release();
    Log.d(TAG, String.format("Released semaphore for operation %s. There are %d permits left",
        bluetoothOperation, mOperationSemaphore.availablePermits()));
  }

  /** Waits for all future on the list to complete, ignoring the results. */
  public <T> void waitFor(List<Future<T>> futures) throws BluetoothException {
    for (Future<T> future : futures) {
      if (future == null) {
        continue;
      }
      getResult(future);
    }
  }

  /** Waits with timeout for all future on the list to complete, ignoring the results. */
  public <T> void waitFor(List<Future<T>> futures, long timeoutMillis)
      throws BluetoothException {
    long startTime = getTime();
    for (Future<T> future : futures) {
      if (future == null) {
        continue;
      }
      getResult(future, timeoutMillis - (getTime() - startTime));
    }
  }

  /** Waits for a future to complete and returns the result. */
  @Nullable
  public static <T> T getResult(Future<T> future) throws BluetoothException {
    return getResultInternal(future, NO_TIMEOUT);
  }

  /** Waits for a future to complete and returns the result with timeout. */
  @Nullable
  public static <T> T getResult(Future<T> future, long timeoutMillis) throws BluetoothException {
    return getResultInternal(future, Math.max(0, timeoutMillis));
  }

  @Nullable
  private static <T> T getResultInternal(Future<T> future, long timeoutMillis)
      throws BluetoothException {
    try {
      if (timeoutMillis == NO_TIMEOUT) {
        return future.get();
      } else {
        return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      throw new BluetoothException("Wait interrupted");
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof BluetoothException) {
        throw (BluetoothException) cause;
      }
      throw new RuntimeException(e);
    } catch (TimeoutException e) {
      boolean cancelSuccess = future.cancel(true);
      if (!cancelSuccess && future.isDone()) {
        // Operation has succeeded before we send cancel to it.
        return getResultInternal(future, NO_TIMEOUT);
      }
      throw new BluetoothOperationTimeoutException(
          String.format("Wait timed out after %s ms.", timeoutMillis), e);
    }
  }

  private static long getTime() {
    return new Date().getTime();
  }

  /**
   * Asynchronous bluetooth operation to schedule.
   *
   * <p>An instance that doesn't implemented run() can be used to notify operation result.
   */
  public static class Operation<T> {
    private Object[] mElements;

    public Operation(Object... elements) {
      mElements = elements;
    }

    public void execute(BluetoothOperationExecutor executor) {
      try {
        run();
      } catch (BluetoothException e) {
        executor.postResult(this, e);
      }
    }

    @SuppressWarnings("unused")
    public void run() throws BluetoothException {
      throw new RuntimeException("Not implemented");
    }

    /** Try to cancel operation when a timeout occurs. */
    public void cancel() {
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (o == null) {
        return false;
      }
      if (!Operation.class.isInstance(o)) {
        return false;
      }
      Operation<?> other = (Operation<?>) o;
      return Arrays.equals(mElements, other.mElements);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(mElements);
    }

    @Override
    public String toString() {
      return Joiner.on('-').join(mElements);
    }
  }

  /**
   * Synchronous bluetooth operation to schedule.
   */
  public static class SynchronousOperation<T> extends Operation<T> {
    public SynchronousOperation(Object... elements) {
      super(elements);
    }

    @Override
    public void execute(BluetoothOperationExecutor executor) {
      try {
        Object result = call();
        if (result == null) {
          result = NULL_RESULT;
        }
        executor.postResult(this, result);
      } catch (BluetoothException e) {
        executor.postResult(this, e);
      }
    }

    @SuppressWarnings("unused")
    @Nullable
    public T call() throws BluetoothException {
      throw new RuntimeException("Not implemented");
    }
  }

  /**
   * {@link Future} to wait / get result of an operation.
   *
   * <li>Waits for operation to complete
   * <li>Handles timeouts if needed
   * <li>Queues identical Bluetooth operations
   * <li>Unwraps Exceptions and null values
   *
   */
  private class BluetoothOperationFuture<T> implements Future<T> {
    private final Object mLock = new Object();

    /**
     * Queue that will be used to store the result. It should normally contains one element maximum,
     * but using a queue avoid some race conditions.
     */
    private final BlockingQueue<Object> mResultQueue;
    private final Operation<T> mBluetoothOperation;
    private final boolean mOperationExecuted;
    private boolean mIsCancelled = false;
    private boolean mIsDone = false;

    public BluetoothOperationFuture(BlockingQueue<Object> resultQueue,
        Operation<T> bluetoothOperation, boolean operationExecuted) {
      mResultQueue = resultQueue;
      mBluetoothOperation = bluetoothOperation;
      mOperationExecuted = operationExecuted;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      synchronized (mLock) {
        if (mIsDone) {
          return false;
        }
        if (mIsCancelled) {
          return true;
        }
        mBluetoothOperation.cancel();
        mIsCancelled = true;
        notifyFailure(mBluetoothOperation, new BluetoothException("Operation cancelled."));
        return true;
      }
    }

    @Override
    public boolean isCancelled() {
      synchronized (mLock) {
        return mIsCancelled;
      }
    }

    @Override
    public boolean isDone() {
      synchronized (mLock) {
        return mIsDone;
      }
    }

    @Override
    @Nullable
    public T get() throws InterruptedException, ExecutionException {
      try {
        return getInternal(NO_TIMEOUT, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        throw new RuntimeException(e); // This is not supposed to be thrown
      }
    }

    @Override
    @Nullable
    public T get(long timeoutMillis, TimeUnit unit) throws InterruptedException, ExecutionException,
        TimeoutException {
      return getInternal(Math.max(0, timeoutMillis), unit);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private T getInternal(long timeoutMillis, TimeUnit unit) throws ExecutionException,
        InterruptedException, TimeoutException {
      // Prevent parallel executions of this method.
      long startTime = getTime();
      synchronized (this) {
        synchronized (mLock) {
          if (mIsDone) {
            throw new ExecutionException(new BluetoothException("get() called twice..."));
          }
        }
        if (!mOperationExecuted) {
          if (timeoutMillis == NO_TIMEOUT) {
            mOperationSemaphore.acquire();
          } else {
            if (!mOperationSemaphore.tryAcquire(timeoutMillis - (getTime() - startTime), unit)) {
              throw new TimeoutException(String.format(
                  "A timeout occurred when processing %s after %s %s.",
                  mBluetoothOperation, timeoutMillis, unit));
            }
          }
          if (mOperationResultQueues.get(mBluetoothOperation) != null) {
            throw new ExecutionException(
                new BluetoothException("Operations conflict in result queues"));
          }
          mOperationResultQueues.put(mBluetoothOperation, mResultQueue);
          mBluetoothOperation.execute(BluetoothOperationExecutor.this);
        }
        Object result;

        if (timeoutMillis == NO_TIMEOUT) {
          result = mResultQueue.take();
        } else {
          result = mResultQueue.poll(timeoutMillis - (getTime() - startTime), unit);
        }

        if (result == null) {
          throw new TimeoutException(String.format(
              "A timeout occurred when processing %s after %s ms.",
              mBluetoothOperation, timeoutMillis));
        }
        synchronized (mLock) {
          mIsDone = true;
        }
        if (result instanceof BluetoothException) {
          throw new ExecutionException((BluetoothException) result);
        }
        if (result == NULL_RESULT) {
          result = null;
        }
        return (T) result;
      }
    }
  }

  /**
   * Exception thrown when an operation execution times out. Since state of the system is unknown
   * afterward (operation may still complete or not), it is recommended to disconnect and reconnect.
   */
  public static class BluetoothOperationTimeoutException extends BluetoothException {
    public BluetoothOperationTimeoutException(String message) {
      super(message);
    }

    public BluetoothOperationTimeoutException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
