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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STSS_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STSS_H_

#include <string>
#include <vector>

#include "cpp/video/full_atom.h"

namespace vr180 {

// Sync sample table, contains the list of all key frame indexes.
//
// ISO/IEC 14496-12 Section 8.6.2
class AtomSTSS : public FullAtom {
 public:
  AtomSTSS(Atom::AtomSize header_size, Atom::AtomSize data_size,
           const std::string& atom_type);

  std::vector<uint32_t> KeyFrameIndices() const;

 private:
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  Atom::AtomSize GetDataSizeWithoutChildren() const override;
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;

  // List of key frame indices.
  std::vector<uint32_t> key_frame_indices_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STSS_H_
