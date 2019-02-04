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

#ifndef VR180_CPP_VIDEO_FORMAT_BINARY_READER_IMPL_H_
#define VR180_CPP_VIDEO_FORMAT_BINARY_READER_IMPL_H_

#include <cstdint>
#include <fstream>
#include <memory>
#include <string>

#include "cpp/video/binary_reader.h"

namespace vr180 {

// A class to read network bits from an input stream.
class BinaryReaderImpl : public BinaryReader {
 public:
  explicit BinaryReaderImpl(std::shared_ptr<std::istream> stream);

  uint64_t Size() override;
  uint64_t Tell() const override;
  FormatStatus Seek(uint64_t pos) override;
  std::unique_ptr<BinaryReader> Clone() override;
  FormatStatus ReadUInt8(uint8_t* value) override;
  FormatStatus ReadUInt16(uint16_t* value) override;
  FormatStatus ReadUInt24(uint32_t* value) override;
  FormatStatus ReadUInt32(uint32_t* value) override;
  FormatStatus ReadUInt64(uint64_t* value) override;
  FormatStatus ReadString(std::string* value, uint64_t size) override;

 protected:
  void UpdatePos();
  std::shared_ptr<std::istream> stream_;
  uint64_t pos_ = 0;
};

// A class to read network bits from a file.
class FileBinaryReader : public BinaryReaderImpl {
 public:
  explicit FileBinaryReader(const std::string& filename);
#ifdef _WIN32
  explicit FileBinaryReader(const std::wstring& filename);
#endif
};

// A class to read network bits from a std::string.
class MemoryBinaryReader : public BinaryReaderImpl {
 public:
  explicit MemoryBinaryReader(const std::string& data);
};

}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_BINARY_READER_IMPL_H_
