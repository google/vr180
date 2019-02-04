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

#ifndef VR180_CPP_VIDEO_FORMAT_BINARY_WRITER_H_
#define VR180_CPP_VIDEO_FORMAT_BINARY_WRITER_H_

#include <string>

#include <cstdint>
#include "cpp/video/binary_reader.h"
#include "cpp/video/format_status.h"

namespace vr180 {

// An interface to write network bits to a stream.
class BinaryWriter {
 public:
  virtual ~BinaryWriter() {}

  // Returns the current position of the stream.
  virtual uint64_t Tell() const = 0;

  // Seeks to the given position in the stream in bytes.
  virtual FormatStatus Seek(uint64_t pos) = 0;

  // Write methods for different types.
  virtual FormatStatus PutUInt8(uint8_t value) = 0;
  virtual FormatStatus PutUInt16(uint16_t value) = 0;
  virtual FormatStatus PutUInt24(uint32_t value) = 0;
  virtual FormatStatus PutUInt32(uint32_t value) = 0;
  virtual FormatStatus PutUInt64(uint64_t value) = 0;
  virtual FormatStatus PutString(const std::string& value) = 0;
  virtual FormatStatus PutData(BinaryReader* reader, uint64_t size) = 0;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_BINARY_WRITER_H_
