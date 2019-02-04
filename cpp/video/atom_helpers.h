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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOM_HELPERS_H_
#define VR180_CPP_VIDEO_FORMAT_ATOM_HELPERS_H_

#include "cpp/video/atom.h"

namespace vr180 {

// Returns the first child of the given class, or nullptr if there is none.
template <class ATOM>
ATOM* FindChild(const Atom& parent) {
  for (int i = 0; i < parent.NumChildren(); ++i) {
    Atom* child = parent.GetChild(i);
    if (typeid(ATOM) == typeid(*child)) {
      return reinterpret_cast<ATOM*>(child);
    }
  }
  return nullptr;
}

// Returns all the children of the given type.
template <class ATOM>
void FindChildren(const Atom& parent, std::vector<ATOM*>* children) {
  for (int i = 0; i < parent.NumChildren(); ++i) {
    Atom* child = parent.GetChild(i);
    if (typeid(ATOM) == typeid(*child)) {
      children->push_back(reinterpret_cast<ATOM*>(child));
    }
  }
}

// Deletes all the children of the given type.
template <class ATOM>
void DeleteChildren(Atom* parent) {
  for (int i = parent->NumChildren() - 1; i >= 0; --i) {
    Atom* child = parent->GetChild(i);
    if (typeid(ATOM) == typeid(*child)) {
      parent->DeleteChild(i);
    }
  }
}

// Returns the index of the given child in the parent. Returns -1 on failure.
inline int FindIndex(const Atom& parent, Atom* child) {
  for (int i = 0; i < parent.NumChildren(); i++) {
    if (parent.GetChild(i) == child) {
      return i;
    }
  }
  return -1;
}

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOM_HELPERS_H_
