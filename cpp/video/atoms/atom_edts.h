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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_EDTS_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_EDTS_H_

#include <string>

#include "cpp/video/atom.h"

namespace vr180 {

// An Edit Box maps the presentation time-line to the media time-line as
// it is stored in the file. The Edit Box is a container for the edit lists.
// The Edit Box is optional. In the absence of this box, there is
// an implicit one-to-one mapping of these time-lines, and the presentation of
// a track starts at the beginning of the presentation.
// An empty edit is used to offset the start time of a track.
//
// Container: Track Box ('trak')
// Mandatory: No
// Quantity: Zero or one
//
// ISO/IEC 14496-12:2008 Section 8.6.5
class AtomEDTS : public Atom {
 public:
  AtomEDTS(const AtomSize header_size, const AtomSize data_size,
           const std::string& atom_type);
  AtomEDTS();
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_EDTS_H_
