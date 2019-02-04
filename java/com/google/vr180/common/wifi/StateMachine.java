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

import android.os.Handler;
import android.os.HandlerThread;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.vr180.common.logging.Log;
import java.util.HashMap;
import java.util.Map;

/**
 * A state machine that tracks states and handles events
 *
 * All the state transitions should happen on handler for thread safety.
 */
public class StateMachine<State, Event, Data> {

  private static final String TAG = "StateMachine";

  /** Event handling callback */
  public interface EventCallback {

    /**
     * called before or after event handling, depending on whether it is preHandleCallback or
     * postHandleCallback
     *
     * @param exception null if the event is handled successfully, or an exception if error occurs.
     */
    void callback(Exception exception);
  }

  /** Listener for state change */
  public interface StateChangeListener {
    /** Called whenever state is changed */
    void onStateChanged(Object oldState, Object newState);
  }

  private final Handler handler;
  private final StateChangeListener stateChangeListener;

  final Runnable timeoutRunnable;
  final Table<State, Event, State> rules = HashBasedTable.create();
  final Table<State, Event, State> ignoreRules = HashBasedTable.create();
  final Table<State, Event, State> warningRules = HashBasedTable.create();
  final Table<State, Event, Exception> rejectRules = HashBasedTable.create();
  final Map<State, TimeoutRule<State>> timeoutRules = new HashMap<>();

  private State state;
  private Data data;

  /**
   * Create a handler on a new handler thread
   *
   * @return a handler on a new handler thread
   */
  public static Handler createStateMachineHandler() {
    HandlerThread stateMachineThread = new HandlerThread("StateMachineThread");
    stateMachineThread.start();
    return new Handler(stateMachineThread.getLooper());
  }

  /**
   * StateMachine constructor
   *
   * @param handler The handler that all events are handled on. Should be single-threaded
   * @param stateChangeListener State change callback
   * @param initialState Initial state
   * @param timeoutEvent Timeout event that should be fired when a state times out
   */
  public StateMachine(
      Handler handler,
      StateChangeListener stateChangeListener,
      State initialState,
      Event timeoutEvent) {
    this.handler = handler;
    this.stateChangeListener = stateChangeListener;
    this.state = initialState;
    this.timeoutRunnable = () -> this.handleEvent(timeoutEvent);
  }

  /**
   * Handles an event without callbacks or data
   * @param event The event
   */
  public void handleEvent(Event event) {
    handleEvent(event, null, null, false, null);
  }

  /**
   * Handles an event with attached data, which will be saved in the state machine
   *
   * @param event The event
   * @param data The data attached to the event
   */
  public void handleEventWithData(Event event, Data data) {
    handleEvent(event, null, null, true, data);
  }

  /**
   * Handles an event with callbacks
   *
   * @param event The event
   * @param preHandleCallback called before state transition.
   * @param postHandleCallback called after state transition.
   */
  public void handleEventWithCallbacks(
      Event event,
      EventCallback preHandleCallback,
      EventCallback postHandleCallback) {
    handleEvent(event, preHandleCallback, postHandleCallback, false, null);
  }

  /**
   * Handle the event
   *
   * @param event The event
   * @param preHandleCallback Callback function called before state transition
   * @param postHandleCallback Callback function called after state transition
   * @param hasData Indicate if the event is attached with data
   * @param data The additional data to be saved in the state machine
   */
  public void handleEvent(
      Event event,
      EventCallback preHandleCallback,
      EventCallback postHandleCallback,
      boolean hasData,
      Data data) {
    Log.d(TAG, "Posting event " + event + " to event queue");
    handler.post(() ->
      handleEventInternal(event, preHandleCallback, postHandleCallback, hasData, data));
  }

  /**
   * Get the current state
   *
   * @return the current state
   */
  public synchronized State getState() {
    return state;
  }

  /**
   * Get the current data
   *
   * @return the current data
   */
  public synchronized Data getData() {
    return data;
  }

  /** Set timeout rules */
  public synchronized void setTimeout(Long timeout, State... states) {
    for (State state : states) {
      if (timeoutRules.containsKey(state)) {
        throw new IllegalStateException("Timeout rule already set for state " + state);
      }
    }
    TimeoutRule<State> timeoutRule = new TimeoutRule<>(states, timeout);
    for (State state : states) {
      timeoutRules.put(state, timeoutRule);
    }
  }

  /** Helper function to set rules. See @RuleHelper */
  public synchronized RuleHelper on(Event event) {
    return new RuleHelper(event);
  }

