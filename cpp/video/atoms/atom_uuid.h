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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_UUID_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_UUID_H_

#include <cstdint>
#include <string>
#include <vector>

#include "cpp/video/atom.h"
#include "cpp/video/atoms/common.h"
#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"

namespace vr180 {

// The UUID atom is a child of TRAK and is used to store the Spherical V1
// Metadata.
//
// See https://github.com/google/spatial-media/blob/master/docs/spherical-video-rfc.md#mp4
class AtomUUID : public Atom {
 public:
  AtomUUID(const AtomSize header_size, const AtomSize data_size,
           const std::string& atom_type);
  AtomUUID();

  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  AtomSize GetDataSizeWithoutChildren() const override;

  const std::string& uuid() { return uuid_; }
  void set_uuid(const std::string& uuid) {
    uuid_ = uuid;
    Update();
  }

  const std::string& value() { return value_; }
  void set_value(const std::string& value) {
    value_ = value;
    Update();
  }

 private:
  std::string uuid_;
  std::string value_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_UUID_H_
