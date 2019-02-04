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

#ifndef VR180_CPP_PHOTO_JPEG_ENCODER_H_
#define VR180_CPP_PHOTO_JPEG_ENCODER_H_

#include <cstdint>
#include <string>

extern "C" {
#include "libjpeg_turbo/jpeglib.h"
}

namespace vr180 {
// Encodes the subimage of data specified by x,y,width,height,stride into
// result.
bool EncodeJpeg(const uint8_t* data, int x, int y, int width, int height,
                int stride, int quality, J_COLOR_SPACE color_space,
                std::string* result);

// A wrapper to encoder RGBA jpegs.
bool EncodeRGBAJpeg(const uint8_t* data, int x, int y, int width, int height,
                    int stride, int quality, std::string* result);

}  // namespace vr180

#endif  // VR180_CPP_PHOTO_JPEG_ENCODER_H_
