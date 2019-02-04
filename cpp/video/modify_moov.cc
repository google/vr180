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

#include "cpp/video/modify_moov.h"

#include <vector>
#include <glog/logging.h>
#include "cpp/video/atom.h"
#include "cpp/video/atom_helpers.h"
#include "cpp/video/atom_reader.h"
#include "cpp/video/atom_writer.h"
#include "cpp/video/atoms/atom_moov.h"
#include "cpp/video/atoms/atom_stco.h"
#include "cpp/video/atoms/atom_trak.h"
#include "cpp/video/binary_reader_impl.h"
#include "cpp/video/binary_writer_impl.h"
#include "cpp/video/format_status.h"

namespace vr180 {
namespace {
constexpr char kMoovType[] = "moov";
constexpr char kMdatType[] = "mdat";
constexpr char kFreeType[] = "free";
constexpr int64_t kFreeAtomHeaderSize = 8;

FormatStatus AdjustTrackOffsets(AtomMOOV* moov, const int64_t delta) {
  LOG(INFO) << "Adjusting STCO offsets by " << delta << " bytes";
  for (AtomTRAK* trak : moov->tracks()) {
    Atom* stbl = trak->atom_stbl();
    if (stbl == nullptr) {
      return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                                 "track does not contain stbl atom");
    }
    AtomSTCO* stco = FindChild<AtomSTCO>(*stbl);
    if (stco == nullptr) {
      return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                                 "track does not contain stco atom");
    }
    stco->AdjustChunkOffsets(delta);
  }

  return FormatStatus::OkStatus();
}

std::vector<std::unique_ptr<Atom>> ReadAtoms(BinaryReader* input) {
  std::vector<std::unique_ptr<Atom>> atoms;
  while (input->Tell() < input->Size()) {
    std::unique_ptr<Atom> top_level_atom = ReadAtom(input);
    if (top_level_atom == nullptr) break;
    atoms.push_back(std::move(top_level_atom));
  }
  return atoms;
}

uint64_t GetAtomPosition(
    const std::vector<std::unique_ptr<Atom>>& top_level_atoms,
    const std::string& type) {
  uint64_t position = 0;
  for (const auto& top_level_atom : top_level_atoms) {
    if (top_level_atom->atom_type() == type) return position;
    position += top_level_atom->size();
  }
  return position;
}

int GetAtomIndex(const std::vector<std::unique_ptr<Atom>>& top_level_atoms,
                 const std::string& type) {
  for (int i = 0; i < top_level_atoms.size(); ++i) {
    if (top_level_atoms[i]->atom_type() == type) return i;
  }
  return -1;
}

// Write a free space of the given size at the current position
FormatStatus WriteFreeSpace(const uint32_t size, BinaryWriter* output) {
  RETURN_IF_FORMAT_ERROR(output->PutUInt32(size));
  return output->PutString(kFreeType);
}

// Copy the specified atoms to memory then write to the output.
FormatStatus WriteAtomsInPlace(
    const std::vector<std::unique_ptr<Atom>>& input_atoms,
    const int input_start, const int input_end, const uint64_t output_position,
    BinaryWriter* output) {
  MemoryBinaryWriter memory_output;
  for (int i = input_start; i < input_end; ++i) {
    CHECK_NE(input_atoms[i]->atom_type(), kMdatType) << "Should not copy mdat";
    RETURN_IF_FORMAT_ERROR(WriteAtom(*input_atoms[i], &memory_output));
  }
  const std::string content = memory_output.GetContents();
  MemoryBinaryReader memory_input(content);
  const auto& memory_atoms = ReadAtoms(&memory_input);
  if (memory_atoms.size() != input_end - input_start) {
    return FormatStatus::Error(FormatErrorCode::UNEXPECTED_ERROR,
                               "Invalid number of atoms to write");
  }
  RETURN_IF_FORMAT_ERROR(output->Seek(output_position));
  for (int i = 0; i < input_end - input_start; ++i) {
    RETURN_IF_FORMAT_ERROR(WriteAtom(*memory_atoms[i], output));
  }
  return FormatStatus::OkStatus();
}

FormatStatus ModifyMoov(const MoovModifier& modifier, BinaryReader* input,
                        BinaryWriter* output) {
  std::vector<std::unique_ptr<Atom>> top_level_atoms = ReadAtoms(input);
  const int moov_index = GetAtomIndex(top_level_atoms, kMoovType);
  const int mdat_index = GetAtomIndex(top_level_atoms, kMdatType);
  if (moov_index == -1 || mdat_index == -1) {
    return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                               "Invalid video file");
  }
  const uint64_t mdat_position_before_update =
      GetAtomPosition(top_level_atoms, kMdatType);

  AtomMOOV* moov = static_cast<AtomMOOV*>(top_level_atoms[moov_index].get());
  RETURN_IF_FORMAT_ERROR(modifier(moov));

  if (moov_index > mdat_index) {
    std::swap(top_level_atoms[moov_index], top_level_atoms[mdat_index]);
  }
  const uint64_t mdat_position_after_update =
      GetAtomPosition(top_level_atoms, kMdatType);
  const int64_t delta =
      mdat_position_after_update - mdat_position_before_update;

  if (delta != 0) {
    RETURN_IF_FORMAT_ERROR(AdjustTrackOffsets(moov, delta));
  }

  // Output all the atoms.
  for (const auto& top_level_atom : top_level_atoms) {
    RETURN_IF_FORMAT_ERROR(WriteAtom(*top_level_atom, output));
  }
  return FormatStatus::OkStatus();
}

