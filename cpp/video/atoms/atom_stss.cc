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

#include "cpp/video/atoms/atom_stss.h"

#include <algorithm>

#include <glog/logging.h>
#include "cpp/video/atom_registry.h"
#include "cpp/video/format_status.h"

namespace vr180 {

const char kType[] = "stss";

AtomSTSS::AtomSTSS(const AtomSize header_size, const AtomSize data_size,
                   const std::string& atom_type)
    : FullAtom(header_size, data_size, atom_type) {}

FormatStatus AtomSTSS::ReadDataWithoutChildren(BinaryReader* io) {
  RETURN_IF_FORMAT_ERROR(ReadVersionAndFlags(io));
  uint32_t num_key_frames;
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&num_key_frames));

  key_frame_indices_.reserve(num_key_frames);
  for (uint32_t i = 0; i < num_key_frames; ++i) {
    uint32_t frame_index = 0;
    RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&frame_index));
    key_frame_indices_.push_back(frame_index);
  }
  return FormatStatus::OkStatus();
}

std::vector<uint32_t> AtomSTSS::KeyFrameIndices() const {
  return std::vector<uint32_t>(key_frame_indices_);
}

FormatStatus AtomSTSS::WriteDataWithoutChildren(BinaryWriter* io) const {
  RETURN_IF_FORMAT_ERROR(VersionAndFlags(io));
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(key_frame_indices_.size()));
  for (uint32_t i = 0; i < key_frame_indices_.size(); ++i) {
    RETURN_IF_FORMAT_ERROR(io->PutUInt32(key_frame_indices_[i]));
  }
  return FormatStatus::OkStatus();
}

Atom::AtomSize AtomSTSS::GetDataSizeWithoutChildren() const {
  return data_size();
}


REGISTER_ATOM(AtomSTSS, kType);

}  // namespace vr180
