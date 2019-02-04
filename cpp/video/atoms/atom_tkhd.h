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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_TKHD_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_TKHD_H_

#include <cstdint>
#include <string>

#include "cpp/video/atom.h"
#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"
#include "cpp/video/full_atom.h"

namespace vr180 {

// The Track Header atom contains some information describing this track.  There
// must be exactly one TKHD in each TRAK.  The fields that must not be 0 are
// track_id (which must be less than MVHD.next_track_id) and duration (which is
// in track-time units).  You can either read this atom from an IOBuffer or
// construct an empty instance and set some fields.
//
// ISO/IEC 14496-12 Section 8.5
class AtomTKHD : public FullAtom {
 public:
  static const int kMatrixSize = 9;

  AtomTKHD(const AtomSize header_size, const AtomSize data_size,
           const std::string& atom_type);

  AtomTKHD();

  uint32_t track_id() const { return track_id_; }
  uint64_t duration() const { return duration_; }
  void set_track_id(const uint32_t i) { track_id_ = i; }
  void set_duration(const uint64_t d) {
    duration_ = d;
    if (d > UINT32_MAX) set_version(1);
  }

 private:
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  AtomSize GetDataSizeWithoutChildren() const override;

  uint64_t created_date_;
  uint64_t modified_date_;
  uint32_t track_id_;
  uint32_t reserved32_1_;
  uint64_t duration_;
  uint32_t reserved32_2_;
  uint32_t reserved32_3_;
  uint16_t layer_;
  uint16_t group_;
  uint16_t volume_;
  uint16_t reserved16_;
  uint32_t matrix_[kMatrixSize];
  uint32_t width_;
  uint32_t height_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_TKHD_H_
