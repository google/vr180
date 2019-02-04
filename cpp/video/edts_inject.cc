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

#include "cpp/video/edts_inject.h"

#include "absl/memory/memory.h"
#include "cpp/video/atom_helpers.h"
#include "cpp/video/atoms/atom_edts.h"
#include "cpp/video/atoms/atom_elst.h"
#include "cpp/video/atoms/atom_mdia.h"
#include "cpp/video/atoms/atom_moov.h"
#include "cpp/video/atoms/atom_tkhd.h"
#include "cpp/video/atoms/atom_trak.h"
#include "cpp/video/modify_moov.h"

namespace vr180 {

FormatStatus InjectEdtsToMoov(AtomMOOV* moov) {
  for (AtomTRAK* trak : moov->tracks()) {
    AtomMDIA* mdia = FindChild<AtomMDIA>(*trak);
    if (mdia == nullptr) {
      return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                                 "trak has no mdia atom.");
    }

    // Get or create the edts atom.
    std::unique_ptr<Atom> edts;
    AtomEDTS* existing_edts = FindChild<AtomEDTS>(*trak);
    if (existing_edts != nullptr) {
      edts = trak->DeleteChild(FindIndex(*trak, existing_edts));
    } else {
      edts = absl::make_unique<AtomEDTS>();
      std::unique_ptr<AtomELST> elst = absl::make_unique<AtomELST>();
      AtomTKHD* tkhd = FindChild<AtomTKHD>(*trak);
      if (tkhd == nullptr) {
        return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                                   "trak has no tkhd atom.");
      }
      elst->AddEntry(AtomELST::Entry(tkhd->duration()));
      edts->AddChild(std::move(elst));
    }
    // Insert the edts atom before the mdia atom.
    trak->AddChildAt(std::move(edts), FindIndex(*trak, mdia));
  }
  return FormatStatus::OkStatus();
}

}  // namespace vr180
