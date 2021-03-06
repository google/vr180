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

package com.google.vr180.api.camerainterfaces;

import com.google.vr180.CameraApi.CameraApiRequest.DebugLogsRequest;
import com.google.vr180.CameraApi.DebugLogMessage;
import java.util.List;

/** Interface to fetch debug logs based on a GET_DEBUG_LOGS request. */
public interface DebugLogsProvider {
  /** Fetches the debug logs matching the provided request. */
  List<DebugLogMessage> getDebugLogs(DebugLogsRequest request);
}
