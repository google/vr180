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

#include "cpp/video/atoms/atom_stco.h"

#include <algorithm>

#include <glog/logging.h>

#include "absl/strings/str_format.h"
#include "cpp/video/atom_registry.h"
#include "cpp/video/format_status.h"

namespace vr180 {

const char kType32[] = "stco";
const char kType64[] = "co64";

static const uint32_t kConstSTCOBaseSize = 8;
static const uint32_t kChunkOffsetSize32 = 4;
static const uint32_t kChunkOffsetSize64 = 8;

AtomSTCO::AtomSTCO() : AtomSTCO(0, 0, kType32) { Update(); }

AtomSTCO::AtomSTCO(const AtomSize header_size, const AtomSize data_size,
                   const std::string& atom_type)
    : FullAtom(header_size, data_size, atom_type),
      moov_size_delta_(0),
      max_chunk_offset_(0) {}

void AtomSTCO::ReviewAtomType() {
  if (max_chunk_offset_ + moov_size_delta_ > UINT32_MAX) {
    set_atom_type(kType64);
    Update();
  }
}

void AtomSTCO::AdjustChunkOffsets(const int64_t adjustment) {
  moov_size_delta_ += adjustment;
  ReviewAtomType();
  Update();
}

FormatStatus AtomSTCO::ReadDataWithoutChildren(BinaryReader* io) {
  RETURN_IF_FORMAT_ERROR(ReadVersionAndFlags(io));

  uint32_t num_chunks;
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&num_chunks));
  if (!CheckNumberOfChunks(num_chunks)) {
    return FormatStatus::Error(
        FormatErrorCode::FILE_FORMAT_ERROR,
        absl::StrFormat(
            "Number of chunks is not consistent with atom size (%d) "
            "reading STCO atom",
            num_chunks));
  }
  chunk_offsets_.clear();
  chunk_offsets_.reserve(num_chunks);
  for (int i = 0; i < num_chunks; ++i) {
    uint64_t offset = 0;
    if (atom_type() == kType32) {
      uint32_t offset_32 = 0;
      RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&offset_32));

      offset = offset_32;
    } else {
      RETURN_IF_FORMAT_ERROR(io->ReadUInt64(&offset));
    }
    chunk_offsets_.push_back(offset);
  }
  if (!chunk_offsets_.empty()) {
    max_chunk_offset_ =
        *std::max_element(chunk_offsets_.begin(), chunk_offsets_.end());
  }
  return FormatStatus::OkStatus();
}

FormatStatus AtomSTCO::WriteDataWithoutChildren(BinaryWriter* io) const {
  RETURN_IF_FORMAT_ERROR(VersionAndFlags(io));

  RETURN_IF_FORMAT_ERROR(io->PutUInt32(chunk_offsets_.size()));
  if (atom_type() == kType32) {
    for (unsigned i = 0; i < chunk_offsets_.size(); ++i) {
      RETURN_IF_FORMAT_ERROR(
          io->PutUInt32(chunk_offsets_[i] + moov_size_delta_));
    }
  } else {
    for (unsigned i = 0; i < chunk_offsets_.size(); ++i) {
      RETURN_IF_FORMAT_ERROR(
          io->PutUInt64(chunk_offsets_[i] + moov_size_delta_));
    }
  }

  return FormatStatus::OkStatus();
}

Atom::AtomSize AtomSTCO::GetDataSizeWithoutChildren() const {
  const AtomSize chunk_offset_size =
      (atom_type() == kType32) ? kChunkOffsetSize32 : kChunkOffsetSize64;
  return kConstSTCOBaseSize + chunk_offsets_.size() * chunk_offset_size;
}

bool AtomSTCO::CheckNumberOfChunks(const uint32_t num) const {
  const AtomSize chunk_offset_size =
      (atom_type() == kType32) ? kChunkOffsetSize32 : kChunkOffsetSize64;
  const AtomSize sum_chunk_offset_size = num * chunk_offset_size;
  return data_size() >= kConstSTCOBaseSize + sum_chunk_offset_size;
}

REGISTER_ATOM(AtomSTCO, kType32);
REGISTER_ATOM(AtomSTCO, kType64);

}  // namespace vr180
