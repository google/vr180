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

package com.google.vr180.capture.photo;

import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.view.Surface;
import java.util.List;

/** Interface for handling the image processing tasks of a photo capture flow. */
public interface PhotoCapturer {
  /** Returns the width of the output surface. */
  public int getWidth();

  /** Returns the height of the output surface. */
  public int getHeight();

  /** Returns a list of surfaces for camera preview. */
  public List<Surface> getTargetSurfaces();

  /** Releases all resources. Other function calls are not valid after this. */
  public void close();

  /**
   * Returns true if the capturer is ready to accept photo requests. Calls to addPhotoRequest may be
   * ignored if this returns false.
   */
  public boolean isReadyForCapture();

  /** Returns true if the capturer has any unprocessed photo requests. */
  public boolean hasPendingRequests();

  /** Adds a request for a photo. */
  public void addPhotoRequest(int id, String path);

  /** Notifies the photo capturer that a camera capture result is ready for processing. */
  public void onCaptureResult(CaptureResult result);

  /** Saves image to path and notifies callback on success. Must close image. */
  public void processPhoto(Image image, int id, String path);
}
