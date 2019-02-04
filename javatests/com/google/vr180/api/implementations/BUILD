# Copyright 2018 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

load("//javatests/com/google/vr180/testhelpers:sdk_versions.bzl", "MANIFEST_VALUES")

android_local_test(
    name = "CachedFileChecksumProviderTest",
    srcs = ["CachedFileChecksumProviderTest.java"],
    manifest_values = MANIFEST_VALUES,
    deps = [
        "//java/com/google/vr180/api",
        "//proto:camera_api_java_proto_lite",
        "@com_google_truth//jar",
        "@guava//jar",
        "@mockito//jar",
        "@robolectric//bazel:robolectric",
    ],
)

android_local_test(
    name = "AndroidMediaProviderTest",
    srcs = ["AndroidMediaProviderTest.java"],
    manifest_values = MANIFEST_VALUES,
    deps = [
        "//java/com/google/vr180/api",
        "//javatests/com/google/vr180/testhelpers",
        "//proto:camera_api_java_proto_lite",
        "@com_google_truth//jar",
        "@guava//jar",
        "@mockito//jar",
        "@robolectric//bazel:robolectric",
    ],
)

android_local_test(
    name = "AndroidNetworkManagerTest",
    size = "small",
    srcs = ["AndroidNetworkManagerTest.java"],
    manifest_values = MANIFEST_VALUES,
    deps = [
        "//java/com/google/vr180/api",
        "//proto:camera_api_java_proto_lite",
        "@com_google_truth//jar",
        "@guava//jar",
        "@mockito//jar",
        "@robolectric//bazel:robolectric",
    ],
)

android_local_test(
    name = "AndroidAudioVolumeManagerTest",
    srcs = ["AndroidAudioVolumeManagerTest.java"],
    manifest_values = MANIFEST_VALUES,
    deps = [
        "//java/com/google/vr180/api",
        "//java/com/google/vr180/common",
        "//proto:camera_api_java_proto_lite",
        "@com_google_truth//jar",
        "@guava//jar",
        "@mockito//jar",
        "@robolectric//bazel:robolectric",
    ],
)
