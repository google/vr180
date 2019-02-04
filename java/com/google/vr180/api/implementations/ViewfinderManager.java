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
import com.google.vr180.CameraApi;
import com.google.vr180.CameraApi.CameraApiRequest.WebRtcRequest;
import com.google.vr180.CameraApi.CaptureMode;
import com.google.vr180.CameraApi.FrameSize;
import com.google.vr180.CameraApi.ViewfinderMode;
import com.google.vr180.CameraApi.WebRtcSessionDescription;
import com.google.vr180.api.camerainterfaces.CaptureManager;
import com.google.vr180.api.camerainterfaces.ViewfinderCaptureSource;
import com.google.vr180.api.camerainterfaces.ViewfinderProvider;
import com.google.vr180.common.logging.Log;
import com.google.vr180.common.webrtc.BlockingPeerObserver;
import com.google.vr180.common.webrtc.BlockingSdpObserver;
import com.google.vr180.common.webrtc.IceCandidateConverter;
import com.google.vr180.common.webrtc.WebRtcConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.PeerConnection.RTCConfiguration;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

/** Manages the state of the viewfinder capture and implements ViewfinderProvider. */
public class ViewfinderManager implements ViewfinderProvider {
  private static final String TAG = "ViewfinderManager";

  private static final String VIDEO_TRACK_ID = "video";
  private static final String LOCAL_MEDIA_STREAM_ID = "camera";

  /** Default capture setting used for the viewfinder if no viewfinder mode is set. */
  private static final ViewfinderMode DEFAULT_VIEWFINDER_MODE =
      ViewfinderMode.newBuilder()
          .setFrameSize(FrameSize.newBuilder().setFrameWidth(640).setFrameHeight(480))
          .setFramesPerSecond(30f)
          .build();

  private final Context applicationContext;
  private final RTCConfiguration rtcConfig;
  private final CaptureManager captureManager;
  private final ViewfinderCaptureSource videoCaptureSource;
  private SurfaceTextureHelper surfaceTextureHelper;
  private VideoCapturer videoCapturer;
  private VideoSource videoSource;

  @GuardedBy("this")
  private PeerConnectionFactory peerConnectionFactory;
  /** Viewfinder connections. */
  @GuardedBy("this")
  private final HashMap<String, PeerConnectionState> connections =
      new HashMap<String, PeerConnectionState>();

  public ViewfinderManager(
      Context context, CaptureManager captureManager, ViewfinderCaptureSource videoCaptureSource) {
    applicationContext = context;
    this.captureManager = captureManager;
    this.videoCaptureSource = videoCaptureSource;
    rtcConfig = new RTCConfiguration(new ArrayList<IceServer>());
    rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
    rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.ALL;
    rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
  }

  @Override
  public WebRtcSessionDescription startViewfinderWebrtc(WebRtcRequest request) throws IOException {
    if (videoCaptureSource == null) {
      Log.d(TAG, "videoCaptureSource is not available");
      return null;
    }

    WebRtcSessionDescription offerDescription = request.getOffer();
    SessionDescription remoteSdp =
        new SessionDescription(
            SessionDescription.Type.OFFER, offerDescription.getSessionDescription());

    PeerConnectionState connectionState = getConnectionState(request.getSessionName());
    SessionDescription localSessionDescription = acceptOffer(remoteSdp, connectionState.connection);
    for (CameraApi.IceCandidate candidate : offerDescription.getIceCandidateList()) {
      connectionState.connection.addIceCandidate(IceCandidateConverter.parse(candidate));
    }

    WebRtcSessionDescription.Builder result =
        WebRtcSessionDescription.newBuilder()
            .setSessionDescription(localSessionDescription.description);

    List<IceCandidate> iceCandidates = connectionState.observer.waitForIceCandidates();
    for (IceCandidate candidate : iceCandidates) {
      result.addIceCandidate(IceCandidateConverter.serialize(candidate));
    }
    return result.build();
  }

  @Override
  public void stopViewfinderWebrtc(WebRtcRequest request) {
    Log.d(TAG, "Closing webrtc session: " + request.getSessionName());
    closeConnection(request.getSessionName());
  }

  private SessionDescription acceptOffer(
      SessionDescription remoteSessionDescription, PeerConnection peerConnection)
      throws IOException {
    BlockingSdpObserver sdbObserver = new BlockingSdpObserver();
    peerConnection.setRemoteDescription(sdbObserver, remoteSessionDescription);
    sdbObserver.waitForSet();
    peerConnection.createAnswer(sdbObserver, createAnswerConstraints());
    SessionDescription localDescription = sdbObserver.waitForCreate();

    peerConnection.setLocalDescription(sdbObserver, localDescription);
    sdbObserver.waitForSet();
    return localDescription;
  }

