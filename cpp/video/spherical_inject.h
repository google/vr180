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

#ifndef VR180_CPP_VIDEO_SPHERICAL_INJECT_H_
#define VR180_CPP_VIDEO_SPHERICAL_INJECT_H_

#include "cpp/video/atoms/atom_moov.h"
#include "cpp/video/atoms/atom_sv3d.h"
#include "cpp/video/atoms/atom_uuid.h"
#include "cpp/video/format_status.h"
#include "cpp/video/stereo_mode.h"

namespace vr180 {

// Function for injecting Spherical V1 and Spherical V2 metadata to a MOOV atom.
FormatStatus InjectSphericalMetadataToMoov(
    StereoMode stereo_mode, std::unique_ptr<AtomSV3D> sv3d,
    std::unique_ptr<AtomUUID> v1_metadata, AtomMOOV* moov);

// Function for injecting Spherical V2 Metdata with a serialized sv3d box
// to MOOV atom.
FormatStatus InjectProjectionMetadataToMoov(const StereoMode stereo_mode,
                                            const std::string& serialized_sv3d,
                                            AtomMOOV* moov);

// Function for injecting Spherical V1 Metadata to MOOV atom.
FormatStatus InjectSphericalV1MetadataToMoov(
    const std::string& stitcher, const StereoMode stereo_mode, int width, int height,
    double fov_x_in_degrees, double fov_y_in_degrees, AtomMOOV* moov);

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_SPHERICAL_INJECT_H_
