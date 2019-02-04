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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_ELST_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_ELST_H_

#include <cstdint>
#include <string>
#include <vector>

#include "cpp/video/atom.h"
#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"
#include "cpp/video/full_atom.h"

namespace vr180 {

// This box contains an explicit timeline map. Each entry defines part
// of the track time-line: by mapping part of the media time-line, or by
// indicating ‘empty’ time, or by defining a ‘dwell’, where a single time-point
// in the media is held for a period.
//
// Container: Edit Box ('edts')
// Mandatory: No
// Quantity: Zero or one
//
// ISO/IEC 14496-12:2008 Section 8.6.6
class AtomELST : public FullAtom {
 public:
  struct Entry {
    explicit Entry(const uint64_t duration = 0, const int64_t time = 0,
                   const int16_t rate_integer = 1,
                   const int16_t rate_fraction = 0)
        : segment_duration(duration),
          media_time(time),
          media_rate_integer(rate_integer),
          media_rate_fraction(rate_fraction) {}
    static const uint32_t kEntryV0Size;
    static const uint32_t kEntryV1Size;
    uint64_t segment_duration;
    int64_t media_time;
    int16_t media_rate_integer;
    int16_t media_rate_fraction;
  };

  AtomELST(const AtomSize header_size, const AtomSize data_size,
           const std::string& atom_type);

  AtomELST();

  // The number of Entries in this atom
  uint32_t NumEntries() const { return entries_.size(); }

  // Returns the i-th Entry in the range 0..NumEntries()-1.
  const Entry& GetEntry(const uint32_t i) const { return entries_[i]; }
  void ReplaceEntry(uint32_t i, const Entry& entry) { entries_[i] = entry; }

  // Append Entry to the atom.
  void AddEntry(const Entry& entry);

 private:
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  AtomSize GetDataSizeWithoutChildren() const override;

  // AtomELST has variable size depending on Entries number
  // stored inside. Incorrect number of may cause memory allocation
  // faults and need to be checked before reading entries
  bool CheckNumEntries(const uint32_t num) const;

  // Entry structure size depends on atom version
  // this method calculates it
  AtomSize EntrySize() const;

  std::vector<Entry> entries_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_ELST_H_
