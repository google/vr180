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

package com.google.vr180.media.photo;

/** A java wrapper for JNI interface for creating a vr180 formatted photo. */
public class PhotoWriter {
  static {
    System.loadLibrary("camera");
  }

  /**
   * Saves a VR180 formatted jpeg to 'outputPath' by compressing left and right eye images
   * separately and storing the right eye and pano data in the xmp extended section.
   *
   * @param rgba - an array of bytes containing a stereo (left/right or top/bottom) image
   * @param width - width of the stereo image in pixels
   * @param height - height of the stereo image in pixels
   * @param stride - number of bytes between rows of the stereo image
   * @param fovX - the horiontal field of view of a single eye from the stereo image
   * @param fovY - the vertical field of view of a single eye from the stereo image
   * @param angleAxis - the angle axis orientation of the camera when the photo was taken
   * @param stereoMode - the stereo mode of the image
   * @param outputPath - the file path to save the formatted image to
   */
  public static native boolean nativeWriteVRPhotoToFile(
      byte[] rgba,
      int width,
      int height,
      int stride,
      float fovX,
      float fovY,
      float[] angleAxis,
      int stereoMode,
      String outputPath);
}
