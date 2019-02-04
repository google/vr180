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

#ifndef VR180_CPP_JNI_JNI_UTILS_H_
#define VR180_CPP_JNI_JNI_UTILS_H_

#include <jni.h>
#include <string>
#include <vector>

namespace vr180 {
// Utility function to convert a Java std::string to std::string.
std::string JavaStringToCppString(JNIEnv* env, jstring java_string);

// Utility function to convert a Java byteArray to std::string.
//
// @warning The returned std::string contains binary data, possibly including
//          null characters ('\0'). This is fine for std::string objects,
//          but do not use the std::string::c_str() method unless you can
//          ensure no null characters are present in the data.
std::string JavaByteArrayToCppString(JNIEnv* env, jbyteArray array);

// Utility function to convert a Java floatArray to a std::vector<float>.
std::vector<float> JavaFloatArrayToCppVector(JNIEnv* env, jfloatArray array);
}  // namespace vr180

#endif  // VR180_CPP_JNI_JNI_UTILS_H_
