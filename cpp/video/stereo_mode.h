/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef VR180_CPP_VIDEO_STEREO_MODE_H_
#define VR180_CPP_VIDEO_STEREO_MODE_H_

namespace vr180 {
// https://github.com/google/spatial-media/blob/master/docs/spherical-video-v2-rfc.md
enum class StereoMode { MONO = 0, TOP_BOTTOM = 1, LEFT_RIGHT = 2 };

}  // namespace vr180
#endif  // VR180_CPP_VIDEO_STEREO_MODE_H_
