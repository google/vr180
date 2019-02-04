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

load("@gmaven_rules//:defs.bzl", "gmaven_artifact")

DEPS = [
    gmaven_artifact("com.android.support:support_annotations:jar:28.0.0"),
    gmaven_artifact("com.android.support:support_fragment:aar:28.0.0"),
    gmaven_artifact("com.android.support:support_v4:aar:28.0.0"),
    "//java/com/google/vr180/common",
    "//proto:camera_api_java_proto_lite",
    "//proto:camera_internal_api_java_proto_lite",
    "@glide//aar",
    "@glide_disklrucache//jar",
    "@glide_gifdecoder//aar",
    "@guava//jar",
    "@httpclient//jar",
    "@httpcore//jar",
    "@jodatime//jar",
    "@jsr305//jar",
    "@okhttp//jar",
    "@rxjava2//jar",
    "@webrtc//aar",
]

android_library(
    name = "api",
    srcs = glob(["**/*.java"]),
    deps = DEPS,
)
