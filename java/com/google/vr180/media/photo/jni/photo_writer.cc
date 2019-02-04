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

#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <array>
#include <cmath>

#include <glog/logging.h>
#include <Eigen/Core>
#include <Eigen/Geometry>
#include "cpp/common/io.h"
#include "cpp/jni/jni_utils.h"
#include "cpp/jni/macros.h"
#include "cpp/photo/jpeg_encoder.h"
#include "cpp/video/stereo_mode.h"
#include "xmpmeta/gimage.h"
#include "xmpmeta/gpano.h"
#include "xmpmeta/pano_meta_data.h"
#include "xmpmeta/photo_sphere_writer.h"
#include "xmpmeta/vr_photo_writer.h"
#include "xmpmeta/xmp_writer.h"

#undef JNI_PACKAGE_NAME
#define JNI_PACKAGE_NAME com_google_vr180_media_photo

#undef JNI_CLASS_NAME
#define JNI_CLASS_NAME PhotoWriter

namespace {

static const double kRadiansToDegrees = 180.0 / M_PI;
static const int kJpegQuality = 100;

xmpmeta::PanoMetaData GetPanoMetaData(int width, int height, double fov_x,
                                      double fov_y,
                                      const Eigen::Vector3d& euler_angles) {
  xmpmeta::PanoMetaData meta;
  const double ppd_x = width / fov_x;
  const double ppd_y = height / fov_y;
  meta.full_height = static_cast<int>(180.0 * (ppd_x + ppd_y) / 2.0);
  meta.full_width = meta.full_height * 2;
  meta.cropped_width = width;
  meta.cropped_height = height;
  meta.cropped_left = (meta.full_width - meta.cropped_width) / 2;
  meta.cropped_top = (meta.full_height - meta.cropped_height) / 2;
  meta.initial_heading_degrees = 180;
  meta.pose_heading_degrees = 0.0;
  
  
  return meta;
}

// Write left image to jpeg and right image to xmp metadata.
bool WriteVRPhoto(const std::string& left, const std::string& right,
                  const xmpmeta::PanoMetaData& metadata,
                  const std::string& output_path) {
  const std::unique_ptr<xmpmeta::GPano> gpano =
      xmpmeta::GPano::CreateFromData(metadata);
  const std::unique_ptr<xmpmeta::XmpData> xmp_data =
      xmpmeta::CreateXmpData(/*create_extended=*/true);

  if (right.empty()) {
    // For mono photos, only package the GPano into the XMP metadata.
    if (!xmpmeta::WritePhotoSphereMetaToXmp(*gpano, xmp_data.get())) {
      return false;
    }
  } else {
    // Package right eye JPEG and GPano into the XMP metadata.
    const std::unique_ptr<xmpmeta::GImage> gimage =
        xmpmeta::GImage::CreateFromData(right, "image/jpeg");
    if (!xmpmeta::WriteVrPhotoMetaToXmp(*gimage, *gpano, nullptr,
                                        xmp_data.get())) {
      return false;
    }
  }

  // Write jpeg with xmp data.
  return xmpmeta::WriteLeftEyeAndXmpMeta(left, output_path, *xmp_data);
}

// Gets the euler angles about the world axes y,x,z.
Eigen::Vector3d GetEulerAngles(const Eigen::Matrix3d& coeff) {
  Eigen::Vector3d result;
  result[0] = std::atan2(coeff(0, 2), coeff(2, 2));
  const double c2 = Eigen::Vector2d(coeff(1, 1), coeff(1, 0)).norm();
  result[1] = std::atan2(-coeff(1, 2), c2);
  const double s1 = std::sin(result[0]);
  const double c1 = std::cos(result[0]);
  result[2] = std::atan2(s1 * coeff(2, 1) - c1 * coeff(0, 1),
                         c1 * coeff(0, 0) - s1 * coeff(2, 0));
  return result;
}

Eigen::Vector3d GetEulerAngles(const std::array<double, 3>& angle_axis) {
  const Eigen::Vector3d axis_vector(angle_axis.data());
  return GetEulerAngles(
      Eigen::AngleAxisd(axis_vector.norm(), axis_vector.normalized())
          .toRotationMatrix());
}

}  // namespace

extern "C" {
JNIEXPORT bool JNICALL JNI_METHOD(nativeWriteVRPhotoToFile)(
    JNIEnv* env, jobject obj, jbyteArray rgba_buffer, jint stereo_width,
    jint stereo_height, jint stride, jfloat fov_x, jfloat fov_y,
    jfloatArray angle_axis, jint stereo_mode, jstring joutput_path) {
  uint8* rgba_data =
      reinterpret_cast<uint8*>(env->GetByteArrayElements(rgba_buffer, nullptr));
  const std::string output_path = vr180::JavaStringToCppString(env, joutput_path);

  std::string left, right;
  bool success = false;
  if (fov_x == 0 || fov_y == 0) {
    // If we don't have a valid crop just save the raw image.
    success =
        vr180::EncodeRGBAJpeg(rgba_data, 0, 0, stereo_width, stereo_height,
                              stride, kJpegQuality, &left) &&
        vr180::SetFileContents(output_path, left);
  } else {
    // Get the photo metadata.
    const bool is_left_right =
        stereo_mode == static_cast<int>(vr180::StereoMode::LEFT_RIGHT);
    const int width = stereo_width / (is_left_right ? 2 : 1);
    const bool is_top_bottom =
        stereo_mode == static_cast<int>(vr180::StereoMode::TOP_BOTTOM);
    const int height = stereo_height / (is_top_bottom ? 2 : 1);
    const std::vector<float> fangle_axis =
        vr180::JavaFloatArrayToCppVector(env, angle_axis);
    const std::array<double, 3> orientation = {fangle_axis[0], fangle_axis[1],
                                               fangle_axis[2]};
    // Note: Xmpmeta does not currently support pitch and roll, so the full
    // orientation will not be saved.
    const xmpmeta::PanoMetaData metadata = GetPanoMetaData(
        width, height, fov_x, fov_y, GetEulerAngles(orientation));

    success =
        // Encode the left eye.
        vr180::EncodeRGBAJpeg(rgba_data, 0, 0, width, height, stride,
                              kJpegQuality, &left) &&
        // Encode the right eye iff the photo is a stereo photo.
        ((!is_left_right && !is_top_bottom) ||
         vr180::EncodeRGBAJpeg(rgba_data, stereo_width - width,
                               stereo_height - height, width, height, stride,
                               kJpegQuality, &right)) &&
        // Write the image(s) and metadata to file.
        WriteVRPhoto(left, right, metadata, output_path);
  }
  env->ReleaseByteArrayElements(rgba_buffer,
                                reinterpret_cast<jbyte*>(rgba_data), JNI_ABORT);
  return success;
}
}
