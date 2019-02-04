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

#include "cpp/video/atoms/atom_tkhd.h"

#include <string.h>

#include <glog/logging.h>
#include "cpp/video/atom_registry.h"

namespace vr180 {

const char kType[] = "tkhd";

AtomTKHD::AtomTKHD(const AtomSize header_size, const AtomSize data_size,
                   const std::string& atom_type)
    : FullAtom(header_size, data_size, atom_type),
      created_date_(0),
      modified_date_(0),
      track_id_(0),
      reserved32_1_(0),
      duration_(0),
      reserved32_2_(0),
      reserved32_3_(0),
      layer_(0),
      group_(0),
      volume_(0),
      reserved16_(0),
      width_(0),
      height_(0) {
  memset(matrix_, 0, sizeof(matrix_));
  matrix_[0] = 0x00010000;
  matrix_[4] = 0x00010000;
  matrix_[8] = 0x40000000;
}

AtomTKHD::AtomTKHD() : AtomTKHD(0, 0, kType) {
  memset(matrix_, 0, sizeof(matrix_));
  matrix_[0] = 0x00010000;
  matrix_[4] = 0x00010000;
  matrix_[8] = 0x40000000;
  Update();
}

FormatStatus AtomTKHD::WriteDataWithoutChildren(BinaryWriter* io) const {
  RETURN_IF_FORMAT_ERROR(VersionAndFlags(io));

  if (version() == 1) {
    RETURN_IF_FORMAT_ERROR(io->PutUInt64(created_date_));
    RETURN_IF_FORMAT_ERROR(io->PutUInt64(modified_date_));
    RETURN_IF_FORMAT_ERROR(io->PutUInt32(track_id_));
    RETURN_IF_FORMAT_ERROR(io->PutUInt32(reserved32_1_));
    RETURN_IF_FORMAT_ERROR(io->PutUInt64(duration_));
  } else {
    RETURN_IF_FORMAT_ERROR(
        io->PutUInt32(static_cast<uint32_t>(created_date_ & UINT32_MAX)));
    RETURN_IF_FORMAT_ERROR(
        io->PutUInt32(static_cast<uint32_t>(modified_date_ & UINT32_MAX)));
    RETURN_IF_FORMAT_ERROR(io->PutUInt32(track_id_));
    RETURN_IF_FORMAT_ERROR(io->PutUInt32(reserved32_1_));
    RETURN_IF_FORMAT_ERROR(
        io->PutUInt32(static_cast<uint32_t>(duration_ & UINT32_MAX)));
  }
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(reserved32_2_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(reserved32_3_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(layer_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(group_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(volume_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(reserved16_));

  for (int i = 0; i < kMatrixSize; i++) {
    RETURN_IF_FORMAT_ERROR(io->PutUInt32(matrix_[i]));
  }

  // width and height are fixed point xxxx.yyyy. Assumes integers.
  uint32_t width = width_ << 16;
  uint32_t height = height_ << 16;
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(width));
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(height));
  return FormatStatus::OkStatus();
}

FormatStatus AtomTKHD::ReadDataWithoutChildren(BinaryReader* io) {
  RETURN_IF_FORMAT_ERROR(ReadVersionAndFlags(io));

  if (version() == 1) {
    RETURN_IF_FORMAT_ERROR(io->ReadUInt64(&created_date_));
    RETURN_IF_FORMAT_ERROR(io->ReadUInt64(&modified_date_));
    RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&track_id_));
    RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&reserved32_1_));
    RETURN_IF_FORMAT_ERROR(io->ReadUInt64(&duration_));
  } else {
    uint32_t created_date_32;
    uint32_t modified_date_32;
    uint32_t duration_32;
    RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&created_date_32));
    RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&modified_date_32));
    RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&track_id_));
    RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&reserved32_1_));
    RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&duration_32));
    created_date_ = static_cast<uint64_t>(created_date_32);
    modified_date_ = static_cast<uint64_t>(modified_date_32);
    duration_ = static_cast<uint64_t>(duration_32);
  }
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&reserved32_2_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&reserved32_3_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&layer_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&group_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&volume_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&reserved16_));

  for (int i = 0; i < kMatrixSize; i++) {
    RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&matrix_[i]));
  }

  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&width_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&height_));
  // width and height are fixed point xxxx.yyyy. Assumes integers.
  width_ >>= 16;
  height_ >>= 16;
  return FormatStatus::OkStatus();
}

Atom::AtomSize AtomTKHD::GetDataSizeWithoutChildren() const {
  AtomSize data_size = version() == 1 ? 96 : 84;
  return data_size;
}

REGISTER_ATOM(AtomTKHD, kType);

}  // namespace vr180
