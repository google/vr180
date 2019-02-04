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

#include "cpp/video/spherical_inject.h"
#include <memory>

#include <glog/logging.h>
#include "absl/memory/memory.h"
#include "absl/strings/str_format.h"
#include "cpp/video/atom.h"
#include "cpp/video/atom_helpers.h"
#include "cpp/video/atom_reader.h"
#include "cpp/video/atoms/atom_moov.h"
#include "cpp/video/atoms/atom_st3d.h"
#include "cpp/video/atoms/atom_sv3d.h"
#include "cpp/video/atoms/atom_trak.h"
#include "cpp/video/atoms/atom_uuid.h"
#include "cpp/video/binary_reader_impl.h"
#include "cpp/video/stereo_mode.h"

namespace vr180 {
namespace {

constexpr char kSphericalV1UiidId[] =
    "\xff\xcc\x82\x63\xf8\x55\x4a\x93\x88\x14\x58\x7a\x02\x52\x1f\xdd";

// Printf arg reference:
//   %s: StitcherSoftware
//   %s: StereoMode
//   %d: CroppedAreaLeftPixels
//   %d: CroppredAreaTopPixels
//   %d: CroppedAreaImageWidthPixels
//   %d: CroppedAreaImageHeight
//   %d: FullPanoWidthPixels
//   %d: FullPanoHeightPixels
constexpr char kSphericalV1CroppedEquirectXml[] = R"END(
<rdf:SphericalVideo xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                    xmlns:GSpherical="http://ns.google.com/videos/1.0/spherical/">
  <GSpherical:Spherical>true</GSpherical:Spherical>
  <GSpherical:Stitched>true</GSpherical:Stitched>
  <GSpherical:ProjectionType>equirectangular</GSpherical:ProjectionType>
  <GSpherical:StitchingSoftware>%s</GSpherical:StitchingSoftware>
  <GSpherical:SourceCount>2</GSpherical:SourceCount>
  <GSpherical:StereoMode>%s</GSpherical:StereoMode>
  <GSpherical:CroppedAreaLeftPixels>%d</GSpherical:CroppedAreaLeftPixels>
  <GSpherical:CroppedAreaTopPixels>%d</GSpherical:CroppedAreaTopPixels>
  <GSpherical:CroppedAreaImageWidthPixels>%d</GSpherical:CroppedAreaImageWidthPixels>
  <GSpherical:CroppedAreaImageHeightPixels>%d</GSpherical:CroppedAreaImageHeightPixels>
  <GSpherical:FullPanoWidthPixels>%.0f</GSpherical:FullPanoWidthPixels>
  <GSpherical:FullPanoHeightPixels>%.0f</GSpherical:FullPanoHeightPixels>
</rdf:SphericalVideo>
)END";

FormatStatus GetStereoModeAsString(StereoMode stereo_mode, std::string* mode) {
  switch (stereo_mode) {
    case StereoMode::LEFT_RIGHT:
      *mode = "left-right";
      return FormatStatus::OkStatus();
    case StereoMode::TOP_BOTTOM:
      *mode = "top-bottom";
      return FormatStatus::OkStatus();
    case StereoMode::MONO:
      *mode = "mono";
      return FormatStatus::OkStatus();
    default:
      return FormatStatus::Error(
          FormatErrorCode::UNEXPECTED_ERROR,
          absl::StrFormat("Invalid stereo mode: %d", stereo_mode));
  }
}

FormatStatus CreateUuidAtom(const std::string& stitcher, StereoMode stereo_mode,
                            int width, int height, double fov_x_in_degrees,
                            double fov_y_in_degrees, AtomUUID* uuid) {
  if (width <= 0 || height <= 0 || fov_x_in_degrees <= 0.0 ||
      fov_x_in_degrees > 360.0 || fov_y_in_degrees <= 0.0 ||
      fov_y_in_degrees > 180.0) {
    return FormatStatus::Error(FormatErrorCode::UNEXPECTED_ERROR,
                               "Invalid v1 metadata settings.");
  }

  std::string mode;
  RETURN_IF_FORMAT_ERROR(GetStereoModeAsString(stereo_mode, &mode));

  const double full_width = width * 360.0 / fov_x_in_degrees;
  const double full_height = height * 180.0 / fov_y_in_degrees;
  const int crop_left = (full_width - width) / 2.0;
  const int crop_top = (full_height - height) / 2.0;
  std::string payload = absl::StrFormat(
      kSphericalV1CroppedEquirectXml, stitcher.c_str(), mode.c_str(), crop_left,
      crop_top, width, height, std::round(full_width), std::round(full_height));
  uuid->set_uuid(kSphericalV1UiidId);
  uuid->set_value(payload);
  return FormatStatus::OkStatus();
}

}  // namespace

FormatStatus InjectSphericalMetadataToMoov(
    StereoMode stereo_mode, std::unique_ptr<AtomSV3D> sv3d,
    std::unique_ptr<AtomUUID> v1_metadata, AtomMOOV* moov) {
  // Get video track.
  AtomTRAK* video_trak = moov->GetFirstVideoTrack();
  if (video_trak == nullptr) {
    return FormatStatus::Error(
        FormatErrorCode::FILE_FORMAT_ERROR,
        "File has no video track during spherical injection");
  }

  // Insert Spherical V1 Metadata.
  if (v1_metadata != nullptr) {
    DeleteChildren<AtomUUID>(video_trak);
    video_trak->AddChild(std::move(v1_metadata));
  }

  // Insert Spherical V2 Metadata.
  if (sv3d != nullptr) {
    // Find the visual sample entry within the video trak.
    VisualSampleEntry* visual_sample_entry = video_trak->visual_sample_entry();
    if (visual_sample_entry == nullptr) {
      return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                                 "Track has no visual sample entry");
    }

    std::unique_ptr<AtomST3D> st3d(new AtomST3D());
    st3d->set_stereo_mode(stereo_mode);
    DeleteChildren<AtomST3D>(visual_sample_entry);
    DeleteChildren<AtomSV3D>(visual_sample_entry);
    visual_sample_entry->AddChild(std::move(st3d));
    visual_sample_entry->AddChild(std::move(sv3d));
  }
  return FormatStatus::OkStatus();
}

FormatStatus InjectProjectionMetadataToMoov(const StereoMode stereo_mode,
                                            const std::string& serialized_sv3d,
                                            AtomMOOV* moov) {
  MemoryBinaryReader reader(serialized_sv3d);
  std::unique_ptr<Atom> sv3d = ReadAtom(&reader);
  if (sv3d == nullptr) {
    return FormatStatus::Error(FormatErrorCode::FILE_FORMAT_ERROR,
                               "Cannot parse the Sv3d Atom");
  }
  return InjectSphericalMetadataToMoov(
      stereo_mode,
      std::unique_ptr<AtomSV3D>(
          dynamic_cast<AtomSV3D*>(std::move(sv3d).release())),
      nullptr, moov);
}

FormatStatus InjectSphericalV1MetadataToMoov(
    const std::string& stitcher, const StereoMode stereo_mode, int width, int height,
    double fov_x_in_degrees, double fov_y_in_degrees, AtomMOOV* moov) {
  std::unique_ptr<AtomUUID> uuid = absl::make_unique<AtomUUID>();
  RETURN_IF_FORMAT_ERROR(CreateUuidAtom(stitcher, stereo_mode, width, height,
                                        fov_x_in_degrees, fov_y_in_degrees,
                                        uuid.get()));
  return InjectSphericalMetadataToMoov(stereo_mode, nullptr, std::move(uuid),
                                       moov);
}

}  // namespace vr180
