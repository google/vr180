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

#include "cpp/video/atom_reader.h"

#include <memory>

#include <glog/logging.h>
#include "absl/strings/str_format.h"
#include "cpp/video/atom_registry.h"
#include "cpp/video/format_status.h"

namespace vr180 {

namespace {

// Helper functions to deal with big integers.
inline uint64_t safe_add(const uint64_t a, const uint64_t b) {
  const uint64_t sum = a + b;
  return sum < a ? UINT64_MAX : sum;
}

inline uint64_t safe_sub(const uint64_t a, const uint64_t b) {
  return a < b ? 0 : a - b;
}

inline int64_t safe_convert_uint64_t_to_int64_t(const uint64_t a) {
  return a > INT64_MAX ? INT64_MAX : a;
}

bool ReadChildAtoms(BinaryReader* input, Atom* parent) {
  // NOTE: these two variables intentionally have unsigned type, since the
  // atom size in the mp4 spec is uint64_t.
  uint64_t sum_sizes = 0;
  const uint64_t children_size =
      safe_sub(parent->data_size(), parent->GetDataSizeWithoutChildren());

  bool success = true;
  while (success && sum_sizes + Atom::kMinSizeofAtomHeader <= children_size) {
    std::unique_ptr<Atom> child = ReadAtom(input);
    if (child == nullptr) return false;
    // Add atom to the tree if we parsed anything (even broken atom).
    sum_sizes += child->size();
    parent->AddChild(std::move(child));
  }

  return success;
}

FormatStatus ReadHeader(BinaryReader* input, Atom::AtomSize* header_size,
                        Atom::AtomSize* data_size, std::string* atom_type) {
  // The MP4 spec allows for size to be either 32 bits or 64 bits.
  // When the 32 bit version of size is 1, the correct, 64 bit version of
  // size is after the atom_type.
  uint32_t size32 = 0;
  Atom::AtomSize size64 = 0;
  int header_bytes = Atom::kSizeOf32bitSize + Atom::kAtomTypeSize;
  RETURN_IF_FORMAT_ERROR(input->ReadUInt32(&size32));
  size64 = size32;
  RETURN_IF_FORMAT_ERROR(input->ReadString(atom_type, Atom::kAtomTypeSize));
  if (size32 == Atom::kIndicateSizeIs64) {
    // Reads the 64 bit version of size.
    header_bytes += sizeof(size64);
    RETURN_IF_FORMAT_ERROR(input->ReadUInt64(&size64));
  } else if (size32 == Atom::kIndicateSizeIsToEndOfFile) {
    // TODO: what if it's live stream and Size returned -1?
    // We don't use atoms with zero size, so we can consider this case later.
    size64 = input->Size() - input->Tell() + header_bytes;
  }

  if (size64 < header_bytes) {
    return FormatStatus::Error(
        FormatErrorCode::FILE_FORMAT_ERROR,
        absl::StrFormat("Atom structure is broken: atom_size=%u"
                        " is less than header_size=%d",
                        size64, header_bytes));
  }

  // Setup result sizes.
  *header_size = header_bytes;
  *data_size = size64 - header_bytes;

  return FormatStatus::OkStatus();
}

}  // namespace

std::unique_ptr<Atom> ReadAtom(BinaryReader* input) {
  std::unique_ptr<Atom> atom;

  const int64_t initial_pos = input->Tell();
  Atom::AtomSize header_size = 0;
  Atom::AtomSize data_size = 0;
  std::string atom_type;

  FormatStatus status = ReadHeader(input, &header_size, &data_size, &atom_type);
  if (!status.ok()) {
    LOG(ERROR) << "Failed to read atom header: " << status.message();
    return nullptr;
  }

  const uint64_t atom_size = header_size + data_size;
  const int64_t expected_pos =
      safe_convert_uint64_t_to_int64_t(safe_add(initial_pos, atom_size));

  // Creates and initializes a new atom of the correct type.
  atom = AtomRegistry::CreateAtom(header_size, data_size, atom_type);
  status = atom->ReadDataWithoutChildren(input);
  if (!status.ok()) {
    LOG(ERROR) << "Failed to read atom [" << atom_type
               << "] payload: " << status.message();
    return nullptr;
  }
  if (!ReadChildAtoms(input, atom.get())) {
    LOG(ERROR) << "Failed to read child atoms for [" << atom_type << "]";
    return nullptr;
  }
  // Handle null terminators.
  if (atom_size - input->Tell() + initial_pos == 4) {
    LOG(INFO) << "Found null terminator for atom [" << atom_type << "]";
    atom->set_has_null_terminator(true);
    uint32_t terminator;
    status = input->ReadUInt32(&terminator);
    if (!status.ok()) {
      LOG(WARNING) << "Failed to read null terminator during atom read: "
                   << status.message();
    }
  }

  // Verify that atom structure is sane.
  const uint64_t consumed_num_bytes = input->Tell() - initial_pos;
  if (input->Tell() != expected_pos) {
    LOG(WARNING) << "Atom [" << atom->atom_type()
                 << "] structure is broken: ReadAtom consumed "
                 << consumed_num_bytes << " bytes, "
                 << "but expected to consume " << atom_size;
    return nullptr;
  }

  return atom;
}

}  // namespace vr180
