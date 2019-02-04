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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_COMMON_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_COMMON_H_

namespace vr180 {

// Track type in MP4 is specified by trak.mdia.hdlr.component_subtype() value.
// component_sybtype is a 4 byte std::string. For convenience TrackMediaType enum
// is introduced.
enum TrackMediaType {
  UNKNOWN_MEDIA_TYPE,
  VISUAL_MEDIA_TYPE,
  SOUND_MEDIA_TYPE,
  TEXT_MEDIA_TYPE,
  SUBTITLE_MEDIA_TYPE,
  BASE_MEDIA_TYPE,
  CLOSED_CAPTION_MEDIA_TYPE,
  HINT_MEDIA_TYPE,
  MPEG_MEDIA_TYPE,
  MUXED_MEDIA_TYPE,
  ODSM_MEDIA_TYPE,
  SDSM_MEDIA_TYPE,
  QUARTZ_COMPOSER_MEDIA_TYPE,
  SKIN_MEDIA_TYPE,
  SPRITE_MEDIA_TYPE,
  STREAMING_MEDIA_TYPE,
  TIMECODE_MEDIA_TYPE,
  TIMED_METADATA_MEDIA_TYPE,
  TWEEN_MEDIA_TYPE,
  META_MEDIA_TYPE,
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_COMMON_H_
