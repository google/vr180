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

package com.google.vr180.common.wifi;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import com.google.vr180.common.wifi.StateMachine.EventCallback;
import com.google.vr180.common.wifi.StateMachine.StateChangeListener;
import com.google.vr180.common.wifi.StateMachine.TimeoutRule;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

/** Test the StateMachine class */
@RunWith(RobolectricTestRunner.class)
public class StateMachineTest {

  enum State {
    STATE_1,
    STATE_2,
    STATE_3,
  }

  enum Event {
    EVENT_MOVE,
    EVENT_REJECTED,
    EVENT_IGNORED,
    EVENT_TIMEOUT,
  }

  private final Exception exception = new IllegalStateException();
  private StateMachine<State, Event, String> stateMachine;
  private Handler mockHandler;

  @Before
  public void setup() throws Exception {
    mockHandler = Mockito.mock(Handler.class);
    resetHandler();
    stateMachine = new StateMachine<>(
        mockHandler,
        null,
        State.STATE_1,
        Event.EVENT_TIMEOUT);
  }

  @Test
  public void testRuleSetup() throws Exception {
    stateMachine.on(Event.EVENT_MOVE)
        .states(State.STATE_1).transitionTo(State.STATE_2)
        .states(State.STATE_2).transitionTo(State.STATE_3)
        .states(State.STATE_3).transitionWithWarning(State.STATE_1);
    assertEquals(State.STATE_2, stateMachine.rules.get(State.STATE_1, Event.EVENT_MOVE));
    assertEquals(State.STATE_3, stateMachine.rules.get(State.STATE_2, Event.EVENT_MOVE));
    assertEquals(State.STATE_1, stateMachine.warningRules.get(State.STATE_3, Event.EVENT_MOVE));

    stateMachine.on(Event.EVENT_REJECTED)
        .states(State.STATE_1, State.STATE_2, State.STATE_3).reject(exception);
    assertEquals(exception, stateMachine.rejectRules.get(State.STATE_1, Event.EVENT_REJECTED));
    assertEquals(exception, stateMachine.rejectRules.get(State.STATE_2, Event.EVENT_REJECTED));
    assertEquals(exception, stateMachine.rejectRules.get(State.STATE_3, Event.EVENT_REJECTED));

    stateMachine.on(Event.EVENT_IGNORED)
        .states(State.STATE_1, State.STATE_2, State.STATE_3).ignore();
    assertEquals(State.STATE_1, stateMachine.ignoreRules.get(State.STATE_1, Event.EVENT_IGNORED));
    assertEquals(State.STATE_2, stateMachine.ignoreRules.get(State.STATE_2, Event.EVENT_IGNORED));
    assertEquals(State.STATE_3, stateMachine.ignoreRules.get(State.STATE_3, Event.EVENT_IGNORED));
  }

  @Test
  public void testTransition() throws Exception {
    setupRules();
    assertEquals(State.STATE_1, stateMachine.getState());
    stateMachine.handleEvent(Event.EVENT_MOVE);
    assertEquals(State.STATE_2, stateMachine.getState());
    stateMachine.handleEvent(Event.EVENT_MOVE);
    assertEquals(State.STATE_3, stateMachine.getState());
    stateMachine.handleEvent(Event.EVENT_MOVE);
    assertEquals(State.STATE_1, stateMachine.getState());

    stateMachine.handleEvent(Event.EVENT_REJECTED);
    assertEquals(State.STATE_1, stateMachine.getState());

    stateMachine.handleEvent(Event.EVENT_IGNORED);
    assertEquals(State.STATE_1, stateMachine.getState());
  }

  @Test
  public void testTimeoutRule() throws Exception {
    setupRules();
    Long timeout = 100L;
    stateMachine.setTimeout(timeout, State.STATE_2, State.STATE_3);
    assertEquals(timeout, stateMachine.timeoutRules.get(State.STATE_2).timeoutMs);
    assertEquals(timeout, stateMachine.timeoutRules.get(State.STATE_3).timeoutMs);
    TimeoutRule<State> timeoutRule2 = stateMachine.timeoutRules.get(State.STATE_2);
    TimeoutRule<State> timeoutRule3 = stateMachine.timeoutRules.get(State.STATE_3);
    assertEquals(timeoutRule2, timeoutRule3);

    stateMachine.handleEvent(Event.EVENT_MOVE);
    assertEquals(State.STATE_2, stateMachine.getState());
    verify(mockHandler).postDelayed(stateMachine.timeoutRunnable, timeout);

    resetHandler();
    stateMachine.handleEvent(Event.EVENT_MOVE);
    assertEquals(State.STATE_3, stateMachine.getState());
    verify(mockHandler, never()).removeCallbacks(stateMachine.timeoutRunnable);
    verify(mockHandler, never()).postDelayed(stateMachine.timeoutRunnable, timeout);

    resetHandler();
    stateMachine.handleEvent(Event.EVENT_MOVE);
    assertEquals(State.STATE_1, stateMachine.getState());
    verify(mockHandler).removeCallbacks(stateMachine.timeoutRunnable);
    verify(mockHandler, never()).postDelayed(stateMachine.timeoutRunnable, timeout);
  }

