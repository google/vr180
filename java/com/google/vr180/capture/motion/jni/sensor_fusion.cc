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

#include <Eigen/Core>
#include <Eigen/Geometry>
#include "cpp/jni/macros.h"
#include "cpp/sensor_fusion/online_sensor_fusion.h"

#undef JNI_PACKAGE_NAME
#define JNI_PACKAGE_NAME com_google_vr180_capture_motion

#undef JNI_CLASS_NAME
#define JNI_CLASS_NAME SensorFusion

using vr180::OnlineSensorFusion;

extern "C" {

namespace {
static const double kNanoSecondToSecond = 1e-9;

static Eigen::Vector3d JNIFloatArrayToVector3d(JNIEnv* env, jfloatArray array) {
  float tmp[3];
  env->GetFloatArrayRegion(array, 0, 3, tmp);
  const Eigen::Vector3d vec3(tmp[0], tmp[1], tmp[2]);
  return vec3;
}

static Eigen::Matrix3d JNIFloatArrayToMatrix3d(JNIEnv* env, jfloatArray array) {
  float tmp[9];
  env->GetFloatArrayRegion(array, 0, 9, tmp);
  return Eigen::Map<Eigen::Matrix3f>(tmp, 3, 3).cast<double>();
}

inline jlong jptr(OnlineSensorFusion* native_object) {
  return reinterpret_cast<intptr_t>(native_object);
}

inline OnlineSensorFusion* native(jlong ptr) {
  return reinterpret_cast<OnlineSensorFusion*>(ptr);
}

}  // namespace

JNIEXPORT jlong JNICALL JNI_METHOD(nativeInit)(
    JNIEnv* env, jobject obj, jfloatArray device_to_imu_transform) {
  OnlineSensorFusion::Options options;
  options.device_to_imu_transform =
      JNIFloatArrayToMatrix3d(env, device_to_imu_transform);
  return jptr(new OnlineSensorFusion(options));
}

JNIEXPORT void JNICALL JNI_METHOD(nativeRelease)(JNIEnv* env, jobject obj,
                                                 jlong native_object) {
  delete native(native_object);
}

JNIEXPORT void JNICALL JNI_METHOD(nativeAddGyroMeasurement)(
    JNIEnv* env, jobject obj, jlong native_object, jfloatArray gyro,
    jlong timestamp_ns) {
  OnlineSensorFusion* filter = native(native_object);
  if (filter == nullptr) {
    return;
  }
  const auto gyro_vec3 = JNIFloatArrayToVector3d(env, gyro);
  const double timestamp_s = timestamp_ns * kNanoSecondToSecond;
  filter->AddGyroMeasurement(gyro_vec3, timestamp_s);
}

JNIEXPORT void JNICALL JNI_METHOD(nativeAddAccelMeasurement)(
    JNIEnv* env, jobject obj, jlong native_object, jfloatArray accel,
    jlong timestamp_ns) {
  OnlineSensorFusion* filter = native(native_object);
  if (filter == nullptr) {
    return;
  }
  const auto accel_vec3 = JNIFloatArrayToVector3d(env, accel);
  const double timestamp_s = timestamp_ns * kNanoSecondToSecond;
  filter->AddAccelMeasurement(accel_vec3, timestamp_s);
}

JNIEXPORT jfloatArray JNICALL JNI_METHOD(nativeGetOrientation)(
    JNIEnv* env, jobject obj, jlong native_object) {
  OnlineSensorFusion* filter = native(native_object);
  jfloatArray out = env->NewFloatArray(3);
  if (filter == nullptr) {
    return out;
  }
  Eigen::Vector3f v = filter->GetOrientation();
  float carray[3] = {v[0], v[1], v[2]};
  env->SetFloatArrayRegion(out, 0, 3, carray);
  return out;
}

JNIEXPORT void JNICALL JNI_METHOD(nativeRecenter)(JNIEnv* env, jobject obj,
                                                  jlong native_object) {
  OnlineSensorFusion* filter = native(native_object);
  if (filter == nullptr) {
    return;
  }
  filter->Recenter();
}

JNIEXPORT void JNICALL JNI_METHOD(nativeSetGyroBias)(JNIEnv* env, jobject obj,
                                                     jlong native_object,
                                                     jfloatArray bias) {
  OnlineSensorFusion* filter = native(native_object);
  if (filter == nullptr) {
    return;
  }
  const auto gyro_bias = JNIFloatArrayToVector3d(env, bias);
  filter->SetGyroBias(gyro_bias);
}
}
