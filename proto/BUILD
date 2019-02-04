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

proto_library(
    name = "camera_api_proto",
    srcs = ["camera_api.proto"],
)

proto_library(
    name = "camera_pairing_proto",
    srcs = ["camera_pairing.proto"],
    deps = [
        ":camera_api_proto",
    ],
)

proto_library(
    name = "camera_internal_api_proto",
    srcs = ["camera_internal_api.proto"],
)

proto_library(
    name = "mesh_protolib",
    srcs = ["mesh.proto"],
)

java_lite_proto_library(
    name = "camera_api_java_proto_lite",
    strict_deps = 0,
    deps = [":camera_api_proto"],
)

java_lite_proto_library(
    name = "camera_pairing_java_proto_lite",
    strict_deps = 0,
    deps = [":camera_pairing_proto"],
)

java_lite_proto_library(
    name = "camera_internal_api_java_proto_lite",
    strict_deps = 0,
    deps = [":camera_internal_api_proto"],
)

java_lite_proto_library(
    name = "mesh_java_proto_lite",
    strict_deps = 0,
    deps = [":mesh_protolib"],
)
