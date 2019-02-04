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

package com.google.vr180.api.implementations;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import com.google.vr180.api.camerainterfaces.StatusNotifier;

/** Notifies clients when the android network state changes. */
public class NetworkStateNotifier implements AutoCloseable {

  private final Context context;
  private final StatusNotifier notifier;
  private final NetworkStateReceiver receiver;

  public NetworkStateNotifier(Context context, StatusNotifier notifier) {
    this.context = context;
    this.notifier = notifier;
    IntentFilter networkStateChangedIntentFilter = new IntentFilter();
    networkStateChangedIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
    this.receiver = new NetworkStateReceiver();
    context.registerReceiver(receiver, networkStateChangedIntentFilter);
  }

  @Override
  public void close() {
    context.unregisterReceiver(receiver);
  }

  private class NetworkStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      notifier.notifyStatusChanged();
    }
  }
}
