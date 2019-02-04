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

import com.google.vr180.CameraApi.Vector3;

/**
 * Interface for requesting the current direction of gravity relative to the camera.
 */
public interface GravityVectorProvider {
  /**
   * Gets the current direction of gravity (downward), or null if the gravity is unknown.
   *
   * The coordinate space of the returned vector should be OpenGL style coordinates, where x points
   * rightward, y points up, and negative z points in the direction the camera is looking.
   *
   * This gravity vector has a much higher tolerance for error than the video's encoded motion
   * track.
   */
  Vector3 getGravityVector();
}
