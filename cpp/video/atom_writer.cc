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

#include "cpp/video/atom_writer.h"

#include <memory>

#include <glog/logging.h>
#include "absl/strings/str_format.h"
#include "cpp/video/format_status.h"

namespace vr180 {
namespace {

FormatStatus WriteAtomHeader(const Atom& atom, BinaryWriter* output) {
  const Atom::AtomSize size_length = atom.header_size() - Atom::kAtomTypeSize;
  // Check how size was encoded.
  bool flag_size_large = false;
  if (size_length == Atom::kSizeOf32bitSize) {
    flag_size_large = false;
  } else if (size_length == Atom::kSizeOf32bitSize + sizeof(atom.data_size())) {
    flag_size_large = true;
  } else {
    return FormatStatus::Error(
        FormatErrorCode::FILE_FORMAT_ERROR,
        absl::StrFormat(
            "Incorrect header_size of atom (%s) writing atom header",
            atom.atom_type()));
  }

  const uint32_t size32 =
      flag_size_large ? Atom::kIndicateSizeIs64 : atom.size();
  const uint64_t size64 = atom.size();
  RETURN_IF_FORMAT_ERROR(output->PutUInt32(size32));
  RETURN_IF_FORMAT_ERROR(output->PutString(atom.atom_type()));
  if (flag_size_large) {
    RETURN_IF_FORMAT_ERROR(output->PutUInt64(size64));
  }

  return FormatStatus::OkStatus();
}

FormatStatus WriteChildAtoms(const Atom& atom, BinaryWriter* output) {
  for (int i = 0; i < atom.NumChildren(); ++i) {
    RETURN_IF_FORMAT_ERROR(WriteAtom(*atom.GetChild(i), output));
  }
  return FormatStatus::OkStatus();
}
}  // namespace

FormatStatus WriteAtom(const Atom& atom, BinaryWriter* output) {
  RETURN_IF_FORMAT_ERROR(WriteAtomHeader(atom, output));
  RETURN_IF_FORMAT_ERROR(atom.WriteDataWithoutChildren(output));
  RETURN_IF_FORMAT_ERROR(WriteChildAtoms(atom, output));
  if (atom.has_null_terminator()) {
    RETURN_IF_FORMAT_ERROR(output->PutUInt32(0));
  }

  return FormatStatus::OkStatus();
}

}  // namespace vr180
