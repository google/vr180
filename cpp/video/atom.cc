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

#include "cpp/video/atom.h"

#include <algorithm>
#include <memory>
#include <string>
#include <utility>

#include <glog/logging.h>

namespace vr180 {

const int Atom::kIndicateSizeIsToEndOfFile = 0;
const int Atom::kIndicateSizeIs64 = 1;
const int Atom::kSizeOf32bitSize = 4;
const int Atom::kSizeOf64bitSize = kSizeOf32bitSize + sizeof(AtomSize);
const int Atom::kAtomTypeSize = 4;
const int Atom::kUserTypeSize = 16;

const int Atom::kMinSizeofAtomHeader = kSizeOf32bitSize + kAtomTypeSize;
const uint64_t Atom::kMaxSizeofAtomHeader =
    kSizeOf64bitSize + kAtomTypeSize + kUserTypeSize;

Atom::Atom(const AtomSize header_size, const AtomSize data_size,
           const std::string& atom_type)
    : atom_type_(atom_type),
      header_size_(header_size),
      data_size_(data_size),
      parent_(nullptr),
      has_null_terminator_(false) {
  if (header_size == 0 && data_size != 0) {
    ComputeHeaderSize();
  }
}

void Atom::Update() {
  UpdateSize();

  // Update the size and named descendants of the parent atom if it exists.
  if (parent_ != nullptr) {
    parent_->Update();
  }
}

void Atom::AddChild(std::unique_ptr<Atom> child) {
  AddChildAt(std::move(child), NumChildren());
}

void Atom::AddChildAt(std::unique_ptr<Atom> child, int index) {
  int size = NumChildren();
  if (index < 0 || index > size) {
    LOG(ERROR) << "Index out of bounds: " << index << ", size: " << size;
    return;
  }
  child->set_parent(this);
  for (int i = index; i < size; i++) {
    children_[i].swap(child);
  }
  children_.push_back(std::move(child));
  Update();
}

std::unique_ptr<Atom> Atom::DeleteChild(int i) {
  if (i < 0 || i >= NumChildren()) {
    LOG(ERROR) << "Index out of bounds: " << i << ", size: " << NumChildren();
    return nullptr;
  }
  std::unique_ptr<Atom> child;
  child.swap(children_[i]);
  children_.erase(children_.begin() + i);
  Update();
  return child;
}

void Atom::ComputeHeaderSize() {
  // Calculate header_size.
  AtomSize header_bytes = kSizeOf32bitSize + kAtomTypeSize;
  if (header_bytes + data_size() > UINT32_MAX) {
    header_bytes += sizeof(data_size());
  }
  set_header_size(header_bytes);
}

void Atom::UpdateSize() {
  // Calculate data_size.
  AtomSize children_size = 0;
  for (int i = 0; i < children_.size(); ++i) {
    children_size += children_[i]->size();
  }
  const AtomSize terminator_size = has_null_terminator_ ? 4 : 0;
  set_data_size(GetDataSizeWithoutChildren() + children_size + terminator_size);

  // Calculate header_size.
  ComputeHeaderSize();
}

void Atom::set_has_null_terminator(bool value) {
  if (has_null_terminator_ != value) {
    has_null_terminator_ = value;
    Update();
  }
}

}  // namespace vr180
