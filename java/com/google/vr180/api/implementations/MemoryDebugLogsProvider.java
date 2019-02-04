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

import com.google.vr180.CameraApi.CameraApiRequest.DebugLogsRequest;
import com.google.vr180.CameraApi.DebugLogMessage;
import com.google.vr180.api.camerainterfaces.DebugLogsProvider;
import com.google.vr180.common.logging.MemoryLogger;
import java.util.ArrayList;
import java.util.List;

/** Uses the MemoryLogger to provide debug logs when requested. */
public class MemoryDebugLogsProvider implements DebugLogsProvider {

  private final MemoryLogger logger;

  public MemoryDebugLogsProvider(MemoryLogger logger) {
    this.logger = logger;
  }

  @Override
  public List<DebugLogMessage> getDebugLogs(DebugLogsRequest request) {
    List<DebugLogMessage> messages = logger.getMessages();
    ArrayList<DebugLogMessage> result = new ArrayList<DebugLogMessage>();
    boolean hasTagFilter = !"".equals(request.getTagFilter());
    for (DebugLogMessage message : messages) {
      if (message.getLevel().getNumber() < request.getMinLevel().getNumber()) {
        continue;
      }
      if (hasTagFilter && !request.getTagFilter().equals(message.getTag())) {
        continue;
      }
      result.add(message);

      if (request.hasMaxCount() && result.size() >= request.getMaxCount()) {
        break;
      }
    }
    return result;
  }
}
