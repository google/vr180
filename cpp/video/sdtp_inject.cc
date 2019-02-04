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

#include "cpp/video/sdtp_inject.h"

#include <glog/logging.h>
#include "cpp/video/atom_helpers.h"
#include "cpp/video/atoms/atom_moov.h"
#include "cpp/video/atoms/atom_sdtp.h"
#include "cpp/video/atoms/atom_stss.h"
#include "cpp/video/atoms/atom_trak.h"
#include "cpp/video/modify_moov.h"

namespace vr180 {

FormatStatus InjectSdtpToMoov(AtomMOOV* moov) {
  // Get video track.
  AtomTRAK* video_trak = moov->GetFirstVideoTrack();
  if (video_trak == nullptr) {
    return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                               "File has no video track during SDTP injection");
  }

  AtomSTBL* stbl = video_trak->atom_stbl();
  AtomSDTP* prev_sdtp = FindChild<AtomSDTP>(*stbl);
  if (prev_sdtp == nullptr) {
    std::unique_ptr<AtomSDTP> sdtp(new AtomSDTP());
    const AtomSTSS* stss = FindChild<AtomSTSS>(*stbl);
    if (stss == nullptr) {
      return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                                 "File has no STSS box.");
    } else {
      sdtp->PopulateFromKeyFrameIndices(stss->KeyFrameIndices());
    }
    stbl->AddChild(std::move(sdtp));
  } else {
    LOG(ERROR) << "An SDTP box is already present.";
  }

  return FormatStatus::OkStatus();
}

FormatStatus InjectSdtpBox(const std::string& input_url, const std::string& output_url) {
  return ModifyMoov(InjectSdtpToMoov, input_url, output_url);
}

}  // namespace vr180
