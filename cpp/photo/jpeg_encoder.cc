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

#include "cpp/photo/jpeg_encoder.h"
#include <stdint.h>

#include <glog/logging.h>

extern "C" {
#include "libjpeg_turbo/jpeglib.h"
}

namespace vr180 {
namespace {
static const char kTag[] = "JPEGEncoder";

class StringJPEGDestination : public jpeg_destination_mgr {
 public:
  static const int kOutputBufferSize = 4096;

  StringJPEGDestination(std::string* out) : data_(out) {
    init_destination = InitDestination;
    empty_output_buffer = EmptyOutputBuffer;
    term_destination = TermDestination;
  }

  static void InitDestination(struct jpeg_compress_struct* compress_info) {
    StringJPEGDestination* dest =
        reinterpret_cast<StringJPEGDestination*>(compress_info->dest);
    dest->data_->clear();
    dest->next_output_byte = dest->buffer_;
    dest->free_in_buffer = kOutputBufferSize;
  }

  static boolean EmptyOutputBuffer(struct jpeg_compress_struct* compress_info) {
    StringJPEGDestination* dest =
        reinterpret_cast<StringJPEGDestination*>(compress_info->dest);
    dest->data_->append(reinterpret_cast<char*>(dest->buffer_),
                        kOutputBufferSize);
    dest->next_output_byte = dest->buffer_;
    dest->free_in_buffer = kOutputBufferSize;
    return TRUE;
  }

  static void TermDestination(struct jpeg_compress_struct* compress_info) {
    StringJPEGDestination* dest =
        reinterpret_cast<StringJPEGDestination*>(compress_info->dest);
    int buffer_used = kOutputBufferSize - dest->free_in_buffer;
    dest->data_->append(reinterpret_cast<char*>(dest->buffer_), buffer_used);
  }

 private:
  std::string* data_;
  uint8_t buffer_[kOutputBufferSize];
};

class JPEGErrorManager : public jpeg_error_mgr {
 public:
  JPEGErrorManager() {
    jpeg_std_error(this);
    error_exit = ErrorExit;
    output_message = OutputMessage;
    error_ = false;
  }

  void set_error() { error_ = true; }
  bool has_error() { return error_; }

  static void ErrorExit(j_common_ptr cinfo) {
    JPEGErrorManager* err = reinterpret_cast<JPEGErrorManager*>(cinfo->err);
    (err->output_message)(cinfo);
    err->set_error();
  }

  static void OutputMessage(j_common_ptr cinfo) {
    char buf[JMSG_LENGTH_MAX];
    (*cinfo->err->format_message)(cinfo, buf);
    LOG(ERROR) << buf;
  }

 private:
  bool error_;
};
}  // namespace

bool EncodeJpeg(const uint8_t* data, int x, int y, int width, int height,
                int stride, int quality, J_COLOR_SPACE color_space,
                std::string* result) {
  // Create error handler and destination.
  JPEGErrorManager err_handler;
  StringJPEGDestination dest(result);

  // Create compressor object.
  struct jpeg_compress_struct compress_info;
  jpeg_create_compress(&compress_info);
  compress_info.dest = &dest;
  compress_info.err = &err_handler;
  compress_info.image_width = width;
  compress_info.image_height = height;
  compress_info.in_color_space = color_space;
  switch (color_space) {
    case JCS_EXT_RGBA:
    case JCS_EXT_RGBX:
    case JCS_EXT_XRGB:
    case JCS_EXT_BGRA:
    case JCS_EXT_BGRX:
    case JCS_EXT_XBGR:
      compress_info.input_components = 4;
      break;
    case JCS_RGB:
    case JCS_EXT_RGB:
    case JCS_EXT_BGR:
      compress_info.input_components = 3;
      break;
    default:
      LOG(ERROR) << "Unsupported color space: " << color_space;
      return false;
  }
  jpeg_set_defaults(&compress_info);
  compress_info.optimize_coding = TRUE;
  jpeg_set_quality(&compress_info, quality, TRUE);

  // Compress the jpeg.
  jpeg_start_compress(&compress_info, TRUE);
  const uint8_t* scanline =
      data + (y * stride) + (x * compress_info.input_components);
  for (int i = 0; i < height; i++) {
    jpeg_write_scanlines(
        &compress_info,
        reinterpret_cast<JSAMPLE**>(const_cast<uint8_t**>(&scanline)), 1);
    scanline += stride;
    if (err_handler.has_error()) {
      jpeg_destroy_compress(&compress_info);
      return false;
    }
  }
  jpeg_finish_compress(&compress_info);
  jpeg_destroy_compress(&compress_info);

  return true;
}

bool EncodeRGBAJpeg(const uint8_t* data, int x, int y, int width, int height,
                    int stride, int quality, std::string* result) {
  return EncodeJpeg(data, x, y, width, height, stride, quality, JCS_EXT_RGBA,
                    result);
}

}  // namespace vr180
