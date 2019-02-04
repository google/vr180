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

package com.google.vr180.capture;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;
import com.google.common.base.Preconditions;
import com.google.vr180.capture.motion.MotionCaptureSource;
import com.google.vr180.capture.video.VideoCaptureSource;
import com.google.vr180.common.logging.Log;
import com.google.vr180.media.AbrController;
import com.google.vr180.media.MediaConstants;
import com.google.vr180.media.MediaEncoder;
import com.google.vr180.media.MediaEncoder.EndOfStreamCallback;
import com.google.vr180.media.audio.AudioEncoderFactory;
import com.google.vr180.media.audio.AudioInput;
import com.google.vr180.media.audio.AudioInputFactory;
import com.google.vr180.media.metadata.MetadataInjector;
import com.google.vr180.media.motion.MotionEncoder;
import com.google.vr180.media.motion.MotionEncoderFactory;
import com.google.vr180.media.muxer.ChapteredFileMuxer;
import com.google.vr180.media.muxer.MediaMux;
import com.google.vr180.media.muxer.MediaMuxFactory;
import com.google.vr180.media.rtmp.RealClock;
import com.google.vr180.media.video.VideoEncoder;
import com.google.vr180.media.video.VideoEncoderFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Helper class for managing the MediaMux video capture and recording pipeline. */
@TargetApi(21)
final class MediaMuxCapturePipelineManager {
  /** Status callback during an active capture session */
  public interface CaptureStatusCallback {
    /**
     * An error occurred during capture.
     *
     * @param status Status code indicating error or warning,
     */
    void onCaptureError(int status);
  }

  private static final String TAG = "MediaMuxCapturePipelineMgr";

  private static final int CODEC_DRAIN_DELAY_MILLIS = 250;
  private static final int CODEC_THREAD_PRIORITY = Process.THREAD_PRIORITY_DEFAULT;
  private static final int DEFAULT_MIN_BITRATE = 1500 * 1024;

  private final Context context;
  private final Handler codecHandler;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final MediaMuxFactory mediaMuxFactory;
  private final AudioInputFactory audioInputFactory;
  private final AudioEncoderFactory audioEncoderFactory;
  private final VideoEncoderFactory videoEncoderFactory;
  private final MotionEncoderFactory motionEncoderFactory;
  private final CaptureStatusCallback captureStatusCallback;
  private final VideoCaptureSource videoCaptureSource;
  private final MotionCaptureSource motionCaptureSource;
  private final ScheduledExecutorService abrExecutor = Executors.newSingleThreadScheduledExecutor();

  private HandlerThread codecThread;
  private VideoEncoder videoEncoder;
  private MediaEncoder audioEncoder;
  private MotionEncoder motionEncoder;
  private AudioInput audioInput;
  private MediaMux mediaMux;
  private boolean needPartialResultCleanup;
  private AbrController abrController;

  /** Whether the camera is currently capturing. */
  private boolean captureActive = false;

  MediaMuxCapturePipelineManager(
      Context context,
      VideoCaptureSource videoCaptureSource,
      MotionCaptureSource motionCaptureSource,
      CaptureStatusCallback statusCallback) {
    this.context = context.getApplicationContext();
    this.videoCaptureSource = videoCaptureSource;
    this.motionCaptureSource = motionCaptureSource;
    this.captureStatusCallback = statusCallback;
    this.mediaMuxFactory = MediaMuxFactory.getInstance();
    this.audioInputFactory = AudioInputFactory.getInstance();
    this.audioEncoderFactory = AudioEncoderFactory.getInstance();
    this.videoEncoderFactory = VideoEncoderFactory.getInstance();
    this.motionEncoderFactory = MotionEncoderFactory.getInstance();
    codecThread = new HandlerThread("CodecThread", CODEC_THREAD_PRIORITY);
    codecThread.setUncaughtExceptionHandler(
        (thread, throwable) -> {
          Log.e(TAG, "Codec thread died unexpectedly", throwable);
          sendCaptureError(MediaConstants.STATUS_ERROR);
        });
    codecThread.start();
    codecHandler = new Handler(codecThread.getLooper());
    this.motionCaptureSource.configure(/*sampleRateHz=*/ 200, codecHandler);
  }

  public synchronized void startCapture(
      final MediaFormat videoFormat,
      final MediaFormat audioFormat,
      final MediaFormat motionFormat,
      final String targetUri,
      final String targetKey,
      final MetadataInjector metadataInjector) {
    Log.i(TAG, "startCapture " + targetUri + " " + targetKey);
    codecHandler.post(
        () ->
            doStartCapture(
                videoFormat, audioFormat, motionFormat, targetUri, targetKey, metadataInjector));
  }

