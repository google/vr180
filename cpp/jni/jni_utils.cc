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

#include "cpp/jni/jni_utils.h"

namespace vr180 {

std::string JavaStringToCppString(JNIEnv* env, jstring java_string) {
  if (!java_string) return std::string();

  const char* data = env->GetStringUTFChars(java_string, NULL);
  size_t size = env->GetStringUTFLength(java_string);
  std::string cpp_string(data, size);
  env->ReleaseStringUTFChars(java_string, data);
  return cpp_string;
}

std::string JavaByteArrayToCppString(JNIEnv* env, jbyteArray array) {
  if (!array) return std::string();

  jbyte* data = env->GetByteArrayElements(array, NULL);
  jsize len = env->GetArrayLength(array);
  std::string cpp_string(reinterpret_cast<char*>(data), static_cast<size_t>(len));
  env->ReleaseByteArrayElements(array, data, JNI_ABORT);
  return cpp_string;
}

std::vector<float> JavaFloatArrayToCppVector(JNIEnv* env, jfloatArray array) {
  if (!array) return std::vector<float>();
  const jsize size = env->GetArrayLength(array);
  jfloat* jfloats = env->GetFloatArrayElements(array, nullptr);
  const std::vector<float> vect(jfloats, jfloats + size);
  env->ReleaseFloatArrayElements(array, jfloats, JNI_ABORT);
  return vect;
}

}  // namespace vr180
