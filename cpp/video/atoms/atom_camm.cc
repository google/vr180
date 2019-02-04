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

#include "cpp/video/atoms/atom_camm.h"

#include "cpp/video/atom_registry.h"
#include "cpp/video/format_status.h"

namespace vr180 {

const char kType[] = "camm";
const int kReservedSize = 6;

AtomCAMM::AtomCAMM()
    : Atom(0, 0, kType), reserved_(kReservedSize, 0), data_reference_index_(0) {
  Update();
}

AtomCAMM::AtomCAMM(const Atom::AtomSize header_size,
                   const Atom::AtomSize data_size, const std::string& atom_type)
    : Atom(header_size, data_size, atom_type) {}

FormatStatus AtomCAMM::WriteDataWithoutChildren(BinaryWriter* io) const {
  RETURN_IF_FORMAT_ERROR(io->PutString(reserved_));
  return io->PutUInt16(data_reference_index_);
}

FormatStatus AtomCAMM::ReadDataWithoutChildren(BinaryReader* io) {
  RETURN_IF_FORMAT_ERROR(io->ReadString(&reserved_, kReservedSize));
  return io->ReadUInt16(&data_reference_index_);
}

Atom::AtomSize AtomCAMM::GetDataSizeWithoutChildren() const {
  return reserved_.size() + sizeof(data_reference_index_);
}

REGISTER_ATOM(AtomCAMM, kType);

}  // namespace vr180
