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

#include "cpp/video/atoms/atom_stsd.h"

#include <algorithm>
#include <string>
#include <vector>

#include <glog/logging.h>
#include "cpp/video/atom_registry.h"

namespace vr180 {

const char kType[] = "stsd";
const int kConstSTSDSize = 8;

AtomSTSD::AtomSTSD(const AtomSize header_size, const AtomSize data_size,
                   const std::string& atom_type)
    : FullAtom(header_size, data_size, atom_type) {}

AtomSTSD::AtomSTSD() : FullAtom(0, 0, kType) { Update(); }

FormatStatus AtomSTSD::WriteDataWithoutChildren(BinaryWriter* io) const {
  RETURN_IF_FORMAT_ERROR(VersionAndFlags(io));
  return io->PutUInt32(NumChildren());
}

FormatStatus AtomSTSD::ReadDataWithoutChildren(BinaryReader* io) {
  RETURN_IF_FORMAT_ERROR(ReadVersionAndFlags(io));
  uint32_t descriptor_count = 0;
  return io->ReadUInt32(&descriptor_count);
}

Atom::AtomSize AtomSTSD::GetDataSizeWithoutChildren() const {
  return kConstSTSDSize;
}

REGISTER_ATOM(AtomSTSD, kType);

}  // namespace vr180
