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

#include "cpp/video/atoms/atom_hdlr.h"

#include <algorithm>
#include <string>
#include <vector>

#include <glog/logging.h>
#include "cpp/video/atom_registry.h"

namespace vr180 {
const char kType[] = "hdlr";
const int32_t kTypeSize = 4;

const char kVisualSubtype[] = "vide";
const char kSoundSubtype[] = "soun";
const char kTextSubtype[] = "text";
const char kSubtitleSubtype[] = "sbtl";
const char kBaseSubtype[] = "gnrc";
const char kClosedCaptionSubtype[] = "clcp";
const char kHintSubtype[] = "hint";
const char kMPEGSubtype[] = "MPEG";
const char kMuxedSubtype[] = "muxx";
const char kODSMSubtype[] = "odsm";
const char kSDSMSubtype[] = "sdsm";
const char kQuartzComposerSubtype[] = "qzr ";
const char kSkinSubtype[] = "skin";
const char kSpriteSubtype[] = "sprt";
const char kStreamingSubtype[] = "strm";
const char kTimecodeSubtype[] = "tmcd";
const char kTimedMetaSubtype[] = "tmet";
const char kTweenSubtype[] = "twen";
const char kMdirSubtype[] = "mdir";
const char kMetaSubtype[] = "meta";

std::string CapSize(const std::string& value, int max_size) {
  if (value.size() <= max_size) {
    return value;
  }
  return value.substr(0, max_size);
}

AtomHDLR::AtomHDLR(const AtomSize header_size, const AtomSize data_size,
                   const std::string& atom_type)
    : FullAtom(header_size, data_size, atom_type),
      component_flags_(0),
      component_flags_mask_(0) {}

AtomHDLR::AtomHDLR()
    : FullAtom(0, 0, kType),
      component_type_(std::string(kTypeSize, '\0')),
      component_subtype_(std::string(kTypeSize, '\0')),
      component_manufacturer_(std::string(kTypeSize, '\0')),
      component_flags_(0),
      component_flags_mask_(0),
      component_name_(std::string(1, '\0')) {
  Update();
}

TrackMediaType AtomHDLR::GetTrackMediaType() const {
  if (component_subtype_ == kVisualSubtype) {
    return VISUAL_MEDIA_TYPE;
  } else if (component_subtype_ == kSoundSubtype) {
    return SOUND_MEDIA_TYPE;
  } else if (component_subtype_ == kTextSubtype) {
    return TEXT_MEDIA_TYPE;
  } else if (component_subtype_ == kSubtitleSubtype) {
    return SUBTITLE_MEDIA_TYPE;
  } else if (component_subtype_ == kBaseSubtype) {
    return BASE_MEDIA_TYPE;
  } else if (component_subtype_ == kClosedCaptionSubtype) {
    return CLOSED_CAPTION_MEDIA_TYPE;
  } else if (component_subtype_ == kHintSubtype) {
    return HINT_MEDIA_TYPE;
  } else if (component_subtype_ == kMPEGSubtype) {
    return MPEG_MEDIA_TYPE;
  } else if (component_subtype_ == kMuxedSubtype) {
    return MUXED_MEDIA_TYPE;
  } else if (component_subtype_ == kODSMSubtype) {
    return ODSM_MEDIA_TYPE;
  } else if (component_subtype_ == kSDSMSubtype) {
    return SDSM_MEDIA_TYPE;
  } else if (component_subtype_ == kQuartzComposerSubtype) {
    return QUARTZ_COMPOSER_MEDIA_TYPE;
  } else if (component_subtype_ == kSkinSubtype) {
    return SKIN_MEDIA_TYPE;
  } else if (component_subtype_ == kSpriteSubtype) {
    return SPRITE_MEDIA_TYPE;
  } else if (component_subtype_ == kStreamingSubtype) {
    return STREAMING_MEDIA_TYPE;
  } else if (component_subtype_ == kTimecodeSubtype) {
    return TIMECODE_MEDIA_TYPE;
  } else if (component_subtype_ == kTimedMetaSubtype) {
    return TIMED_METADATA_MEDIA_TYPE;
  } else if (component_subtype_ == kTweenSubtype) {
    return TWEEN_MEDIA_TYPE;
  } else if (component_subtype_ == kMetaSubtype) {
    return META_MEDIA_TYPE;
  }
  return UNKNOWN_MEDIA_TYPE;
}

void AtomHDLR::set_component_subtype(const std::string& subtype) {
  std::copy_n(subtype.begin(), kTypeSize, component_subtype_.begin());
}

FormatStatus AtomHDLR::WriteDataWithoutChildren(BinaryWriter* io) const {
  const int64_t initial_size = io->Tell();
  RETURN_IF_FORMAT_ERROR(VersionAndFlags(io));
  RETURN_IF_FORMAT_ERROR(io->PutString(CapSize(component_type_, kTypeSize)));
  RETURN_IF_FORMAT_ERROR(io->PutString(CapSize(component_subtype_, kTypeSize)));
  RETURN_IF_FORMAT_ERROR(
      io->PutString(CapSize(component_manufacturer_, kTypeSize)));
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(component_flags_));
  RETURN_IF_FORMAT_ERROR(io->PutUInt32(component_flags_mask_));

  const uint64_t max_size = data_size() - (io->Tell() - initial_size);
  return io->PutString(CapSize(component_name_, max_size));
}

FormatStatus AtomHDLR::ReadDataWithoutChildren(BinaryReader* io) {
  const int64_t initial_size = io->Tell();

  RETURN_IF_FORMAT_ERROR(ReadVersionAndFlags(io));
  RETURN_IF_FORMAT_ERROR(io->ReadString(&component_type_, kTypeSize));
  RETURN_IF_FORMAT_ERROR(io->ReadString(&component_subtype_, kTypeSize));
  RETURN_IF_FORMAT_ERROR(io->ReadString(&component_manufacturer_, kTypeSize));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&component_flags_));
  RETURN_IF_FORMAT_ERROR(io->ReadUInt32(&component_flags_mask_));
  return io->ReadString(&component_name_,
                        data_size() - (io->Tell() - initial_size));
}

Atom::AtomSize AtomHDLR::GetDataSizeWithoutChildren() const {
  uint32_t data_size = 12;
  data_size += component_type_.size();
  data_size += component_subtype_.size();
  data_size += component_manufacturer_.size();
  data_size += component_name_.size();
  return data_size;
}

REGISTER_ATOM(AtomHDLR, kType);

}  // namespace vr180
