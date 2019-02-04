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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_MOOV_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_MOOV_H_

#include <cstdint>
#include <string>
#include <vector>

#include "cpp/video/atom.h"
#include "cpp/video/atoms/atom_trak.h"
#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"

namespace vr180 {

// The Movie atom is a top-level atom along with FTYP and MDAT.  There must be
// exactly one MOOV in each mp4 file.  MOOV contains one MVHD, zero or one IODS,
// and one or more TRAK atoms.
//
// ISO/IEC 14496-12 Section 8.1
class AtomMOOV : public Atom {
 public:
  AtomMOOV(const AtomSize header_size, const AtomSize data_size,
           const std::string& atom_type);

  AtomMOOV();

  // Returns all the tracks in this atom.
  std::vector<AtomTRAK*> tracks() const;

  // Returns the 1st video track.
  AtomTRAK* GetFirstVideoTrack() const;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_MOOV_H_