  private void doStartCapture(
      MediaFormat videoFormat,
      MediaFormat audioFormat,
      MediaFormat motionFormat,
      String targetUri,
      String targetKey,
      MetadataInjector metadataInjector) {
    verifyBackground();

    // Validate target URI and stream key.
    if (TextUtils.isEmpty(targetUri)) {
      sendCaptureError(MediaConstants.STATUS_STORAGE_ERROR);
      return;
    }

    // Start clean for good measure.
    resetAll();

    needPartialResultCleanup = true;

    // Set up the audio input.
    int status = prepareAudioInput(audioFormat);
    if (status != MediaConstants.STATUS_SUCCESS) {
      sendCaptureError(status);
      return;
    }

    // Set up the muxer.
    status = prepareMuxer(targetUri, targetKey, metadataInjector);
    if (status != MediaConstants.STATUS_SUCCESS) {
      sendCaptureError(status);
      return;
    }

    // Set up the audio encoder.
    status = prepareAudioEncoder(audioFormat, mediaMux);
    if (status != MediaConstants.STATUS_SUCCESS) {
      sendCaptureError(status);
      return;
    }

    // Set up the video encoder.
    status = prepareVideoEncoder(videoFormat, mediaMux);
    if (status != MediaConstants.STATUS_SUCCESS) {
      sendCaptureError(status);
      return;
    }

    // Set up the video source.
    status = prepareVideoSource();
    if (status != MediaConstants.STATUS_SUCCESS) {
      sendCaptureError(status);
      return;
    }

    // Set up the motion encoder.
    if (motionFormat != null) {
      status = prepareMotionEncoder(motionFormat, mediaMux);
      if (status != MediaConstants.STATUS_SUCCESS) {
        sendCaptureError(status);
        return;
      }
      // Set up the motion source.
      status = prepareMotionSource();
      if (status != MediaConstants.STATUS_SUCCESS) {
        sendCaptureError(status);
        return;
      }
    }

    // Create AbrController if required.
    if (requiresAbrController()) {
      abrController =
          new AbrController(
              DEFAULT_MIN_BITRATE,
              videoFormat.getInteger(MediaFormat.KEY_BIT_RATE),
              videoFormat.getInteger(MediaFormat.KEY_BIT_RATE),
              videoEncoder,
              mediaMux,
              abrExecutor,
              codecHandler,
              new RealClock());
    }

    // Start codec pipeline elements.
    status = startCodecPipeline();
    if (status != MediaConstants.STATUS_SUCCESS) {
      sendCaptureError(status);
      return;
    }

    captureActive = true;
  }

  public void stopCapture() {
    codecHandler.post(() -> doStopCapture());
  }

  private final EndOfStreamCallback endOfStreamCallback =
      new EndOfStreamCallback() {
        private boolean videoEos;
        private boolean audioEos;
        private boolean motionEos;

        @Override
        public void onEndOfStream(MediaEncoder mediaEncoder) {
          if (mediaEncoder == videoEncoder) {
            videoEos = true;
          }
          if (mediaEncoder == audioEncoder) {
            audioEos = true;
          }
          if (mediaEncoder == motionEncoder) {
            motionEos = true;
          }
          if (videoEos && audioEos && (motionEos || motionEncoder == null)) {
            videoEos = false;
            audioEos = false;
            motionEos = false;
            Preconditions.checkNotNull(codecHandler);
            codecHandler.post(codecDrainedAction);
          }
        }
      };

  private final Runnable codecDrainedAction = () -> codecPipelineStopped(true);
  private final Runnable codecDrainTimeoutAction = () -> codecPipelineStopped(false);

  private void doStopCapture() {
    verifyBackground();
    if (!captureActive) {
      Log.d(TAG, "Stop capture requested when not active");
    }

    // Stop the codec and wait for the result to drain.
    Log.d(TAG, "stopCodecPipeline");
    stopCodecPipeline();

    // Give up waiting for muxer to drain after a short while.
    Log.d(TAG, "codecDrainTimeoutAction");
    codecHandler.postDelayed(codecDrainTimeoutAction, CODEC_DRAIN_DELAY_MILLIS);
  }

  private void codecPipelineStopped(boolean didDrain) {
    verifyBackground();
    Log.d(
        TAG,
        "Codec pipeline stopped "
            + (didDrain ? "and drained " : "without draining ")
            + "completely");

    // Cancel other actions
    codecHandler.removeCallbacks(codecDrainedAction);
    codecHandler.removeCallbacks(codecDrainTimeoutAction);
    if (!needPartialResultCleanup) {
      Log.e(TAG, "Re-entered codec pipeline stop handler.  Skipping");
      sendCaptureError(MediaConstants.STATUS_NOT_ACTIVE);
      return;
    }

    // Try to stop the muxer.  If it closes successfully, obtain the final result.
    if (!stopMuxer()) {
      resetAll();
      sendCaptureError(MediaConstants.STATUS_CODEC_ERROR);
    }
    needPartialResultCleanup = false;
    captureActive = false;
  }

