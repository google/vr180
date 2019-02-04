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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STCO_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STCO_H_

#include <string>

#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"
#include "cpp/video/full_atom.h"

namespace vr180 {

// The Sample Table Chunk Offset atom maps 1-based chunk numbers to file byte
// offsets. There must be exactly one STCO atom in each STBL atom.
//
// In order to find where the media data for a chunk is in the mdat, pass the
// 1-based chunk number to ChunkOffset().
//
// There are two ways to fill an instance of this class with chunk offsets. The
// first is to read this atom from an IOBuffer using ReadData (which calls the
// overridden protected Data() method). The second way is to create an empty
// AtomSTCO instance and successively call SetChunkOffset/AppendChunkOffset.
//
// ISO/IEC 14496-12 Section 8.19
class AtomSTCO : public FullAtom {
 public:
  AtomSTCO();

  AtomSTCO(const AtomSize header_size,
           const AtomSize data_size,
           const std::string& atom_type);

  // Adjusts all chunk offsets.
  void AdjustChunkOffsets(const int64_t adjustment);

 private:
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  Atom::AtomSize GetDataSizeWithoutChildren() const override;
  void ReviewAtomType();
  bool CheckNumberOfChunks(const uint32_t num) const;

  // Chunk byte offsets into mdat
  // relative to the beginning of the file.
  std::vector<AtomSize> chunk_offsets_;

  int64_t moov_size_delta_;

  // Max value stored in chunk_offsets_ vector.
  // Used for determining atom type (STCO or CO64)
  int64_t max_chunk_offset_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_STCO_H_
