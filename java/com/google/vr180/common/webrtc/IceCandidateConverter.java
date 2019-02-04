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

import com.google.vr180.CameraApi;
import org.webrtc.IceCandidate;

/**
 * A helper class that converts WebRTC IceCandidate instances to and from their camera api format.
 */
public class IceCandidateConverter {

  /** Converts a WebRTC IceCandidate to a protocol buffer message. */
  public static CameraApi.IceCandidate serialize(IceCandidate candidate) {
    return CameraApi.IceCandidate.newBuilder()
        .setSdpMid(candidate.sdpMid)
        .setSdpMLineIndex(candidate.sdpMLineIndex)
        .setSdp(candidate.sdp)
        .build();
  }

  /** Converts from a protocol buffer candidate to a WebRTC IceCandidate instance. */
  public static IceCandidate parse(CameraApi.IceCandidate candidate) {
    return new IceCandidate(
        candidate.getSdpMid(), candidate.getSdpMLineIndex(), candidate.getSdp());
  }
}
