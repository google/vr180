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

#ifndef VR180_CPP_VIDEO_FORMAT_FULL_ATOM_H_
#define VR180_CPP_VIDEO_FORMAT_FULL_ATOM_H_

#include <cstdint>
#include <string>

#include "cpp/video/atom.h"
#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"
#include "cpp/video/format_status.h"

namespace vr180 {

// Many atoms contain a version number and flags fields.
// The semantics of these two fields are:
// - version is an 8 bit integer that specifies the version of this
//   format of the Atom.
// - flags is a 24 bit map of flags (depends on atom type)
//
// Class FullAtom was added to atom class hierarchy to avoid code duplication.
// This class contains routines to process these two fields.
class FullAtom : public Atom {
 public:
  static const int32_t kVersionAndFlagsSize = 4;

  uint8_t version() const { return version_; }
  uint32_t flags() const { return flags_ & 0xFFFFFF; }

  void set_version(const uint8_t version) {
    version_ = version;
    // Size of some atoms depends on their version, so it's required to
    // recompute atom size each time the version is being changed to keep
    // atom tree in actual state
    Update();
  }
  void set_flags(const uint32_t flags) {
    flags_ = flags & 0xFFFFFF;
    // Size of some atoms depends on their flags, so it's required to
    // recompute atom size each time the flags is being changed to keep
    // atom tree in actual state
    Update();
  }

 protected:
  FullAtom(const AtomSize header_size, const AtomSize data_size,
           const std::string& atom_type)
      : Atom(header_size, data_size, atom_type), version_(0), flags_(0) {}

  FormatStatus VersionAndFlags(BinaryWriter* io) const {
    RETURN_IF_FORMAT_ERROR(io->PutUInt8(version_));
    return io->PutUInt24(flags_);
  }

  FormatStatus ReadVersionAndFlags(BinaryReader* io) {
    RETURN_IF_FORMAT_ERROR(io->ReadUInt8(&version_));
    return io->ReadUInt24(&flags_);
  }

 private:
  uint8_t version_;
  uint32_t flags_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_FULL_ATOM_H_
