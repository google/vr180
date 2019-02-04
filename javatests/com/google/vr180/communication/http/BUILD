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
    name = "AuthorizationValidatorTest",
    srcs = ["AuthorizationValidatorTest.java"],
    manifest_values = MANIFEST_VALUES,
    deps = [
        "@com_google_truth//jar",
        "@junit//jar",
        "@mockito//jar",
        "@robolectric//bazel:robolectric",
        "//java/com/google/vr180/api",
        "//java/com/google/vr180/common",
        "//java/com/google/vr180/communication",
    ],
)

android_local_test(
    name = "HttpCameraApiHandlerTest",
    srcs = ["HttpCameraApiHandlerTest.java"],
    manifest_values = MANIFEST_VALUES,
    deps = [
        "//java/com/google/vr180/api",
        "//java/com/google/vr180/communication",
        "//javatests/com/google/vr180/testhelpers",
        "//proto:camera_api_java_proto_lite",
        "@com_google_truth//jar",
        "@httpclient//jar",
        "@httpcore//jar",
        "@mockito//jar",
        "@robolectric//bazel:robolectric",
    ],
)

android_local_test(
    name = "ByteRangeTest",
    srcs = ["ByteRangeTest.java"],
    manifest_values = MANIFEST_VALUES,
    deps = [
        "//java/com/google/vr180/communication",
        "@com_google_truth//jar",
        "@junit//jar",
        "@robolectric//bazel:robolectric",
    ],
)

android_local_test(
    name = "ContentRangeTest",
    srcs = ["ContentRangeTest.java"],
    manifest_values = MANIFEST_VALUES,
    deps = [
        "//java/com/google/vr180/communication",
        "@com_google_truth//jar",
        "@robolectric//bazel:robolectric",
    ],
)

android_local_test(
    name = "HttpSocketServerTest",
    srcs = ["HttpSocketServerTest.java"],
    manifest_values = MANIFEST_VALUES,
    deps = [
        "@com_google_truth//jar",
        "@junit//jar",
        "@mockito//jar",
        "@robolectric//bazel:robolectric",
        "//java/com/google/vr180/communication",
    ],
)

android_local_test(
    name = "MediaDownloadHandlerTest",
    srcs = ["MediaDownloadHandlerTest.java"],
    manifest_values = MANIFEST_VALUES,
    deps = [
        "@com_google_truth//jar",
        "@guava//jar",
        "@mockito//jar",
        "@robolectric//bazel:robolectric",
        "//java/com/google/vr180/api",
        "//java/com/google/vr180/communication",
        "//proto:camera_api_java_proto_lite",
    ],
)