  /** Verify that all situations are properly handled */
  public void checkRuleCompleteness(State[] states, Event[] events) {
    String error = "";
    for (State state : states) {
      for (Event event : events) {
        if (!rules.contains(state, event) && !warningRules.contains(state, event)
            && !rejectRules.contains(state, event) && !ignoreRules.contains(state, event)) {
          error += state + ":" + event + " ";
        }
      }
    }
    if (!error.equals("")) {
      throw new IllegalStateException("No rule for " + error);
    }
  }

  private synchronized void handleEventInternal(
      Event event,
      EventCallback preCallback,
      EventCallback postCallback,
      boolean hasData,
      Data data) {
    State newState;
    Exception exception = null;

    if (rules.contains(state, event)) {
      newState = rules.get(state, event);
      Log.i(TAG, "[" + state + "] -> [" + newState + "] after event " + event);
    } else if (warningRules.contains(state, event)) {
      newState = warningRules.get(state, event);
      Log.w(TAG, "[" + state + "] -> [" + newState + "] after event " + event + " [warning]");
    } else if (rejectRules.contains(state, event)) {
      newState = state;
      exception = rejectRules.get(state, event);
      Log.e(TAG, "[" + state + "] -> [" + newState + "] after event " + event + " [rejected]");
    } else if (ignoreRules.contains(state, event)) {
      newState = state;
      Log.i(TAG, "[" + state + "] -> [" + newState + "] after event " + event + " [ignored]");
    } else {
      newState = state;
      Log.e(TAG, "[" + state + "] -> [" + newState + "] after event " + event  + " [unhandled]");
    }

    if (preCallback != null) {
      preCallback.callback(exception);
    }
    if (exception == null && hasData) {
      this.data = data;
    }
    onNextState(newState);
    if (postCallback != null) {
      postCallback.callback(exception);
    }
  }

  private synchronized void onNextState(State newState) {
    if (newState == state) {
      return;
    }
    State oldState = state;
    state = newState;
    updateTimeout(oldState, newState);
    if (stateChangeListener != null) {
      stateChangeListener.onStateChanged(oldState, newState);
    }
  }

  private synchronized void updateTimeout(State oldState, State newState) {
    TimeoutRule<State> timeoutRuleForOldState = timeoutRules.get(oldState);
    TimeoutRule<State> timeoutRuleForNewState = timeoutRules.get(newState);
    if (timeoutRuleForOldState == timeoutRuleForNewState) {
      // If the old and new states share the same timeout rule, do not update the timer
      return;
    }
    if (timeoutRuleForOldState != null) {
      handler.removeCallbacks(timeoutRunnable);
    }
    if (timeoutRuleForNewState != null) {
      handler.postDelayed(timeoutRunnable, timeoutRuleForNewState.timeoutMs);
    }
  }

  /**
   * Helper class to set rules
   *
   * e.g. stateMachine.on(Event.START).states(State.IDLE).transitionTo(State.STARTED)
   * e.g. stateMachine.on(Event.P2P_ENABLED).states(State.IDLE).ignore()
   */
  public class RuleHelper {
    Event event;
    State[] inStates;

    public RuleHelper(Event event) {
      this.event = event;
    }

    /** Set the states which should follow the rule set in the subsequent rule function call */
    public RuleHelper states(State... states) {
      if (inStates != null) {
        throw new IllegalStateException();
      }
      inStates = states;
      return this;
    }

    /** Set transition rules*/
    public RuleHelper transitionTo(State outState) {
      checkCondition();
      for (State inState : inStates) {
        rules.put(inState, event, outState);
      }
      clearInStates();
      return this;
    }

    /** Set transition rules with warnings */
    public RuleHelper transitionWithWarning(State outState) {
      checkCondition();
      for (State inState : inStates) {
        warningRules.put(inState, event, outState);
      }
      clearInStates();
      return this;
    }

    /** Set rules to ignore the event */
    public RuleHelper ignore() {
      checkCondition();
      for (State inState : inStates) {
        ignoreRules.put(inState, event, inState);
      }
      clearInStates();
      return this;
    }

    /**
     * Set rules to reject the event, i.e., state machine will call callbacks with isSuccess=false
     * and will not apply the data attached to the event.
     */
    public RuleHelper reject(Exception exception) {
      checkCondition();
      for (State inState : inStates) {
        rejectRules.put(inState, event, exception);
      }
      clearInStates();
      return this;
    }

    private void checkCondition() {
      if (inStates == null || event == null) {
        throw new IllegalStateException();
      }
    }

    private void clearInStates() {
      inStates = null;
    }
  }

  /**
   * Timeout rules. For states in the same rule, they share the same timeout, i.e., if one state
   * transitions to another state, timeout is not reset.
   */
  static class TimeoutRule<State> {
    State[] states;
    Long timeoutMs;

    TimeoutRule(State[] states, Long timeoutMs) {
      this.states = states;
      this.timeoutMs = timeoutMs;
    }
  }
}
