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

#include "cpp/video/atoms/atom_trak.h"

#include <algorithm>
#include <string>
#include <vector>

#include <glog/logging.h>
#include "cpp/video/atom_helpers.h"
#include "cpp/video/atom_registry.h"
#include "cpp/video/atoms/atom_hdlr.h"
#include "cpp/video/atoms/atom_mdia.h"
#include "cpp/video/atoms/atom_minf.h"
#include "cpp/video/atoms/atom_stbl.h"
#include "cpp/video/atoms/atom_stsd.h"
#include "cpp/video/atoms/visual_sample_entry.h"

namespace vr180 {

const char kType[] = "trak";

AtomTRAK::AtomTRAK(const AtomSize header_size, const AtomSize data_size,
                   const std::string& atom_type)
    : Atom(header_size, data_size, atom_type),
      track_type_(UNKNOWN_MEDIA_TYPE),
      visual_sample_entry_(nullptr) {
  // Since these classes are only referenced as pointers, they will
  // be optimized out by the linker if they are not instantiated
  // somewhere.
  AtomMDIA dummy_mdia(0, 0, "dummy");
  AtomMINF dummy_minf(0, 0, "dummy");
  AtomSTBL dummy_stbl(0, 0, "dummy");
}

AtomTRAK::AtomTRAK() : AtomTRAK(0, 0, kType) { Update(); }

TrackMediaType AtomTRAK::track_type() {
  FindNamedChildren();
  return track_type_;
}

VisualSampleEntry* AtomTRAK::visual_sample_entry() {
  FindNamedChildren();
  return visual_sample_entry_;
}

AtomSTBL* AtomTRAK::atom_stbl() {
  FindNamedChildren();
  return stbl_;
}

void AtomTRAK::FindNamedChildren() {
  const AtomMDIA* mdia = FindChild<AtomMDIA>(*this);
  if (mdia == nullptr) {
    LOG(ERROR) << "Atom TRAK does not contain a MDIA child";
    return;
  }
  const AtomHDLR* hdlr = FindChild<AtomHDLR>(*mdia);
  if (hdlr != nullptr) {
    track_type_ = hdlr->GetTrackMediaType();
  }

  const AtomMINF* minf = FindChild<AtomMINF>(*mdia);
  if (minf == nullptr) {
    LOG(ERROR) << "Atom MDIA does not contain a MINF child";
    return;
  }
  stbl_ = FindChild<AtomSTBL>(*minf);
  if (stbl_ == nullptr) {
    LOG(ERROR) << "Atom STBL does not contain a STBL child";
    return;
  }

  const AtomSTSD* stsd = FindChild<AtomSTSD>(*stbl_);
  if (stsd == nullptr) {
    // STSD is not mandatory for all tracks.
    return;
  }

  visual_sample_entry_ = FindChild<VisualSampleEntry>(*stsd);
}

REGISTER_ATOM(AtomTRAK, kType);

}  // namespace vr180
