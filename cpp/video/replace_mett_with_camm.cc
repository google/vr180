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

#include "cpp/video/replace_mett_with_camm.h"

#include <string>
#include "cpp/video/atom.h"
#include "cpp/video/atom_helpers.h"
#include "cpp/video/atoms/atom_camm.h"
#include "cpp/video/atoms/atom_stsd.h"
#include "cpp/video/atoms/atom_trak.h"
#include "cpp/video/atoms/common.h"

namespace vr180 {
namespace {

constexpr char kMettType[] = "mett";

AtomSTSD* FindStsdOfFirstMettTrack(AtomMOOV* moov) {
  for (auto* trak : moov->tracks()) {
    if (trak->track_type() != META_MEDIA_TYPE) continue;
    if (trak->atom_stbl() == nullptr) continue;
    return FindChild<AtomSTSD>(*trak->atom_stbl());
  }
  return nullptr;
}

}  // namespace

FormatStatus ReplaceMettWithCamm(AtomMOOV* moov) {
  AtomSTSD* stsd = FindStsdOfFirstMettTrack(moov);
  if (stsd == nullptr || stsd->NumChildren() != 1) {
    return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                               "File has no valid meta data track");
  }
  Atom* child = stsd->GetChild(0);
  if (child->atom_type() != kMettType) {
    return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                               "Metadata track is not mett");
  }
  stsd->DeleteChild(0);
  stsd->AddChild(std::unique_ptr<Atom>(new AtomCAMM()));
  return FormatStatus::OkStatus();
}

}  // namespace vr180
