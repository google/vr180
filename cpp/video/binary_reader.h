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

#ifndef VR180_CPP_VIDEO_FORMAT_BINARY_READER_H_
#define VR180_CPP_VIDEO_FORMAT_BINARY_READER_H_

#include <cstdint>
#include <memory>
#include <string>
#include "cpp/video/format_status.h"

namespace vr180 {

// An interface to read network bits from a stream.
// Instances returned by Clone() are not mutually thread-safe.
class BinaryReader {
 public:
  virtual ~BinaryReader() {}

  // Returns the size of the stream in bytes.
  virtual uint64_t Size() = 0;
  // Returns the current position in the stream in bytes.
  virtual uint64_t Tell() const = 0;
  // Seeks to the given position in the stream in bytes.
  virtual FormatStatus Seek(uint64_t pos) = 0;

  // Clones the reader
  virtual std::unique_ptr<BinaryReader> Clone() = 0;

  // Read methods for different types.
  virtual FormatStatus ReadUInt8(uint8_t* value) = 0;
  virtual FormatStatus ReadUInt16(uint16_t* value) = 0;
  // Reads into the first three bytes of value.
  virtual FormatStatus ReadUInt24(uint32_t* value) = 0;
  virtual FormatStatus ReadUInt32(uint32_t* value) = 0;
  virtual FormatStatus ReadUInt64(uint64_t* value) = 0;
  virtual FormatStatus ReadString(std::string* value, uint64_t size) = 0;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_BINARY_READER_H_
