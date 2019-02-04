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

package com.google.vr180.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.android.MainThreadDisposable;

/** Wraps a BroadcastReceiver in an Observable. */
class BroadcastReceiverObservable {

  /** Returns an observable that notifies when an intent filter broadcast receiver fires. */
  public static Observable<Intent> create(Context context, IntentFilter intentFilter) {
    Context applicationContext = context.getApplicationContext();
    return Observable.create(
        emitter -> {
          BroadcastReceiver broadcastReceiver = new SubscriberBroadcastReceiver(emitter);
          applicationContext.registerReceiver(broadcastReceiver, intentFilter);
          emitter.setDisposable(
              new MainThreadDisposable() {
                @Override
                protected void onDispose() {
                  applicationContext.unregisterReceiver(broadcastReceiver);
                }
              });
        });
  }

  /** Forwards received broadcasts to a subscriber. */
  private static class SubscriberBroadcastReceiver extends BroadcastReceiver {
    private final ObservableEmitter<? super Intent> emitter;

    public SubscriberBroadcastReceiver(ObservableEmitter<? super Intent> emitter) {
      this.emitter = emitter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      emitter.onNext(intent);
    }
  }
}