  protected void finalize() throws Throwable {
    if (codecThread != null) {
      codecThread.quit();
    }
    super.finalize();
  }

  private void stopCodecPipeline() {
    // Stop the video source first to avoid unresponsiveness caused by locking frame data.
    resetVideoSource();
    resetMotionSource();
    resetAbrController();
    signalEndOfStream();
  }

  private void signalEndOfStream() {
    if (videoEncoder != null) {
      videoEncoder.signalEndOfStream(endOfStreamCallback);
    }
    if (audioEncoder != null) {
      audioEncoder.signalEndOfStream(endOfStreamCallback);
    }
    if (motionEncoder != null) {
      motionEncoder.signalEndOfStream(endOfStreamCallback);
    }
  }

  private void cleanupPartialResults() {
    if (needPartialResultCleanup && mediaMux != null) {
      mediaMux.cleanupPartialResults();
    }
  }

  private void resetAll() {
    verifyBackground();
    stopCodecPipeline();
    stopMuxer();
    cleanupPartialResults();
    resetMuxer();
    resetEncoders();
    captureActive = false;
  }

  private void verifyBackground() {
    Preconditions.checkState(!Looper.getMainLooper().isCurrentThread());
  }

  // Send error status from the codec thread.
  private void sendCaptureError(final int status) {
    if (status == MediaConstants.STATUS_SUCCESS) {
      return;
    }
    codecHandler.post(() -> resetAll());
    mainHandler.post(
        () -> {
          if (captureStatusCallback != null) {
            captureStatusCallback.onCaptureError(status);
          }
        });
  }

  private int startCodecPipeline() {
    if (!hasAllComponents()) {
      return MediaConstants.STATUS_NOT_ACTIVE;
    }

    if (!videoEncoder.start()) {
      return MediaConstants.STATUS_CODEC_ERROR;
    }

    if (!audioEncoder.start()) {
      return MediaConstants.STATUS_CODEC_ERROR;
    }

    if (!videoCaptureSource.start()) {
      return MediaConstants.STATUS_CODEC_ERROR;
    }

    if (motionEncoder != null) {
      if (!motionEncoder.start()) {
        return MediaConstants.STATUS_CODEC_ERROR;
      }
      if (!motionCaptureSource.start()) {
        return MediaConstants.STATUS_CODEC_ERROR;
      }
    }

    if (abrController != null) {
      abrController.setActive(true);
      Log.i(TAG, "Activate AbrController");
    }

    return MediaConstants.STATUS_SUCCESS;
  }

  private boolean hasMotionComponentIfRequiredByMuxer() {
    if (mediaMux instanceof ChapteredFileMuxer) {
      return motionCaptureSource != null && motionEncoder != null;
    } else {
      // Muxer does not need motion component.
      return true;
    }
  }

  private boolean hasAllComponents() {
    return videoCaptureSource != null
        && videoEncoder != null
        && audioEncoder != null
        && audioInput != null
        && mediaMux != null
        && hasMotionComponentIfRequiredByMuxer();
  }

  private int prepareVideoSource() {
    // Connect the capture source with the video encoder.
    videoCaptureSource.setTargetSurface(videoEncoder.getInputSurface());

    VideoCaptureSource.ErrorCallback errorCallback =
        new VideoCaptureSource.ErrorCallback() {
          @Override
          public void onError(VideoCaptureSource videoSource, int errorCode) {
            Log.e(TAG, "Video source error");
            sendCaptureError(errorCode);
          }
        };
    videoCaptureSource.setErrorCallback(errorCallback, codecHandler);
    if (!videoCaptureSource.prepare()) {
      Log.e(TAG, "Could not prepare video source");
      return MediaConstants.STATUS_CODEC_ERROR;
    }

    return MediaConstants.STATUS_SUCCESS;
  }

  private void resetVideoSource() {
    if (videoCaptureSource != null) {
      videoCaptureSource.setErrorCallback(null /* callback */, null /* handler */);
      videoCaptureSource.stop();
    }
  }

  private final MediaEncoder.ErrorCallback encoderErrorCallback =
      new MediaEncoder.ErrorCallback() {
        @Override
        public void onError(MediaEncoder mediaEncoder, int errorCode) {
          Log.e(TAG, "Encoder error for " + mediaEncoder.getName());
          sendCaptureError(errorCode);
        }
      };

