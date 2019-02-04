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

import javax.net.ServerSocketFactory;

/**
 * Interface for configuring SSL on the HTTP server. Provides the ServerSocketFactory for listening
 * on the server, and provides the signature of our self-signed certificate so we can return it as
 * part of the NetworkStatus.
 */
public interface SslManager {
  /** Returns the ServerSocketFactory for creating server sockets with SSL configured. */
  ServerSocketFactory getServerSocketFactory();

  /** Returns the signature of the SSL cert we are using. */
  byte[] getCameraCertificateSignature();
}