  @Test
  public void testEventWithData() throws Exception {
    setupRules();
    assertEquals(State.STATE_1, stateMachine.getState());
    assertEquals(null, stateMachine.getData());

    stateMachine.handleEventWithData(Event.EVENT_MOVE, "A");
    assertEquals(State.STATE_2, stateMachine.getState());
    assertEquals("A", stateMachine.getData());
    stateMachine.handleEventWithData(Event.EVENT_MOVE, "B");
    assertEquals(State.STATE_3, stateMachine.getState());
    assertEquals("B", stateMachine.getData());
    stateMachine.handleEventWithData(Event.EVENT_MOVE, "C");
    assertEquals(State.STATE_1, stateMachine.getState());
    assertEquals("C", stateMachine.getData());

    // Rejected events will not update the Data
    stateMachine.handleEventWithData(Event.EVENT_REJECTED, "D");
    assertEquals(State.STATE_1, stateMachine.getState());
    assertEquals("C", stateMachine.getData());

    stateMachine.handleEventWithData(Event.EVENT_IGNORED, "E");
    assertEquals(State.STATE_1, stateMachine.getState());
    assertEquals("E", stateMachine.getData());
  }


  @Test
  public void testEventWithCallbacks() throws Exception {
    StateChangeListener listener = Mockito.mock(StateChangeListener.class);
    EventCallback preCallback = Mockito.mock(Callback.class);
    EventCallback postCallback = Mockito.mock(Callback.class);

    stateMachine = new StateMachine<>(
        mockHandler,
        listener,
        State.STATE_1,
        Event.EVENT_TIMEOUT);
    setupRules();
    assertEquals(State.STATE_1, stateMachine.getState());

    stateMachine.handleEventWithCallbacks(Event.EVENT_MOVE, preCallback, postCallback);
    assertEquals(State.STATE_2, stateMachine.getState());
    verify(listener).onStateChanged(State.STATE_1, State.STATE_2);
    verify(preCallback).callback(null);
    verify(postCallback).callback(null);

    Mockito.reset(listener, preCallback, postCallback);
    stateMachine.handleEventWithCallbacks(Event.EVENT_MOVE, preCallback, postCallback);
    assertEquals(State.STATE_3, stateMachine.getState());
    verify(listener).onStateChanged(State.STATE_2, State.STATE_3);
    verify(preCallback).callback(null);
    verify(postCallback).callback(null);

    Mockito.reset(listener, preCallback, postCallback);
    stateMachine.handleEventWithCallbacks(Event.EVENT_MOVE, preCallback, postCallback);
    assertEquals(State.STATE_1, stateMachine.getState());
    verify(listener).onStateChanged(State.STATE_3, State.STATE_1);
    verify(preCallback).callback(null);
    verify(postCallback).callback(null);

    Mockito.reset(listener, preCallback, postCallback);
    stateMachine.handleEventWithCallbacks(Event.EVENT_REJECTED, preCallback, postCallback);
    assertEquals(State.STATE_1, stateMachine.getState());
    verify(listener, never()).onStateChanged(any(), any());
    verify(preCallback).callback(exception);
    verify(postCallback).callback(exception);

    Mockito.reset(listener, preCallback, postCallback);
    stateMachine.handleEventWithCallbacks(Event.EVENT_IGNORED, preCallback, postCallback);
    assertEquals(State.STATE_1, stateMachine.getState());
    verify(listener, never()).onStateChanged(any(), any());
    verify(preCallback).callback(null);
    verify(postCallback).callback(null);
  }

  private void setupRules() {
    stateMachine.on(Event.EVENT_MOVE)
        .states(State.STATE_1).transitionTo(State.STATE_2)
        .states(State.STATE_2).transitionTo(State.STATE_3)
        .states(State.STATE_3).transitionWithWarning(State.STATE_1);

    stateMachine.on(Event.EVENT_REJECTED)
        .states(State.STATE_1, State.STATE_2, State.STATE_3).reject(exception);

    stateMachine.on(Event.EVENT_IGNORED)
        .states(State.STATE_1, State.STATE_2, State.STATE_3).ignore();
  }

  private void resetHandler() {
    Mockito.reset(mockHandler);
    when(mockHandler.post(any())).thenAnswer(new Answer<Boolean>() {
      @Override
      public Boolean answer(InvocationOnMock invocation) throws Throwable {
        Runnable runnable = (Runnable) invocation.getArguments()[0];
        runnable.run();
        return true;
      }
    });
  }

  private static class Callback implements EventCallback {
    @Override
    public void callback(Exception exception) {}
  }
}
