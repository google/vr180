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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STSD_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STSD_H_

#include <cstdint>
#include <string>
#include <vector>

#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"
#include "cpp/video/full_atom.h"

namespace vr180 {

// The sample description table contains detailed information about the codec
// used and any initialization needed for that codec.
// There must be exactly one stsd in each stbl.
//
// ISO/IEC 14496-12 Section 8.16
class AtomSTSD : public FullAtom {
 public:
  AtomSTSD(const AtomSize header_size, const AtomSize data_size,
           const std::string& atom_type);
  AtomSTSD();

 private:
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  Atom::AtomSize GetDataSizeWithoutChildren() const override;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STSD_H_
