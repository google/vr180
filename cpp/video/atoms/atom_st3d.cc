// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "cpp/video/atoms/atom_st3d.h"

#include "cpp/video/atom_registry.h"
#include "cpp/video/stereo_mode.h"

namespace vr180 {

const char kType[] = "st3d";
const uint32_t kConstST3DSize = FullAtom::kVersionAndFlagsSize + 1;

AtomST3D::AtomST3D() : FullAtom(0, 0, kType), stereo_mode_(StereoMode::MONO) {
  Update();
}

AtomST3D::AtomST3D(Atom::AtomSize header_size, Atom::AtomSize data_size,
                   const std::string& atom_type)
    : FullAtom(header_size, data_size, atom_type),
      stereo_mode_(StereoMode::MONO) {}

FormatStatus AtomST3D::WriteDataWithoutChildren(BinaryWriter* io) const {
  RETURN_IF_FORMAT_ERROR(VersionAndFlags(io));
  return io->PutUInt8(static_cast<uint8_t>(stereo_mode_));
}

FormatStatus AtomST3D::ReadDataWithoutChildren(BinaryReader* io) {
  RETURN_IF_FORMAT_ERROR(ReadVersionAndFlags(io));
  uint8_t mode;
  RETURN_IF_FORMAT_ERROR(io->ReadUInt8(&mode));
  stereo_mode_ = static_cast<StereoMode>(mode);
  return FormatStatus::OkStatus();
}

Atom::AtomSize AtomST3D::GetDataSizeWithoutChildren() const {
  return kConstST3DSize;
}

REGISTER_ATOM(AtomST3D, kType);

}  // namespace vr180
