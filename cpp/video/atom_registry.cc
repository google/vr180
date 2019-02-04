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

#include "cpp/video/atom_registry.h"

#include <mutex>
#include "cpp/video/format_status.h"

namespace vr180 {

ABSL_CONST_INIT std::mutex AtomRegistry::mutex_;
std::unordered_map<std::string, AtomRegistry::AtomConstructor>*
    AtomRegistry::atom_map_;

namespace {
class AtomDefault : public Atom {
 public:
  AtomDefault(Atom::AtomSize header_size, Atom::AtomSize data_size,
              const std::string& atom_type)
      : Atom(header_size, data_size, atom_type) {}
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override {
    if (payload_reader_ != nullptr) {
      return io->PutData(payload_reader_.get(), data_size());
    }
    return FormatStatus::OkStatus();
  }
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override {
    payload_reader_ = io->Clone();
    return io->Seek(io->Tell() + data_size());
  }
  AtomSize GetDataSizeWithoutChildren() const override { return data_size(); }

 private:
  std::unique_ptr<BinaryReader> payload_reader_;
};
}  // namespace

std::unique_ptr<Atom> AtomRegistry::CreateAtom(Atom::AtomSize header_size,
                                               Atom::AtomSize data_size,
                                               const std::string& atom_type) {
  std::lock_guard<std::mutex> lock(mutex_);
  if (atom_map_ != nullptr) {
    auto creator = atom_map_->find(atom_type);
    if (creator != atom_map_->end()) {
      return creator->second(header_size, data_size, atom_type);
    }
  }
  return absl::make_unique<AtomDefault>(header_size, data_size, atom_type);
}

}  // namespace vr180
