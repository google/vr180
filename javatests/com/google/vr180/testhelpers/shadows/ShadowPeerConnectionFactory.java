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

package com.google.vr180.testhelpers.shadows;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.PeerConnectionFactory.InitializationOptions;

/**
 * Shadow of PeerConnectionFactory. Using this shadow allows test class to mock
 * PeerConnectionFactory, because the shadow is mocked and not the real object. The real object
 * can't be mocked because it has native methods.
 */
@Implements(value = PeerConnectionFactory.class, callThroughByDefault = true)
public class ShadowPeerConnectionFactory {
  @Implementation
  public static void initialize(InitializationOptions options) {}
}
