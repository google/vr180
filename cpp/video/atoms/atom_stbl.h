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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STBL_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STBL_H_

#include <string>

#include "cpp/video/atom.h"

namespace vr180 {

// The Sample Table atom contains most of the interesting timing and spacing
// information about this media file.  There must be exactly one stbl in each
// minf.
//
// ISO/IEC 14496-12 Section 8.14
class AtomSTBL : public Atom {
 public:
  AtomSTBL(const Atom::AtomSize header_size, const Atom::AtomSize data_size,
           const std::string& atom_type);
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STBL_H_
