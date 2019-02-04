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

package com.google.vr180.media.metadata;

import com.google.vr180.CameraApi.CaptureMode;

/** A provider for ProjectionMetadata so different apps can have different implementations. */
public interface ProjectionMetadataProvider {
  /** Returns the vr projeciton metadata for the given capture mode with the current settings. */
  public ProjectionMetadata getProjectionMetadata(CaptureMode mode);
}
