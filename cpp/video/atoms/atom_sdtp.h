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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_SDTP_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_SDTP_H_

#include <string>
#include <vector>

#include "cpp/video/full_atom.h"

namespace vr180 {

// Sample Dependency Flags atom uses one byte per sample as a bit field that
// describes dependency information.
//
// ISO/IEC 14496-12 Section 8.6.4.1
class AtomSDTP : public FullAtom {
 public:
  AtomSDTP();
  AtomSDTP(Atom::AtomSize header_size, Atom::AtomSize data_size,
           const std::string& atom_type);

  // Creates an array of flags for each frame between the first and last index
  // in indices. This assume every index in indices is a leading frame, every
  // other frame is droppable.
  void PopulateFromKeyFrameIndices(const std::vector<uint32_t>& indices);

 private:
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  Atom::AtomSize GetDataSizeWithoutChildren() const override;

  // The description of all the frame according to ISO/IEC 14496-12.
  // It might be more memory efficient to just keep track of key frame if we
  // only have this information to generate the model.
  std::vector<uint8_t> frame_description_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_SDTP_H_
