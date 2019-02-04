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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_TRAK_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_TRAK_H_

#include <cstdint>
#include <string>
#include <vector>

#include "cpp/video/atom.h"
#include "cpp/video/atoms/atom_stbl.h"
#include "cpp/video/atoms/common.h"
#include "cpp/video/atoms/visual_sample_entry.h"
#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"

namespace vr180 {

// The Track atom describes a media track in this mp4 file.
// There may be any number of trak atoms in a moov.
//
// ISO/IEC 14496-12 Section 8.4
class AtomTRAK : public Atom {
 public:
  AtomTRAK(const AtomSize header_size, const AtomSize data_size,
           const std::string& atom_type);
  AtomTRAK();

  // Returns the type of track. This value is determined from
  // HDLR::component_subtype value. Definition of known track types is
  // located in track_media_types.h file.
  TrackMediaType track_type();

  // Returns of the VisualSampleEntry in this track, or nullptr
  // if there is none.
  VisualSampleEntry* visual_sample_entry();

  // Returns the STBL atom, or nullptr if there is none.
  AtomSTBL* atom_stbl();

 private:
  void FindNamedChildren();
  TrackMediaType track_type_;
  VisualSampleEntry* visual_sample_entry_;
  AtomSTBL* stbl_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_TRAK_H_