FormatStatus ModifyMoovInPlace(const MoovModifier& modifier,
                               BinaryReader* input, BinaryWriter* output) {
  std::vector<std::unique_ptr<Atom>> top_level_atoms = ReadAtoms(input);
  const int moov_index = GetAtomIndex(top_level_atoms, kMoovType);
  const int mdat_index = GetAtomIndex(top_level_atoms, kMdatType);
  if (moov_index == -1 || mdat_index == -1) {
    return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                               "Invalid video file");
  }

  // Add up all the atom sizes.
  const uint64_t size_before_update = input->Size();
  // Position of the moov atom.
  const uint64_t moov_position_before_update =
      GetAtomPosition(top_level_atoms, kMoovType);

  /// Modify the moov box.
  AtomMOOV* moov = static_cast<AtomMOOV*>(top_level_atoms[moov_index].get());
  const uint64_t moov_size_before_update = moov->size();
  RETURN_IF_FORMAT_ERROR(modifier(moov));
  const uint64_t moov_size_after_update = moov->size();
  const int64_t delta = moov_size_after_update - moov_size_before_update;
  if (moov_index > mdat_index) {
    LOG(INFO) << "Update moov and following boxes in place after mdat";
    RETURN_IF_FORMAT_ERROR(
        WriteAtomsInPlace(top_level_atoms, moov_index, top_level_atoms.size(),
                          moov_position_before_update, output));
    if (delta < 0) {
      // If MOOV is now smaller, we mark the remaining part as free.
      LOG(INFO) << "Moov size is " << -delta << " bytes smaller";
      RETURN_IF_FORMAT_ERROR(
          WriteFreeSpace(std::max(-delta, kFreeAtomHeaderSize), output));
    }
    return FormatStatus::OkStatus();
  }

  const std::unique_ptr<Atom>& next = top_level_atoms[moov_index + 1];
  const int64_t free_space_after_moov =
      next->atom_type() == kFreeType ? next->size() : 0;

  if (delta == free_space_after_moov) {
    // No size change. Just need to update the moov box.
    LOG(INFO) << "Update moov in place";
    return WriteAtomsInPlace(top_level_atoms, moov_index, moov_index + 1,
                             moov_position_before_update, output);
  }

  if (delta + kFreeAtomHeaderSize <= free_space_after_moov) {
    // There is enough space for moov and a free box.
    LOG(INFO) << "Update moov and add free";
    RETURN_IF_FORMAT_ERROR(
        WriteAtomsInPlace(top_level_atoms, moov_index, moov_index + 1,
                          moov_position_before_update, output));
    return WriteFreeSpace(free_space_after_moov - delta, output);
  }

  // Overwrite moov by a free box and move MOOV to the end of file.
  LOG(INFO) << "Move moov to the end:  " << size_before_update;
  RETURN_IF_FORMAT_ERROR(WriteAtomsInPlace(
      top_level_atoms, moov_index, moov_index + 1, size_before_update, output));
  RETURN_IF_FORMAT_ERROR(output->Seek(moov_position_before_update));
  return WriteFreeSpace(moov_size_before_update, output);
}

template <class StringType>
FormatStatus ModifyMoovImpl(const MoovModifier& modifier,
                            const StringType& input_url,
                            const StringType& output_url) {
  if (modifier == nullptr || input_url.empty() || output_url.empty()) {
    return FormatStatus::Error(
        FormatErrorCode::UNEXPECTED_ERROR,
        "Must provide non-null modifier and non-empty input and output urls.");
  }
  if (input_url != output_url) {
    FileBinaryReader reader(input_url);
    FileBinaryWriter writer(output_url);
    return ModifyMoov(modifier, &reader, &writer);
  } else {
    std::shared_ptr<std::fstream> inout(new std::fstream(
        input_url,
        std::fstream::in | std::fstream::out | std::fstream::binary));
    BinaryWriterImpl writer(inout);
    BinaryReaderImpl reader(inout);
    return ModifyMoovInPlace(modifier, &reader, &writer);
  }
}

}  // namespace

FormatStatus ModifyMoov(const MoovModifier& modifier, const std::string& input_url,
                        const std::string& output_url) {
  return ModifyMoovImpl(modifier, input_url, output_url);
}

#ifdef _WIN32
FormatStatus ModifyMoov(const MoovModifier& modifier,
                        const std::wstring& input_url,
                        const std::wstring& output_url) {
  return ModifyMoovImpl(modifier, input_url, output_url);
}
#endif

}  // namespace vr180
