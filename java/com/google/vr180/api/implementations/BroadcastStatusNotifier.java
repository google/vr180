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

import android.content.Context;
import android.content.Intent;
import com.google.vr180.api.camerainterfaces.StatusNotifier;

/**
 * A status notifier using Android Broadcast
 */
public class BroadcastStatusNotifier implements StatusNotifier {

  public static final String STATUS_UPDATE_ACTION = "status_update_action";
  public static final String INTERNAL_STATUS_UPDATE_ACTION = "internal_status_update_action";

  private final Context context;
  private final String action;

  public BroadcastStatusNotifier(Context context, String action) {
    this.context = context;
    this.action = action;
  }

  @Override
  public void notifyStatusChanged() {
    context.sendBroadcast(new Intent(action));
  }
}
