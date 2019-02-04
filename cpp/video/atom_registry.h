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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOM_REGISTRY_H_
#define VR180_CPP_VIDEO_FORMAT_ATOM_REGISTRY_H_

#include <memory>
#include <string>
#include <unordered_map>

#include <glog/logging.h>
#include "absl/memory/memory.h"
#include <mutex>
#include "cpp/common/googleinit.h"
#include "cpp/video/atom.h"

namespace vr180 {
class AtomRegistry {
 public:
  // Creates an Atom given the header info. If the atom type corresponds to
  // a registered atom, it will be instantiated, otherwise a typeless Atom
  // will be returned.
  static std::unique_ptr<Atom> CreateAtom(Atom::AtomSize header_size,
                                          Atom::AtomSize data_size,
                                          const std::string& atom_type);

  // Registers an Atom with the registry.
  template <class ATOM>
  static void RegisterAtom(const std::string& atom_type);

 private:
  typedef std::function<std::unique_ptr<Atom>(Atom::AtomSize, Atom::AtomSize,
                                              std::string)>
      AtomConstructor;
  static std::mutex mutex_;
  static std::unordered_map<std::string, AtomConstructor>* atom_map_;

};

template <class ATOM>
void AtomRegistry::RegisterAtom(const std::string& atom_type) {
  std::lock_guard<std::mutex> lock(mutex_);
  if (atom_map_ == nullptr) {
    atom_map_ = new std::unordered_map<std::string, AtomConstructor>();
  }
  (*atom_map_)[atom_type] = [](Atom::AtomSize header_size,
                               Atom::AtomSize data_size,
                               const std::string& atom_type) {
    return absl::make_unique<ATOM>(header_size, data_size, atom_type);
  };
}

// Macro to register a new atom type.
#define REGISTER_ATOM(ATOM, name)   \
  REGISTER_MODULE_INITIALIZER(name, \
                              vr180::AtomRegistry::RegisterAtom<ATOM>(name))

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOM_REGISTRY_H_
