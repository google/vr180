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

#include "cpp/video/atoms/atom_sdtp.h"

#include <algorithm>

#include <glog/logging.h>

#include "cpp/video/atom_registry.h"
#include "cpp/video/format_status.h"

namespace vr180 {

const char kType[] = "sdtp";
const uint8_t kIFrameDescription = 32;
// This means the frame is droppable, it is a work around for iOS export.
const uint8_t kPFrameDescription = 24;
const uint64_t kFlagAndVersionSize = 4;

AtomSDTP::AtomSDTP() : FullAtom(8, 0, kType) {}

AtomSDTP::AtomSDTP(Atom::AtomSize header_size, Atom::AtomSize data_size,
                   const std::string& atom_type)
    : FullAtom(header_size, data_size, atom_type) {}

void AtomSDTP::PopulateFromKeyFrameIndices(
    const std::vector<uint32_t>& indices) {
  const uint32_t last_index = indices.back();
  frame_description_.clear();
  frame_description_.reserve(last_index);
  uint32_t next_key_frame_index = 0;
  for (uint32_t i = 0; i < last_index; ++i) {
    if (i == indices[next_key_frame_index] - 1) {
      // I-Frame.
      frame_description_.push_back(kIFrameDescription);
      next_key_frame_index++;
    } else {
      // P-Frame.
      frame_description_.push_back(kPFrameDescription);
    }
  }

  Update();
}

FormatStatus AtomSDTP::WriteDataWithoutChildren(BinaryWriter* io) const {
  RETURN_IF_FORMAT_ERROR(VersionAndFlags(io));
  for (uint32_t i = 0; i < frame_description_.size(); ++i) {
    RETURN_IF_FORMAT_ERROR(io->PutUInt8(frame_description_[i]));
  }
  return FormatStatus::OkStatus();
}

Atom::AtomSize AtomSDTP::GetDataSizeWithoutChildren() const {
  return frame_description_.size() + kFlagAndVersionSize;
}

FormatStatus AtomSDTP::ReadDataWithoutChildren(BinaryReader* io) {
  RETURN_IF_FORMAT_ERROR(ReadVersionAndFlags(io));
  frame_description_.clear();
  frame_description_.reserve(data_size() - kFlagAndVersionSize);
  for (int i = 0; i < data_size() - kFlagAndVersionSize; ++i) {
    uint8_t description = 0;
    RETURN_IF_FORMAT_ERROR(io->ReadUInt8(&description));
    frame_description_.push_back(description);
  }
  return FormatStatus::OkStatus();
}

REGISTER_ATOM(AtomSDTP, kType);

}  // namespace vr180
