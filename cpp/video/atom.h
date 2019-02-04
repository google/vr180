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

// This file defines an mp4 Atom abstract base class.
// Subclasses of this class are instantiated in AtomFactory.
//
// In order to do anything meaningful with this library, you need to know a
// little bit about the MP4 file format.
//
// Briefly:
// 1) An MP4 file consists of a tree of atoms.
// 2) Each MP4 atom consists of a header and payload.
//    * An atom header consists of a size and an atom_type:
//      size is the number of bytes in this atom including the header.
//      atom_type is a std::string tag that differentiates one kind of atom from
//      another.
//    * Payload is everything in an atom that isn't the header. If an atom
//      is known, it knows the difference between the atom-specific data,
//      and its children. Otherwise, it assumes everything is atom specific
//      data, and it does not have any chidren.
//
// See ISO/IEC 14496-12 (MPEG-4 Part 12 specification): goto/iso2012spec
//

#ifndef VR180_CPP_VIDEO_FORMAT_ATOM_H_
#define VR180_CPP_VIDEO_FORMAT_ATOM_H_

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"
#include "cpp/video/format_status.h"

namespace vr180 {

class Atom {
 public:
  // The MP4 spec provides a few ways of defining the size of mp4 objects:
  // atom objects may have 32bit or 64bit sizes, while descriptor size may
  // various from 8bits and up to 32bits.
  typedef uint64_t AtomSize;
  static const int kIndicateSizeIsToEndOfFile;
  static const int kIndicateSizeIs64;
  static const int kSizeOf32bitSize;
  static const int kSizeOf64bitSize;
  static const int kAtomTypeSize;
  static const int kUserTypeSize;
  static const int kMinSizeofAtomHeader;
  static const uint64_t kMaxSizeofAtomHeader;

  Atom(const AtomSize header_size, const AtomSize data_size,
       const std::string& atom_type);

  virtual ~Atom() {}

  // Returns the number of children.
  int NumChildren() const { return static_cast<int>(children_.size()); }

  // Adds a new child atom at the end of this atom.
  void AddChild(std::unique_ptr<Atom> child);

  // Adds a new child atom at the provided index.
  void AddChildAt(std::unique_ptr<Atom> child, int i);

  // Deletes the i-th child atom. Returns the deleted child.
  std::unique_ptr<Atom> DeleteChild(int i);

  // Returns a pointer to the i-th child atom.
  Atom* GetChild(int i) const { return children_[i].get(); }

  // Eg. "st3d", "proj", etc.
  const std::string& atom_type() const { return atom_type_; }

  // Size of the atom header in bytes.
  AtomSize header_size() const { return header_size_; }

  // Size of the atom payload in bytes, including the null
  // terminator if present.
  AtomSize data_size() const { return data_size_; }

  // Size of the atom in bytes.
  AtomSize size() const { return header_size() + data_size(); }

  // Some atoms may have 4 byte optional null terminator at the end.
  // Currently known atoms are UDTA and VisualSampleEntry.
  // For details see please:
  // QuickTime File Format Spec [07-13-2011] on page 132.
  bool has_null_terminator() const { return has_null_terminator_; }
  void set_has_null_terminator(bool value);

  // Interfaces for dealing with atom specific data, i.e. payload data excluding
  // children. The default implementations assume the atom does not carrie any
  // custom data, i.e. the atom will just contain its header and other atoms
  // as children.
  virtual FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const {
    return FormatStatus::OkStatus();
  }
  virtual FormatStatus ReadDataWithoutChildren(BinaryReader* input) {
    return FormatStatus::OkStatus();
  }
  virtual AtomSize GetDataSizeWithoutChildren() const { return 0; }

 protected:
  // Updates the atom's size as the sum of its header, payload, and children.
  void Update();

  // Sets a new atom type.
  void set_atom_type(const std::string& type) { atom_type_ = type; }

  // Sets new header size in bytes.
  void set_header_size(const AtomSize size) { header_size_ = size; }

  // Sets the payload size in bytes.
  void set_data_size(const AtomSize size) { data_size_ = size; }

  // Calculates the header size.
  void ComputeHeaderSize();

 private:
  // Computes the size of the current atom.
  void UpdateSize();

  void set_parent(Atom* parent) { parent_ = parent; }

  // Tag describing what kind of atom this is.
  std::string atom_type_;
  // Number of bytes required to store header.
  AtomSize header_size_;
  // Number of bytes required to store the payload.
  AtomSize data_size_;
  std::vector<std::unique_ptr<Atom>> children_;
  Atom* parent_;
  bool has_null_terminator_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOM_H_
