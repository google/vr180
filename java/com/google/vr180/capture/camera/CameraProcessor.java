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

package com.google.vr180.capture.camera;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CaptureResult;
import com.google.vr180.common.opengl.Texture;

/**
 * An interface for processing blocks that consume a {@link Texture} that is updated by the Android
 * Camera via a {@link SurfaceTexture}.
 */
public interface CameraProcessor {
  /**
   * Notify that the underlying {@link Texture} has been created.
   *
   * @param texture the {@link Texture} that will receive the updates
   */
  void onTextureCreated(Texture texture);

  /**
   * Notify the content of the texture has been updated
   *
   * @param textureMatrix the texture matrix defining the valid content
   * @param timestampNs the timestamp of the content in nano seconds
   */
  void onFrameAvailable(float[] textureMatrix, long timestampNs);

  /**
   * Notify the capture result for a single frame.
   *
   * @param result the capture result for the captured frame.
   */
  void onCaptureResult(CaptureResult result);

  /**
   * Block until {@link CameraProcessor} is finished processing the content of the last {@link
   * #onFrameAvailable} call.
   */
  void waitUntilReady();
}