  private int prepareVideoEncoder(MediaFormat format, MediaMux mediaMux) {
    Preconditions.checkNotNull(mediaMux);

    // Create the video encoder.
    videoEncoder = videoEncoderFactory.createEncoder(context, format, mediaMux);
    if (videoEncoder == null) {
      Log.e(TAG, "Could not create video encoder");
      return MediaConstants.STATUS_CODEC_ERROR;
    }
    videoEncoder.setErrorCallback(encoderErrorCallback);

    return MediaConstants.STATUS_SUCCESS;
  }

  private void resetEncoders() {
    if (videoEncoder != null) {
      videoEncoder.setErrorCallback(null);
      videoEncoder.stop();
      videoEncoder.release();
      videoEncoder = null;
    }
    if (audioEncoder != null) {
      audioEncoder.setErrorCallback(null);
      audioEncoder.stop();
      audioEncoder.release();
      audioEncoder = null;
    }
    if (motionEncoder != null) {
      motionEncoder.setErrorCallback(null);
      motionEncoder.stop();
      motionEncoder.release();
      motionEncoder = null;
    }
  }

  private boolean requiresAbrController() {
    return !(mediaMux instanceof ChapteredFileMuxer);
  }

  private void resetAbrController() {
    if (abrController != null) {
      abrController.setActive(false);
      abrController = null;
    }
  }

  private int prepareAudioInput(MediaFormat format) {
    // Create the audio input for the encoder.
    audioInput = audioInputFactory.createAudioInput(format, codecHandler);
    if (audioInput == null) {
      Log.e(TAG, "Could not create audio input");
      return MediaConstants.STATUS_CODEC_ERROR;
    }
    audioInput.setIsEnabled(true);
    return MediaConstants.STATUS_SUCCESS;
  }

  private int prepareAudioEncoder(MediaFormat format, MediaMux mediaMux) {
    // Create the audio encoder with the attendant audio input.
    audioEncoder = audioEncoderFactory.createEncoder(format, audioInput, mediaMux);
    if (audioEncoder == null) {
      Log.e(TAG, "Could not create audio encoder");
      return MediaConstants.STATUS_CODEC_ERROR;
    }
    audioEncoder.setErrorCallback(encoderErrorCallback);

    return MediaConstants.STATUS_SUCCESS;
  }

  private final MotionCaptureSource.MotionEventListener motionEventListener =
      event -> motionEncoder.onMotionEvent(event);

  private int prepareMotionSource() {
    // Connect the capture source with the motion encoder.
    motionCaptureSource.addMotionEventListener(motionEventListener);
    motionCaptureSource.setErrorCallback(error -> sendCaptureError(error));
    return MediaConstants.STATUS_SUCCESS;
  }

  private void resetMotionSource() {
    if (motionCaptureSource != null) {
      motionCaptureSource.removeMotionEventListener(motionEventListener);
      motionCaptureSource.setErrorCallback(null /* callback */);
      motionCaptureSource.stop();
    }
  }

  private int prepareMotionEncoder(MediaFormat format, MediaMux mediaMux) {
    motionEncoder = motionEncoderFactory.createEncoder(format, mediaMux, codecHandler);
    if (motionEncoder == null) {
      Log.e(TAG, "Could not create motion encoder");
      return MediaConstants.STATUS_CODEC_ERROR;
    }
    motionEncoder.setErrorCallback(encoderErrorCallback);
    return MediaConstants.STATUS_SUCCESS;
  }

  private int prepareMuxer(String targetUri, String targetKey, MetadataInjector metadataInjector) {
    Preconditions.checkNotNull(targetUri);
    Preconditions.checkArgument(mediaMux == null);
    mediaMux = mediaMuxFactory.createMediaMux(context, targetUri, targetKey, metadataInjector);

    if (mediaMux == null) {
      Log.e(TAG, "Could not create muxer for " + targetUri);
      return MediaConstants.STATUS_STORAGE_ERROR;
    }

    mediaMux.setErrorCallback(
        new MediaMux.ErrorCallback() {
          @Override
          public void onError(int errorCode) {
            Log.e(TAG, "Muxer error: " + errorCode);
            sendCaptureError(errorCode);
          }
        });

    // Create a MediaMuxer.  Can't add the video track and start() the muxer here,
    // because the MediaFormat doesn't have the Magic Goodies.  These can only be
    // obtained from the encoder after it has started processing data.
    return mediaMux.prepare();
  }

  private boolean stopMuxer() {
    return (mediaMux != null && mediaMux.stop());
  }

  private void resetMuxer() {
    if (mediaMux != null) {
      stopMuxer();
      mediaMux.release();
      mediaMux = null;
    }
  }
}
