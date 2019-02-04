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

#include "cpp/video/binary_reader_impl.h"

#include <cerrno>
#include <cstring>

#include <glog/logging.h>
#include "absl/base/internal/endian.h"
#include "absl/strings/str_format.h"
#include "cpp/video/format_status.h"

namespace vr180 {
namespace {
static inline uint32_t gntoh24(uint32_t x) {
#ifdef ABSL_IS_LITTLE_ENDIAN
  return (((x & 0xFF) << 16) | ((x & 0xFF00)) | ((x & 0xFF0000) >> 16));
#else
  return x >> 8;
#endif
}

FormatStatus IsStreamGood(std::istream* stream, const char* function_name) {
  if (stream->fail()) {
    return FormatStatus::Error(
        FormatErrorCode::FILE_UNEXPECTED_EOF,
        absl::StrFormat("BinaryReaderImpl error in %s: %s", function_name,
                        std::strerror(errno)));
  }
  return FormatStatus::OkStatus();
}
}  // namespace

FileBinaryReader::FileBinaryReader(const std::string& filename)
    : BinaryReaderImpl(std::shared_ptr<std::istream>(
          new std::ifstream(filename, std::ios::binary))) {}

#ifdef _WIN32
FileBinaryReader::FileBinaryReader(const std::wstring& filename)
    : BinaryReaderImpl(std::shared_ptr<std::istream>(
          new std::ifstream(filename, std::ios::binary))) {}
#endif

MemoryBinaryReader::MemoryBinaryReader(const std::string& data)
    : BinaryReaderImpl(std::shared_ptr<std::istream>(
          new std::istringstream(data, std::ios::binary))) {}

BinaryReaderImpl::BinaryReaderImpl(std::shared_ptr<std::istream> stream)
    : stream_(std::move(stream)), pos_(0) {}

uint64_t BinaryReaderImpl::Size() {
  stream_->seekg(0, stream_->end);
  const size_t length = stream_->tellg();
  return length;
}

uint64_t BinaryReaderImpl::Tell() const { return pos_; }

FormatStatus BinaryReaderImpl::Seek(uint64_t pos) {
  stream_->seekg(pos, stream_->beg);
  UpdatePos();
  return IsStreamGood(stream_.get(), "Seek");
}

std::unique_ptr<BinaryReader> BinaryReaderImpl::Clone() {
  BinaryReaderImpl* reader = new BinaryReaderImpl(stream_);
  reader->pos_ = pos_;
  return std::unique_ptr<BinaryReader>(reader);
}

FormatStatus BinaryReaderImpl::ReadUInt8(uint8_t* value) {
  RETURN_IF_FORMAT_ERROR(Seek(pos_));
  stream_->read(reinterpret_cast<char*>(value), sizeof(uint8_t));
  UpdatePos();
  return IsStreamGood(stream_.get(), "ReadUInt8");
}

FormatStatus BinaryReaderImpl::ReadUInt16(uint16_t* value) {
  RETURN_IF_FORMAT_ERROR(Seek(pos_));
  stream_->read(reinterpret_cast<char*>(value), sizeof(uint16_t));
  *value = absl::gntohs(*value);
  UpdatePos();
  return IsStreamGood(stream_.get(), "ReadUInt16");
}

FormatStatus BinaryReaderImpl::ReadUInt24(uint32_t* value) {
  RETURN_IF_FORMAT_ERROR(Seek(pos_));
  stream_->read(reinterpret_cast<char*>(value), 3 * sizeof(uint8_t));
  *value = gntoh24(*value);
  UpdatePos();
  return IsStreamGood(stream_.get(), "ReadUInt24");
}

FormatStatus BinaryReaderImpl::ReadUInt32(uint32_t* value) {
  RETURN_IF_FORMAT_ERROR(Seek(pos_));
  stream_->read(reinterpret_cast<char*>(value), sizeof(uint32_t));
  *value = absl::gntohl(*value);
  UpdatePos();
  return IsStreamGood(stream_.get(), "ReadUInt32");
}
FormatStatus BinaryReaderImpl::ReadUInt64(uint64_t* value) {
  RETURN_IF_FORMAT_ERROR(Seek(pos_));
  stream_->read(reinterpret_cast<char*>(value), sizeof(uint64_t));
  *value = absl::gntohll(*value);
  UpdatePos();
  return IsStreamGood(stream_.get(), "ReadUInt64");
}
FormatStatus BinaryReaderImpl::ReadString(std::string* value, uint64_t size) {
  RETURN_IF_FORMAT_ERROR(Seek(pos_));
  *value = std::string(size, 0);
  stream_->read(&(*value)[0], size);
  UpdatePos();
  return IsStreamGood(stream_.get(), "ReadString");
}

void BinaryReaderImpl::UpdatePos() { pos_ = stream_->tellg(); }

}  // namespace vr180
