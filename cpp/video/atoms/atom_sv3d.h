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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_SV3D_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_SV3D_H_

#include <cstdint>
#include <string>

#include "cpp/video/atom.h"

namespace vr180 {

// Defines a custom atom (SV3D) for specification of spherical video.
//
// https://github.com/google/spatial-media/blob/master/docs/
//     spherical-video-v2-rfc.md
//
// Stores additional information about spherical video content contained
// in this video track. Container: Video Sample Description box (e.g. avc1,
// mp4v, apcn).
class AtomSV3D : public Atom {
 public:
  AtomSV3D();
  AtomSV3D(Atom::AtomSize header_size, Atom::AtomSize data_size,
           const std::string& atom_type);
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_SV3D_H_
