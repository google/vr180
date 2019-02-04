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

#ifndef VR180_CPP_VIDEO_FORMAT_STATUS_H_
#define VR180_CPP_VIDEO_FORMAT_STATUS_H_

#include <memory>
#include <string>
#include "absl/base/attributes.h"
#include "absl/strings/string_view.h"

namespace vr180 {

// A templated simple status class over an enum class ErrorCodeType.
template <typename ErrorCodeType>
class ABSL_MUST_USE_RESULT Status {
 public:
  // Constructs an OK status.
  static Status OkStatus() { return Status<ErrorCodeType>(); }

  // Constructs an error status with an error and a message.
  static Status Error(ErrorCodeType error_code, absl::string_view message) {
    return Status<ErrorCodeType>(error_code, message);
  }

  // Returns whether the status is OK.
  bool ok() const { return error_code_ == ErrorCodeType::OK; }

  // Returns the error message as absl::string_view.
  absl::string_view message() const {
    return message_ != nullptr ? *message_ : absl::string_view();
  }

  // Returns the error code.
  ErrorCodeType error_code() const { return error_code_; }

 private:
  Status() : error_code_(ErrorCodeType::OK) {}
  Status(ErrorCodeType error_code, absl::string_view message)
      : error_code_(error_code), message_(new std::string(message)) {}

  ErrorCodeType error_code_;
  std::unique_ptr<std::string> message_;
};
}  // namespace vr180

#endif  // VR180_CPP_VIDEO_FORMAT_STATUS_H_
