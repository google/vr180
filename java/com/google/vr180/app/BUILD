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

filegroup(
    name = "resource_files",
    srcs = glob(["res/**"]),
)

DEPS = [
    gmaven_artifact("com.android.support:appcompat_v7:aar:28.0.0"),
    gmaven_artifact("com.android.support:localbroadcastmanager:aar:28.0.0"),
    gmaven_artifact("com.android.support:support_annotations:jar:28.0.0"),
    gmaven_artifact("com.android.support:support_fragment:aar:28.0.0"),
    gmaven_artifact("com.android.support:support_v4:aar:28.0.0"),
    "//java/com/google/vr180/api",
    "//java/com/google/vr180/capture",
    "//java/com/google/vr180/common",
    "//java/com/google/vr180/device",
    "//java/com/google/vr180/communication",
    "//java/com/google/vr180/media",
    "//proto:camera_api_java_proto_lite",
    "//proto:camera_internal_api_java_proto_lite",
    "//proto:mesh_java_proto_lite",
    "@guava//jar",
    "@httpclient//jar",
    "@httpcore//jar",
    "@reactivestreams//jar",
    "@rxandroid2//aar",
    "@rxjava2//jar",
    "@webrtc//aar",
]

android_library(
    name = "camera_app_lib",
    srcs = glob(
        [
            "*.java",
            "stubs/*.java",
        ],
        exclude = [
            "CameraAppGlideModule.java",
        ],
    ),
    assets = glob(["assets/**"]),
    assets_dir = "assets",
    manifest = "AndroidManifest.xml",
    resource_files = [":resource_files"],
    deps = DEPS,
)

cc_library(
    name = "camera_native",
    linkopts = [
        "-ldl",
        "-lm",
        "-lz",
        "-Wl,--no-undefined",
    ],
    deps = [
        "//java/com/google/vr180/capture/motion/jni:sensor_fusion",
        "//java/com/google/vr180/media/metadata/jni:vr_metadata_injector",
        "//java/com/google/vr180/media/photo/jni:photo_writer",
    ],
)

java_plugin(
    name = "glide_plugin",
    processor_class = "com.bumptech.glide.annotation.compiler.GlideAnnotationProcessor",
    deps = [
        "@glide//aar",
        "@glide_annotations//jar",
        "@glide_compiler//jar",
    ],
)

APP_DEPS = [
    gmaven_artifact("com.android.support:support_annotations:jar:28.0.0"),
    gmaven_artifact("com.android.support:support_fragment:aar:28.0.0"),
    gmaven_artifact("com.android.support:support_v4:aar:28.0.0"),
    ":camera_app_lib",
    ":camera_native",
    "@glide//aar",
    "@glide_annotations//jar",
]

android_binary(
    name = "camera",
    srcs = [
        "CameraAppGlideModule.java",
    ],
    aapt_version = "aapt",
    manifest = "AndroidManifest.xml",
    multidex = "native",
    plugins = [":glide_plugin"],
    deps = APP_DEPS,
)
