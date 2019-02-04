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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_VISUAL_SAMPLE_ENTRY_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_VISUAL_SAMPLE_ENTRY_H_

#include <cstdint>
#include <string>
#include <vector>

#include "cpp/video/atom.h"
#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"

namespace vr180 {

// The VideoSampleEntry is a base class for all the atoms containing video-
// specific metadata, since they all have the same structure (see chapter 8.16.2
// in ISO/IEC 14496-12:2005(E).
class VisualSampleEntry : public Atom {
 public:
  VisualSampleEntry(const AtomSize header_size, const AtomSize data_size,
                    const std::string& atom_type);
  explicit VisualSampleEntry(const std::string& atom_type);

 private:
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  Atom::AtomSize GetDataSizeWithoutChildren() const override;

  uint32_t reserved_32_;
  uint16_t reserved_16_;
  uint16_t data_reference_index_;
  uint16_t visual_version_;
  uint16_t revision_;
  std::string vendor_;
  uint32_t temporal_quality_;
  uint32_t spatial_quality_;
  uint16_t width_;
  uint16_t height_;
  uint32_t horizontal_res_;
  uint32_t vertical_res_;
  uint32_t entry_data_size_;
  uint16_t frames_per_sample_;
  uint8_t encoder_name_size_;
  std::string encoder_name_;
  uint16_t bit_depth_;
  uint16_t color_table_id_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_VISUAL_SAMPLE_ENTRY_H_
