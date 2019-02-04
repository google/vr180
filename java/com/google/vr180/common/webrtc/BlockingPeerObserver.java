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
import com.google.vr180.common.logging.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

/**
 * Implementation of WebRTC's PeerConnection.Observer which enables waiting for IceCandidates to be
 * gathered.
 *
 * <p>No-op/Log-only implemenations of all other methods are provided (which can be overridden in a
 * subclass).
 */
public class BlockingPeerObserver implements PeerConnection.Observer {
  private static final String TAG = "BlockingPeerObserver";

  private final SettableFuture<List<IceCandidate>> iceCandidatesFuture = SettableFuture.create();
  private final ArrayList<IceCandidate> iceCandidates = new ArrayList<IceCandidate>();

  /** Triggered when the SignalingState changes. */
  @Override
  public void onSignalingChange(PeerConnection.SignalingState newState) {
    Log.d(TAG, "SignalingState: " + newState);
  }

  /** Triggered when the IceConnectionState changes. */
  @Override
  public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
    Log.d(TAG, "IceConnectionState: " + newState);
  }

  /** Triggered when the ICE connection receiving status changes. */
  @Override
  public void onIceConnectionReceivingChange(boolean receiving) {
    Log.d(TAG, "IceConnectionReceiving: " + receiving);
  }

  /** Triggered when the IceGatheringState changes. */
  @Override
  public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
    Log.d(TAG, "IceGatheringState: " + newState);
    if (newState == PeerConnection.IceGatheringState.COMPLETE) {
      iceCandidatesFuture.set(iceCandidates);
    }
  }

  /** Triggered when a new ICE candidate has been found. */
  @Override
  public void onIceCandidate(IceCandidate candidate) {
    Log.d(TAG, "IceCandidate: " + candidate.toString());
    iceCandidates.add(candidate);
  }

  @Override
  public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

  /** Triggered when media is received on a new stream from remote peer. */
  @Override
  public void onAddStream(MediaStream stream) {}

  /** Triggered when a remote peer close a stream. */
  @Override
  public void onRemoveStream(MediaStream stream) {}

  /** Triggered when a remote peer opens a DataChannel. */
  @Override
  public void onDataChannel(DataChannel dataChannel) {}

  /** Triggered when renegotiation is necessary. */
  @Override
  public void onRenegotiationNeeded() {}

  @Override
  public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}

  /**
   * Waits for the IceGatheringState to switch to COMPLETE and returns the collected IceCandidates.
   *
   * @throws IOException If there is an error.
   * @return A list of IceCandidates gathered.
   */
  public List<IceCandidate> waitForIceCandidates() throws IOException {
    try {
      return iceCandidatesFuture.get();
    } catch (Exception e) {
      throw new IOException("Error getting ice candidates.", e);
    }
  }
}
