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

#include "cpp/video/atoms/atom_elst.h"

#include <cstdint>
#include <glog/logging.h>

#include "cpp/video/atom.h"
#include "cpp/video/atom_registry.h"
#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"
#include "cpp/video/format_status.h"
#include "cpp/video/full_atom.h"

namespace vr180 {

const char kType[] = "elst";
const Atom::AtomSize kConstELSTSize = 8;
const uint32_t kEntryV0Size = 12;
const uint32_t kEntryV1Size = 20;

AtomELST::AtomELST(const AtomSize header_size, const AtomSize data_size,
                   const std::string& atom_type)
    : FullAtom(header_size, data_size, atom_type) {}

AtomELST::AtomELST() : FullAtom(0, 0, kType) { Update(); }

void AtomELST::AddEntry(const Entry& entry) {
  if (entry.segment_duration > UINT32_MAX || entry.media_time > INT32_MAX ||
      entry.media_time < INT32_MIN) {
    set_version(1);
  }
  entries_.push_back(entry);
  Update();
}

Atom::AtomSize AtomELST::EntrySize() const {
  return version() == 1 ? kEntryV1Size : kEntryV0Size;
}

bool AtomELST::CheckNumEntries(const uint32_t num) const {
  AtomSize entries_size = num * EntrySize();

  if (data_size() < kConstELSTSize + entries_size) {
    LOG(WARNING) << __FUNCTION__ << " NumEntries is insane: " << num;
    return false;
  }

  return true;
}

FormatStatus AtomELST::WriteDataWithoutChildren(BinaryWriter* io) const {
  RETURN_IF_FORMAT_ERROR(VersionAndFlags(io));
  uint32_t count = NumEntries();
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(count));
  Entry entry;
  for (int i = 0; i < count; i++) {
    entry = GetEntry(i);
    if (version() == 1) {
      RETURN_IF_FORMAT_ERROR(io->PutUInt64(entry.segment_duration));
      RETURN_IF_FORMAT_ERROR(
          io->PutUInt64(static_cast<uint64_t>(entry.media_time)));
    } else {
      RETURN_IF_FORMAT_ERROR(io->PutUInt32(
          static_cast<uint32_t>(entry.segment_duration & UINT32_MAX)));
      RETURN_IF_FORMAT_ERROR(
          io->PutUInt32(static_cast<uint32_t>(entry.media_time & UINT32_MAX)));
    }
    RETURN_IF_FORMAT_ERROR(
        io->PutUInt16(static_cast<uint16_t>(entry.media_rate_integer)));
    RETURN_IF_FORMAT_ERROR(
        io->PutUInt16(static_cast<uint16_t>(entry.media_rate_fraction)));
  }

  return FormatStatus::OkStatus();
}

FormatStatus AtomELST::ReadDataWithoutChildren(BinaryReader* io) {
  RETURN_IF_FORMAT_ERROR(ReadVersionAndFlags(io));
  uint32_t count;
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&count));
  if (!CheckNumEntries(count)) {
    return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                               "entry count does not match data size");
  }
  entries_.reserve(count);
  Entry entry;
  for (int i = 0; i < count; i++) {
    if (version() == 1) {
      uint64_t media_time;
      RETURN_IF_FORMAT_ERROR(io->ReadUInt64(&entry.segment_duration));
      RETURN_IF_FORMAT_ERROR(io->ReadUInt64(&media_time));
      entry.media_time = static_cast<int64_t>(media_time);
    } else {
      uint32_t segment_duration;
      uint32_t media_time;
      RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&segment_duration));
      RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&media_time));
      entry.segment_duration = static_cast<uint64_t>(segment_duration);
      entry.media_time = static_cast<int64_t>(media_time);
    }
    uint16_t media_rate_integer;
    uint16_t media_rate_fraction;
    RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&media_rate_integer));
    RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&media_rate_fraction));
    entry.media_rate_integer = static_cast<int16_t>(media_rate_integer);
    entry.media_rate_fraction = static_cast<int16_t>(media_rate_fraction);
    entries_.push_back(entry);
  }

  return FormatStatus::OkStatus();
}

Atom::AtomSize AtomELST::GetDataSizeWithoutChildren() const {
  return kConstELSTSize + NumEntries() * EntrySize();
}

REGISTER_ATOM(AtomELST, kType);

}  // namespace vr180
