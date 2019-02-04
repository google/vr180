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

#ifndef VR180_CPP_VIDEO_FORMAT_BINARY_WRITER_IMPL_H_
#define VR180_CPP_VIDEO_FORMAT_BINARY_WRITER_IMPL_H_

#include <cstdint>
#include <memory>
#include <ostream>
#include <string>

#include "cpp/video/binary_reader.h"
#include "cpp/video/binary_writer.h"

namespace vr180 {

// An implementation of BinaryWriter using ostream.
class BinaryWriterImpl : public BinaryWriter {
 public:
  explicit BinaryWriterImpl(std::shared_ptr<std::ostream> stream);

  uint64_t Tell() const override;

  // Seeks to the given position in the stream in bytes.
  FormatStatus Seek(uint64_t pos) override;

  // Write methods for different types.
  FormatStatus PutUInt8(uint8_t value) override;
  FormatStatus PutUInt16(uint16_t value) override;
  FormatStatus PutUInt24(uint32_t value) override;
  FormatStatus PutUInt32(uint32_t value) override;
  FormatStatus PutUInt64(uint64_t value) override;
  FormatStatus PutString(const std::string& value) override;
  FormatStatus PutData(BinaryReader* reader, uint64_t size) override;

 protected:
  std::shared_ptr<std::ostream> stream_;
};

// An implementation of BinaryWriter that writes to files.
class FileBinaryWriter : public BinaryWriterImpl {
 public:
  explicit FileBinaryWriter(const std::string& filename);
#ifdef _WIN32
  explicit FileBinaryWriter(const std::wstring& filename);
#endif
};

// An implementation of BinaryWriter that writes to memory.
class MemoryBinaryWriter : public BinaryWriterImpl {
 public:
  MemoryBinaryWriter();
  // Gets the contents of the stream as a std::string.
  std::string GetContents() const;
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_BINARY_WRITER_IMPL_H_
