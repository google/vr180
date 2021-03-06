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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_CAMM_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_CAMM_H_

#include <string>
#include "cpp/video/atom.h"

namespace vr180 {
// SampleEntry definition for Camera Motion Metadata Track.
class AtomCAMM : public Atom {
 public:
  AtomCAMM();
  AtomCAMM(Atom::AtomSize header_size, Atom::AtomSize data_size,
           const std::string& atom_type);

 private:
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  Atom::AtomSize GetDataSizeWithoutChildren() const override;

  std::string reserved_;
  uint16_t data_reference_index_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_CAMM_H_
