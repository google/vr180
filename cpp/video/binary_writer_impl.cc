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

#include "cpp/video/binary_writer_impl.h"

#include <cerrno>
#include <cstring>
#include <fstream>
#include <sstream>

#include <glog/logging.h>
#include "absl/base/internal/endian.h"
#include "absl/strings/str_format.h"
#include "cpp/video/format_status.h"

namespace vr180 {
namespace {
const uint64_t kChunkBufferSizeBytes = 1 << 20;  // 1 Mb
static inline uint32_t ghton24(uint32_t x) {
#ifdef ABSL_IS_LITTLE_ENDIAN
  return (((x & 0xFF) << 16) | ((x & 0xFF00)) | ((x & 0xFF0000) >> 16));
#else
  return x << 8;
#endif
}

FormatStatus IsStreamGood(std::ostream* stream, const char* function_name) {
  if (stream->fail()) {
    return FormatStatus::Error(
        FormatErrorCode::FILE_WRITE_ERROR,
        absl::StrFormat("BinaryWriterImpl error in %s: %s", function_name,
                        std::strerror(errno)));
  }
  return FormatStatus::OkStatus();
}
}  // namespace

FileBinaryWriter::FileBinaryWriter(const std::string& filename)
    : BinaryWriterImpl(std::shared_ptr<std::ostream>(
          new std::ofstream(filename, std::ios::binary))) {}

#ifdef _WIN32
FileBinaryWriter::FileBinaryWriter(const std::wstring& filename)
    : BinaryWriterImpl(std::shared_ptr<std::ostream>(
          new std::ofstream(filename, std::ios::binary))) {}
#endif

MemoryBinaryWriter::MemoryBinaryWriter()
    : BinaryWriterImpl(std::shared_ptr<std::ostream>(
          new std::ostringstream(std::ios::binary))) {}

std::string MemoryBinaryWriter::GetContents() const {
  if (stream_ == nullptr) return "";
  return reinterpret_cast<const std::ostringstream*>(stream_.get())->str();
}

BinaryWriterImpl::BinaryWriterImpl(std::shared_ptr<std::ostream> stream)
    : stream_(std::move(stream)) {}

uint64_t BinaryWriterImpl::Tell() const {
  if (stream_ == nullptr) return -1;
  return stream_->tellp();
}

FormatStatus BinaryWriterImpl::Seek(uint64_t pos) {
  if (stream_ == nullptr) {
    return FormatStatus::Error(FormatErrorCode::UNEXPECTED_ERROR,
                               "stream may not be null in Seek");
  }
  stream_->seekp(pos);
  return IsStreamGood(stream_.get(), "Seek");
}

FormatStatus BinaryWriterImpl::PutUInt8(uint8_t value) {
  if (stream_ == nullptr) {
    return FormatStatus::Error(FormatErrorCode::UNEXPECTED_ERROR,
                               "stream may not be null in PutUInt8");
  }
  stream_->write(reinterpret_cast<char*>(&value), sizeof(uint8_t));
  return IsStreamGood(stream_.get(), "PutUInt8");
}

FormatStatus BinaryWriterImpl::PutUInt16(uint16_t value) {
  if (stream_ == nullptr) {
    return FormatStatus::Error(FormatErrorCode::UNEXPECTED_ERROR,
                               "stream may not be null in PutUInt16");
  }
  value = absl::ghtons(value);
  stream_->write(reinterpret_cast<char*>(&value), sizeof(uint16_t));
  return IsStreamGood(stream_.get(), "PutUInt16");
}

FormatStatus BinaryWriterImpl::PutUInt24(uint32_t value) {
  if (stream_ == nullptr) {
    return FormatStatus::Error(FormatErrorCode::UNEXPECTED_ERROR,
                               "stream may not be null in PutUInt24");
  }
  value = ghton24(value);
  stream_->write(reinterpret_cast<char*>(&value), 3 * sizeof(uint8_t));
  return IsStreamGood(stream_.get(), "PutUInt24");
}

FormatStatus BinaryWriterImpl::PutUInt32(uint32_t value) {
  if (stream_ == nullptr) {
    return FormatStatus::Error(FormatErrorCode::UNEXPECTED_ERROR,
                               "stream may not be null in PutUInt32");
  }
  value = absl::ghtonl(value);
  stream_->write(reinterpret_cast<char*>(&value), sizeof(uint32_t));
  return IsStreamGood(stream_.get(), "PutUInt32");
}

FormatStatus BinaryWriterImpl::PutUInt64(uint64_t value) {
  if (stream_ == nullptr) {
    return FormatStatus::Error(FormatErrorCode::UNEXPECTED_ERROR,
                               "stream may not be null in PutUInt64");
  }
  value = absl::ghtonll(value);
  stream_->write(reinterpret_cast<char*>(&value), sizeof(uint64_t));
  return IsStreamGood(stream_.get(), "PutUInt64");
}

FormatStatus BinaryWriterImpl::PutString(const std::string& value) {
  if (stream_ == nullptr) {
    return FormatStatus::Error(FormatErrorCode::UNEXPECTED_ERROR,
                               "stream may not be null in PutString");
  }
  stream_->write(value.data(), value.size());
  return IsStreamGood(stream_.get(), "PutString");
}

FormatStatus BinaryWriterImpl::PutData(BinaryReader* reader, uint64_t size) {
  if (stream_ == nullptr) {
    return FormatStatus::Error(FormatErrorCode::UNEXPECTED_ERROR,
                               "stream may not be null in PutData");
  }
  std::string buffer;
  while (size > 0) {
    uint64_t size_to_read = std::min(size, kChunkBufferSizeBytes);
    RETURN_IF_FORMAT_ERROR(reader->ReadString(&buffer, size_to_read));
    stream_->write(buffer.data(), size_to_read);
    RETURN_IF_FORMAT_ERROR(IsStreamGood(stream_.get(), "PutData"));
    size -= size_to_read;
  }
  return FormatStatus::OkStatus();
}

}  // namespace vr180
