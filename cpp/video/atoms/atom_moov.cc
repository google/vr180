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

#include "cpp/video/atoms/atom_moov.h"

#include "cpp/video/atom_helpers.h"
#include "cpp/video/atom_registry.h"

#include <algorithm>
#include <string>
#include <vector>

#include <glog/logging.h>

namespace vr180 {
const char kType[] = "moov";

AtomMOOV::AtomMOOV(const AtomSize header_size, const AtomSize data_size,
                   const std::string& atom_type)
    : Atom(header_size, data_size, atom_type) {}

AtomMOOV::AtomMOOV() : AtomMOOV(0, 0, kType) { Update(); }

AtomTRAK* AtomMOOV::GetFirstVideoTrack() const {
  for (auto track : tracks()) {
    if (track->track_type() == VISUAL_MEDIA_TYPE) {
      return track;
    }
  }
  return nullptr;
}

std::vector<AtomTRAK*> AtomMOOV::tracks() const {
  std::vector<AtomTRAK*> tracks;
  FindChildren(*this, &tracks);
  return tracks;
}

REGISTER_ATOM(AtomTRAK, kType);

}  // namespace vr180
