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

#include <glog/logging.h>
#include "cpp/jni/jni_utils.h"
#include "cpp/jni/macros.h"
#include "cpp/video/atoms/atom_moov.h"
#include "cpp/video/edts_inject.h"
#include "cpp/video/format_status.h"
#include "cpp/video/modify_moov.h"
#include "cpp/video/replace_mett_with_camm.h"
#include "cpp/video/sdtp_inject.h"
#include "cpp/video/spherical_inject.h"
#include "cpp/video/stereo_mode.h"

#undef JNI_PACKAGE_NAME
#define JNI_PACKAGE_NAME com_google_vr180_media_metadata

#undef JNI_CLASS_NAME
#define JNI_CLASS_NAME VrMetadataInjector

extern "C" {

const char kStitcher[] = "VR180 Metadata Injector";

JNIEXPORT jboolean JNICALL JNI_METHOD(nativeInjectVRMetadataToVideo)(
    JNIEnv* env, jclass clazz, jint stereo_mode, jbyteArray sv3d, jint width,
    jint height, jfloat fov_x, jfloat fov_y, jstring jpath) {
  const auto& sv3d_string = vr180::JavaByteArrayToCppString(env, sv3d);
  const auto& path = vr180::JavaStringToCppString(env, jpath);

  // Return true if V2 spherical metadata (st3d,sv3d) injection and 'mett'
  // replacement succeed. V1 spherical metadata injection, edts injection, video
  // trak reordering, and sdtp injection are treated as warnings since they
  // aren't required for all players.
  const auto status = vr180::ModifyMoov(
      [stereo_mode, &sv3d_string, width, height, fov_x,
       fov_y](vr180::AtomMOOV* moov) -> vr180::FormatStatus {
        RETURN_IF_FORMAT_ERROR(vr180::InjectProjectionMetadataToMoov(
            static_cast<vr180::StereoMode>(stereo_mode), sv3d_string, moov));
        RETURN_IF_FORMAT_ERROR(vr180::ReplaceMettWithCamm(moov));
        if (!vr180::InjectEdtsToMoov(moov).ok()) {
          LOG(WARNING) << "Error injecting edts box.";
        }
        if (width > 0 && height > 0 && fov_x > 0 && fov_y > 0) {
          if (!vr180::InjectSphericalV1MetadataToMoov(
                   kStitcher, static_cast<vr180::StereoMode>(stereo_mode),
                   width, height, fov_x, fov_y, moov)
                   .ok()) {
            LOG(WARNING) << "Error injecting v1 spherical metadata.";
          }
        }
        if (!vr180::InjectSdtpToMoov(moov).ok()) {
          LOG(WARNING) << "Error injecting sdtp frame drop box.";
        }
        return vr180::FormatStatus::OkStatus();
      },
      path, path);
  LOG_IF(ERROR, !status.ok()) << status.message();
  return status.ok();
}
}
