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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_HDLR_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_HDLR_H_

#include <cstdint>
#include <string>
#include <vector>

#include "cpp/video/atoms/common.h"
#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"
#include "cpp/video/full_atom.h"

namespace vr180 {

// The Handler atom is a descendant of TRAK and indicates the type of the TRAK:
// audio, video, mdir, text, sbtl (short for "subtitle," an alternate spelling
// of "text").  HDLR may optionally contain information about the muxer that
// produced this file. There must be exactly one hdlr atom in each mdia.
//
// ISO/IEC 14496-12 Section 8.9
class AtomHDLR : public FullAtom {
 public:
  AtomHDLR(const AtomSize header_size, const AtomSize data_size,
           const std::string& atom_type);
  AtomHDLR();

  // Returns Track type corresponding to HDLR component subtype value.
  TrackMediaType GetTrackMediaType() const;

 private:
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  Atom::AtomSize GetDataSizeWithoutChildren() const override;
  void set_component_subtype(const std::string& subtype);

  std::string component_type_;
  std::string component_subtype_;
  std::string component_manufacturer_;
  uint32_t component_flags_;
  uint32_t component_flags_mask_;
  std::string component_name_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_HDLR_H_
