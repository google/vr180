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

#ifndef VR180_CPP_VIDEO_MODIFY_MOOV_H_
#define VR180_CPP_VIDEO_MODIFY_MOOV_H_

#include <functional>

#include "cpp/video/atoms/atom_moov.h"
#include "cpp/video/format_status.h"

namespace vr180 {

// A function that modifies a MOOV atom in place, for example,
// inject sdtp, inject spherical V2, or both.
typedef std::function<FormatStatus(AtomMOOV* moov)> MoovModifier;

// Modify the MOOV box given an input and output. This function returns OK if
// all the modification was successful.
//
// When input_url == output_url,
//     The file will be updated in place and mdat will not be moved. If there
//     are enough space to update moov without moving, moov will stay were it
//     was, otherwise it may be moved to the end.
// When input_url != output_url
//     The moov box will be modified and also always placed before mdat.
FormatStatus ModifyMoov(const MoovModifier& modifier, const std::string& input_url,
                        const std::string& output_url);

#ifdef _WIN32
FormatStatus ModifyMoov(const MoovModifier& modifier,
                        const std::wstring& input_url,
                        const std::wstring& output_url);
#endif

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_MODIFY_MOOV_H_
