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

import com.google.vr180.api.camerainterfaces.StatusNotifier;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements StatusNotifier, but allows a StatusNotifier instance to be set after
 * construction.
 * This allows passing the instance to all the code that needs a StatusNotifier, but binding one
 * after we start the bluetooth server.
 */
public class StatusNotificationChannel implements StatusNotifier {

  private List<StatusNotifier> notifiers = new ArrayList<>();

  @Override
  public void notifyStatusChanged() {
    for (StatusNotifier notifier : notifiers) {
      notifier.notifyStatusChanged();
    }
  }

  public void addStatusNotifier(StatusNotifier notifier) {
    notifiers.add(notifier);
  }
}
