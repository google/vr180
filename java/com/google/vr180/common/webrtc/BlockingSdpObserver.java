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

package com.google.vr180.common.webrtc;

import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/** An implementation of WebRTC's SDP observer that provides methods to wait for a callback. */
public class BlockingSdpObserver implements SdpObserver {

  private SettableFuture<Void> setFuture = SettableFuture.create();
  private SettableFuture<SessionDescription> createFuture = SettableFuture.create();

  /** Called on success of Create{Offer,Answer}(). */
  @Override
  public void onCreateSuccess(SessionDescription sdp) {
    createFuture.set(sdp);
  }

  /** Called on error of Create{Offer,Answer}(). */
  @Override
  public void onCreateFailure(String error) {
    createFuture.setException(new IOException(error));
  }

  /** Called on success of Set{Local,Remote}Description(). */
  @Override
  public void onSetSuccess() {
    setFuture.set(null);
  }

  /** Called on error of Set{Local,Remote}Description(). */
  @Override
  public void onSetFailure(String error) {
    setFuture.setException(new IOException(error));
  }

  /**
   * Waits for the result of a Set{Local,Remote}Description().
   *
   * @throws IOException on any error.
   */
  public void waitForSet() throws IOException {
    try {
      setFuture.get();
    } catch (Exception e) {
      throw new IOException("Error setting session description.", e);
    } finally {
      setFuture = SettableFuture.create();
    }
  }

  /**
   * Waits for the result of a Create{Offer,Answer}().
   *
   * @throws IOException on any error.
   * @return The created session description.
   */
  public SessionDescription waitForCreate() throws IOException {
    try {
      return createFuture.get();
    } catch (Exception e) {
      throw new IOException("Error creating session description.", e);
    } finally {
      createFuture = SettableFuture.create();
    }
  }
}
