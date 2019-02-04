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

#include "cpp/common/io.h"

#include <cerrno>
#include <cstdio>
#include <cstring>

#include <glog/logging.h>

namespace vr180 {

namespace {

bool GetFileContents(const std::string& path, std::string* contents) {
  FILE* fd = std::fopen(path.c_str(), "rb");
  if (fd == nullptr) {
    LOG(ERROR) << "Could not open " << path << ": " << std::strerror(errno);
    return false;
  }

  // Resize the input buffer.
  if (std::fseek(fd, 0L, SEEK_END) != 0) {
    std::fclose(fd);
    LOG(ERROR) << "Could not seek in " << path << ": " << std::strerror(errno);
    return false;
  }
  const std::size_t num_bytes = std::ftell(fd);
  if (num_bytes < 0) {
    std::fclose(fd);
    LOG(ERROR) << "Could not tell in " << path << ": " << std::strerror(errno);
    return false;
  }
  contents->resize(num_bytes);
  if (std::fseek(fd, 0L, SEEK_SET) != 0) {
    std::fclose(fd);
    LOG(ERROR) << "Could not seek in " << path << ": " << std::strerror(errno);
    return false;
  }

  // Read the file contents.
  const std::size_t num_read =
      std::fread(const_cast<char*>(contents->data()), 1, num_bytes, fd);

  // Close the file.
  if (std::fclose(fd) != 0) {
    LOG(ERROR) << "Could not close " << path << ": " << std::strerror(errno);
    return false;
  }

  // Check for short reads.
  if (num_read != num_bytes) {
    LOG(ERROR) << "Could not read from " << path
               << ", expected bytes: " << num_bytes
               << ", actual bytes: " << num_read;
    return false;
  }
  return true;
}

}  // namespace

std::string GetFileContentsOrEmpty(const std::string& path) {
  std::string contents;
  if (!GetFileContents(path, &contents)) {
    contents.clear();
  }
  return contents;
}

bool SetFileContents(const std::string& path, const std::string& contents) {
  FILE* fd = std::fopen(path.c_str(), "wb");
  if (fd == nullptr) {
    LOG(ERROR) << "Could not open " << path << ": " << std::strerror(errno);
    return false;
  }

  const std::size_t bytes_written =
      std::fwrite(contents.c_str(), 1, contents.size(), fd);
  if (std::fclose(fd) != 0) {
    LOG(ERROR) << "Could not close " << path << ": " << std::strerror(errno);
    return false;
  }

  if (bytes_written != contents.size()) {
    LOG(ERROR) << "Could not write to " << path
               << ", expected bytes: " << contents.size()
               << ", actual bytes: " << bytes_written;
    return false;
  }
  return true;
}
}  // namespace vr180