  /** Looks for an existing connection state or opens a new one. */
  private synchronized PeerConnectionState getConnectionState(String sessionName) {
    PeerConnectionState result = connections.get(sessionName);
    if (result == null) {
      Log.d(TAG, "Opening webrtc session: " + sessionName);
      initializePeerConnectionFactory();
      if (videoSource == null) {
        Log.d(TAG, "Starting viewfinder capture");
        videoCapturer = videoCaptureSource.getVideoCapturer();
        videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        surfaceTextureHelper =
            SurfaceTextureHelper.create("SurfaceTextureHelper", /* sharedContext= */ null);
        ViewfinderMode mode = getActiveViewfinderMode();
        videoCapturer.initialize(
            surfaceTextureHelper, applicationContext, videoSource.getCapturerObserver());
        videoCapturer.startCapture(
            mode.getFrameSize().getFrameWidth(),
            mode.getFrameSize().getFrameHeight(),
            Math.round(mode.getFramesPerSecond()));
      }

      PeerObserver observer = new PeerObserver(sessionName);
      PeerConnection connection = createPeerConnection(observer);
      result = new PeerConnectionState(connection, observer);
      connections.put(sessionName, result);
    } else {
      Log.d(TAG, "Updating existing webrtc session: " + sessionName);
    }

    return result;
  }

  /** Closes a viewfinder connection. */
  private synchronized void closeConnection(String sessionName) {
    PeerConnectionState connectionState = connections.get(sessionName);
    if (connectionState == null) {
      Log.d(TAG, "Trying to close connection not found: " + sessionName);
      return;
    }

    Log.d(TAG, "Closing session: " + sessionName);
    // Note, we have to setClosing on the observer first so it will ignore further state updates
    // which would try to reenter closeConnection().
    connectionState.observer.setClosing();
    connectionState.connection.dispose();
    connections.remove(sessionName);
    if (!connections.isEmpty()) {
      return;
    }

    if (videoCapturer != null) {
      try {
        Log.d(TAG, "Stopping viewfinder capture");
        videoCapturer.stopCapture();
      } catch (InterruptedException e) {
        Log.e(TAG, "Unable to stop video capture.", e);
      }
      videoCapturer.dispose();
      videoCapturer = null;
    }
    if (videoSource != null) {
      videoSource.dispose();
      videoSource = null;
    }
    if (surfaceTextureHelper != null) {
      surfaceTextureHelper.dispose();
      surfaceTextureHelper = null;
    }
  }

  @GuardedBy("this")
  private PeerConnection createPeerConnection(PeerObserver peerObserver) {
    PeerConnection peerConnection =
        peerConnectionFactory.createPeerConnection(
            rtcConfig, createPeerConnectionConstraints(), peerObserver);
    VideoTrack localVideoTrack =
        peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);

    MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID);
    mediaStream.addTrack(localVideoTrack);
    peerConnection.addStream(mediaStream);
    return peerConnection;
  }

  @GuardedBy("this")
  private void initializePeerConnectionFactory() {
    // Lazy initialize the peerConnectionFactory
    if (peerConnectionFactory == null) {
      this.peerConnectionFactory =
          PeerConnectionFactory.builder()
              .setVideoEncoderFactory(
                  new DefaultVideoEncoderFactory(/* eglContext= */ null, true, true))
              .setVideoDecoderFactory(new DefaultVideoDecoderFactory(/* eglContext= */ null))
              .createPeerConnectionFactory();
    }
  }

  private ViewfinderMode getActiveViewfinderMode() {
    CaptureMode captureMode = captureManager.getActiveCaptureMode();
    if (captureMode == null || !captureMode.hasViewfinderMode()) {
      return DEFAULT_VIEWFINDER_MODE;
    }

    return captureMode.getViewfinderMode();
  }

  private static MediaConstraints createPeerConnectionConstraints() {
    MediaConstraints pcConstraints = new MediaConstraints();
    // Enable Datagram Transport Layer Security.
    pcConstraints.optional.add(
        new MediaConstraints.KeyValuePair(
            WebRtcConstants.DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT, "true"));
    return pcConstraints;
  }

  private static MediaConstraints createAnswerConstraints() {
    MediaConstraints.KeyValuePair keyValueVideo =
        new MediaConstraints.KeyValuePair(
            WebRtcConstants.OFFER_TO_RECEIVE_VIDEO_CONSTRAINT, Boolean.toString(false));
    MediaConstraints.KeyValuePair keyValueAudio =
        new MediaConstraints.KeyValuePair(
            WebRtcConstants.OFFER_TO_RECEIVE_AUDIO_CONSTRAINT, Boolean.toString(false));
    MediaConstraints answerConstraints = new MediaConstraints();
    answerConstraints.mandatory.add(keyValueAudio);
    answerConstraints.mandatory.add(keyValueVideo);
    return answerConstraints;
  }

  /** Handles event callbacks from a viewfinder connection. */
  private class PeerObserver extends BlockingPeerObserver {
    private String sessionName;
    private boolean isClosing = false;

    public PeerObserver(String sessionName) {
      this.sessionName = sessionName;
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
      super.onIceConnectionChange(newState);
      if (isClosing) {
        return;
      }

      if (newState == PeerConnection.IceConnectionState.CLOSED
          || newState == PeerConnection.IceConnectionState.FAILED) {
        // Clean up when the session closes.
        closeConnection(sessionName);
      }
    }

    /**
     * Sets a flag that indicates that the connection is closing. This prevents trying to make a
     * reentrant call when the WebRtc api notifies us that the connection is closed.
     */
    public void setClosing() {
      isClosing = true;
    }
  }

  private static class PeerConnectionState {
    public PeerConnectionState(PeerConnection connection, PeerObserver observer) {
      this.connection = connection;
      this.observer = observer;
    }

    public PeerConnection connection;
    public PeerObserver observer;
  }
}
