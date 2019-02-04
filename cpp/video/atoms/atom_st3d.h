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

#ifndef VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_ST3D_H_
#define VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_ST3D_H_

#include <cstdint>
#include <string>

#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"
#include "cpp/video/full_atom.h"
#include "cpp/video/stereo_mode.h"

namespace vr180 {

// Defines a custom atom (ST3D) for specification of stereoscopic
// rendering mode.
//
// https://github.com/google/spatial-media/blob/master/docs/
//     spherical-video-v2-rfc.md
//
// Container: visual sample entry box.
class AtomST3D : public FullAtom {
 public:
  AtomST3D();
  AtomST3D(Atom::AtomSize header_size, Atom::AtomSize data_size,
           const std::string& atom_type);

  const StereoMode stereo_mode() const { return stereo_mode_; }

  void set_stereo_mode(StereoMode stereo_mode) { stereo_mode_ = stereo_mode; }

 private:
  FormatStatus WriteDataWithoutChildren(BinaryWriter* io) const override;
  FormatStatus ReadDataWithoutChildren(BinaryReader* io) override;
  Atom::AtomSize GetDataSizeWithoutChildren() const override;

  StereoMode stereo_mode_;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_ATOMS_ATOM_ST3D_H_
