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

#include "cpp/video/atoms/visual_sample_entry.h"

#include <glog/logging.h>
#include "cpp/video/atom_registry.h"
#include "cpp/video/format_status.h"

namespace vr180 {

const uint32_t kVisualSampleEntryConstSize = 78;
const uint32_t kVendorSize = 4;
const uint32_t kEncoderNameSize = 31;

VisualSampleEntry::VisualSampleEntry(const AtomSize header_size,
                                     const AtomSize data_size,
                                     const std::string& atom_type)
    : Atom(header_size, data_size, atom_type),
      reserved_32_(0),
      reserved_16_(0),
      data_reference_index_(1),
      visual_version_(0),
      revision_(0),
      vendor_(kVendorSize, '\0'),
      temporal_quality_(0),
      spatial_quality_(0),
      width_(0),
      height_(0),
      horizontal_res_(0x48),
      vertical_res_(0x48),
      entry_data_size_(0),
      frames_per_sample_(1),
      encoder_name_size_(kEncoderNameSize),
      encoder_name_(encoder_name_size_, '\0'),
      bit_depth_(0x18),
      color_table_id_(0xFFFF) {}

VisualSampleEntry::VisualSampleEntry(const std::string& atom_type)
    : VisualSampleEntry(0, 0, atom_type) {
  Update();
}

FormatStatus VisualSampleEntry::WriteDataWithoutChildren(
    BinaryWriter* io) const {
  // Common to all sample entries.
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(reserved_32_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(reserved_16_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(data_reference_index_));

  // Specific to the visual sample entry.
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(visual_version_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(revision_));
  RETURN_IF_FORMAT_ERROR(io->PutString(vendor_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(temporal_quality_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(spatial_quality_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(width_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(height_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(horizontal_res_ << 16));
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(vertical_res_ << 16));
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(entry_data_size_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(frames_per_sample_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt8(encoder_name_size_));
  RETURN_IF_FORMAT_ERROR(io->PutString(encoder_name_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt16(bit_depth_));
  return io->PutUInt16(color_table_id_);
}

FormatStatus VisualSampleEntry::ReadDataWithoutChildren(BinaryReader* io) {
  // Common to all sample entries.
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&reserved_32_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&reserved_16_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&data_reference_index_));

  // Specific to the visual sample entry.
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&visual_version_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&revision_));
  RETURN_IF_FORMAT_ERROR(io->ReadString(&vendor_, kVendorSize));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&temporal_quality_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&spatial_quality_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&width_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&height_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&horizontal_res_));
  horizontal_res_ = horizontal_res_ >> 16;
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&vertical_res_));
  vertical_res_ = vertical_res_ >> 16;
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&entry_data_size_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&frames_per_sample_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt8(&encoder_name_size_));
  RETURN_IF_FORMAT_ERROR(io->ReadString(&encoder_name_, kEncoderNameSize));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt16(&bit_depth_));
  return io->ReadUInt16(&color_table_id_);
}

Atom::AtomSize VisualSampleEntry::GetDataSizeWithoutChildren() const {
  return kVisualSampleEntryConstSize;
}

// Macro to register visual sample entries.
#define REGISTER_SAMPLE(name)  \
  REGISTER_MODULE_INITIALIZER( \
      name, AtomRegistry::RegisterAtom<VisualSampleEntry>(#name))

REGISTER_SAMPLE(AVDJ);  // MJPEG with alpha-channel
REGISTER_SAMPLE(AVdh);  // DNXHR
REGISTER_SAMPLE(AVdn);  // DNXHD
REGISTER_SAMPLE(CFHD);  // Cineform
REGISTER_SAMPLE(DIVX);  // MPEG4
REGISTER_SAMPLE(WMV1);  // Windows media video
REGISTER_SAMPLE(WMV2);  // Windows media video
REGISTER_SAMPLE(WMV3);  // Windows media video
REGISTER_SAMPLE(XVID);  // MPEG4
REGISTER_SAMPLE(ai12);  // AVC-Intra 100M 1080p25/50
REGISTER_SAMPLE(ai13);  // AVC-Intra 100M 1080p24/30/60
REGISTER_SAMPLE(ai15);  // AVC-Intra 100M 1080i50
REGISTER_SAMPLE(ai16);  // AVC-Intra 100M 1080i60
REGISTER_SAMPLE(ai1p);  // AVC-Intra 100M 720p24/30/60
REGISTER_SAMPLE(ai1q);  // AVC-Intra 100M 720p25/50
REGISTER_SAMPLE(ai52);  // AVC-Intra  50M 1080p25/50
REGISTER_SAMPLE(ai53);  // AVC-Intra  50M 1080p24/30/60
REGISTER_SAMPLE(ai55);  // AVC-Intra  50M 1080i50
REGISTER_SAMPLE(ai56);  // AVC-Intra  50M 1080i60
REGISTER_SAMPLE(ai5q);  // AVC-Intra  50M 720p25/50
REGISTER_SAMPLE(ap4h);  // Apple ProRes 4444
REGISTER_SAMPLE(ap4x);  // Apple ProRes 4444 XQ
REGISTER_SAMPLE(apch);  // Apple ProRes 422 High Quality
REGISTER_SAMPLE(apcn);  // Apple ProRes 422 Standard Def.
REGISTER_SAMPLE(apco);  // Apple ProRes 422 Proxy
REGISTER_SAMPLE(apcs);  // Apple ProRes 422 LT
REGISTER_SAMPLE(av01);  // Alliance for open media (AV1)
REGISTER_SAMPLE(avc1);  // AVC-1/H.264
REGISTER_SAMPLE(dmb1);  // Motion JPEG OpenDML
REGISTER_SAMPLE(h263);  // H263
REGISTER_SAMPLE(hev1);  // HEVC/H.265
REGISTER_SAMPLE(hvc1);  // HEVC/H.265
REGISTER_SAMPLE(jpeg);  // PhotoJPEG
REGISTER_SAMPLE(mjp2);  // JPEG 2000 produced by FCP
REGISTER_SAMPLE(mjpa);  // Motion-JPEG (format A)
REGISTER_SAMPLE(mjpb);  // Motion-JPEG (format B)
REGISTER_SAMPLE(mp4v);  // MPEG4
REGISTER_SAMPLE(s263);  // H263
REGISTER_SAMPLE(vp09);  // Google VP9

}  // namespace vr180
