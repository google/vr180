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

#include "cpp/video/atoms/atom_uuid.h"

#include <algorithm>
#include <string>
#include <vector>

#include <glog/logging.h>
#include "absl/strings/str_cat.h"
#include "cpp/video/atom_helpers.h"
#include "cpp/video/atom_registry.h"
#include "cpp/video/format_status.h"

namespace vr180 {

const char kType[] = "uuid";
const int kUuidSize = 16;

AtomUUID::AtomUUID(const AtomSize header_size, const AtomSize data_size,
                   const std::string& atom_type)
    : Atom(header_size, data_size, atom_type) {}

AtomUUID::AtomUUID() : AtomUUID(0, 0, kType) { Update(); }

FormatStatus AtomUUID::WriteDataWithoutChildren(BinaryWriter* io) const {
  if (uuid_.size() != kUuidSize) {
    return FormatStatus::Error(
        FormatErrorCode::FILE_FORMAT_ERROR,
        absl::StrCat("UUID must be ", kUuidSize,
                     " bytes in UUID atom, but was: ", uuid_.size()));
  }
  RETURN_IF_FORMAT_ERROR(io->PutString(uuid_));
  return io->PutString(value_);
}

FormatStatus AtomUUID::ReadDataWithoutChildren(BinaryReader* io) {
  RETURN_IF_FORMAT_ERROR(io->ReadString(&uuid_, kUuidSize));
  return io->ReadString(&value_, data_size() - kUuidSize);
}

Atom::AtomSize AtomUUID::GetDataSizeWithoutChildren() const {
  return uuid_.size() + value_.size();
}

REGISTER_ATOM(AtomUUID, kType);

}  // namespace vr180
