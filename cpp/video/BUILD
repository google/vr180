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

cc_library(
    name = "modify_moov",
    srcs = ["modify_moov.cc"],
    hdrs = ["modify_moov.h"],
    deps = [
        ":atom",
        ":atom_helpers",
        ":atom_reader",
        ":atom_writer",
        ":binary_reader",
        ":binary_reader_impl",
        ":binary_writer",
        ":binary_writer_impl",
        ":format_status",
        "//cpp/video/atoms:atom_moov",
        "//cpp/video/atoms:atom_stco",
        "//cpp/video/atoms:atom_trak",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "sdtp_inject",
    srcs = ["sdtp_inject.cc"],
    hdrs = ["sdtp_inject.h"],
    deps = [
        ":atom",
        ":atom_helpers",
        ":format_status",
        ":modify_moov",
        "//cpp/video/atoms:atom_moov",
        "//cpp/video/atoms:atom_sdtp",
        "//cpp/video/atoms:atom_stss",
        "//cpp/video/atoms:atom_trak",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "spherical_inject",
    srcs = ["spherical_inject.cc"],
    hdrs = ["spherical_inject.h"],
    deps = [
        ":atom",
        ":atom_helpers",
        ":atom_reader",
        ":binary_reader",
        ":binary_reader_impl",
        ":format_status",
        ":stereo_mode",
        "//cpp/video/atoms:atom_moov",
        "//cpp/video/atoms:atom_st3d",
        "//cpp/video/atoms:atom_sv3d",
        "//cpp/video/atoms:atom_trak",
        "//cpp/video/atoms:atom_uuid",
        "@com_google_absl//absl/memory",
        "@com_google_absl//absl/strings:str_format",
        "@com_google_glog//:glog",
    ],
)

cc_test(
    name = "spherical_inject_test",
    srcs = [
        "spherical_inject_test.cc",
    ],
    data = [
        "//cpp/video/testdata:spherical_injection_data",
    ],
    deps = [
        ":format_status",
        ":modify_moov",
        ":spherical_inject",
        ":stereo_mode",
        "//cpp/common:io",
        "//cpp/video/atoms:atom_moov",
        "@com_google_googletest//:gtest_main",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "edts_inject",
    srcs = ["edts_inject.cc"],
    hdrs = ["edts_inject.h"],
    deps = [
        ":atom_helpers",
        ":format_status",
        ":modify_moov",
        "//cpp/video/atoms:atom_edts",
        "//cpp/video/atoms:atom_elst",
        "//cpp/video/atoms:atom_mdia",
        "//cpp/video/atoms:atom_moov",
        "//cpp/video/atoms:atom_tkhd",
        "//cpp/video/atoms:atom_trak",
        "@com_google_absl//absl/memory",
    ],
)

cc_test(
    name = "edts_inject_test",
    srcs = ["edts_inject_test.cc"],
    data = [
        "//cpp/video/testdata:edts_inject_data",
    ],
    deps = [
        ":edts_inject",
        ":format_status",
        ":modify_moov",
        "//cpp/common:io",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_library(
    name = "replace_mett_with_camm",
    srcs = ["replace_mett_with_camm.cc"],
    hdrs = ["replace_mett_with_camm.h"],
    deps = [
        ":atom",
        ":atom_helpers",
        ":format_status",
        "//cpp/video/atoms:atom_camm",
        "//cpp/video/atoms:atom_moov",
        "//cpp/video/atoms:atom_stsd",
        "//cpp/video/atoms:atom_trak",
        "//cpp/video/atoms:common",
        "@com_google_glog//:glog",
    ],
)

cc_test(
    name = "replace_mett_with_camm_test",
    srcs = [
        "replace_mett_with_camm_test.cc",
    ],
    data = [
        "//cpp/video/testdata:replace_mett_with_camm_data",
    ],
    deps = [
        ":format_status",
        ":modify_moov",
        ":replace_mett_with_camm",
        "//cpp/common:io",
        "@com_google_googletest//:gtest_main",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "atom",
    srcs = [
        "atom.cc",
    ],
    hdrs = [
        "atom.h",
    ],
    deps = [
        ":binary_reader",
        ":binary_writer",
        ":format_status",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "atom_registry",
    srcs = [
        "atom_registry.cc",
    ],
    hdrs = [
        "atom_registry.h",
    ],
    deps = [
        ":atom",
        ":format_status",
        "//cpp/common:googleinit",
        "@com_google_absl//absl/memory",
        "@com_google_absl//absl/synchronization",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "atom_writer",
    srcs = [
        "atom_writer.cc",
    ],
    hdrs = [
        "atom_writer.h",
    ],
    deps = [
        ":atom",
        ":binary_writer",
        ":format_status",
        "@com_google_absl//absl/strings:str_format",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "atom_reader",
    srcs = [
        "atom_reader.cc",
    ],
    hdrs = [
        "atom_reader.h",
    ],
    deps = [
        ":atom",
        ":atom_registry",
        ":format_status",
        "@com_google_absl//absl/strings:str_format",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "full_atom",
    hdrs = [
        "full_atom.h",
    ],
    deps = [
        ":atom",
        ":binary_reader",
        ":binary_writer",
        ":format_status",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "binary_writer",
    hdrs = [
        "binary_writer.h",
    ],
    deps = [
        ":binary_reader",
        ":format_status",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "binary_writer_impl",
    srcs = [
        "binary_writer_impl.cc",
    ],
    hdrs = [
        "binary_writer_impl.h",
    ],
    deps = [
        ":binary_reader",
        ":binary_writer",
        ":format_status",
        "@com_google_absl//absl/base:endian",
        "@com_google_absl//absl/strings:str_format",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "binary_reader",
    hdrs = [
        "binary_reader.h",
    ],
    deps = [
        ":format_status",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "binary_reader_impl",
    srcs = [
        "binary_reader_impl.cc",
    ],
    hdrs = [
        "binary_reader_impl.h",
    ],
    deps = [
        ":binary_reader",
        ":format_status",
        "@com_google_absl//absl/base:endian",
        "@com_google_absl//absl/strings:str_format",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "atom_helpers",
    hdrs = [
        "atom_helpers.h",
    ],
    deps = [
        ":atom",
        "@com_google_glog//:glog",
    ],
)

cc_library(
    name = "format_status",
    hdrs = [
        "format_status.h",
        "status.h",
    ],
    deps = [
        "@com_google_absl//absl/base",
        "@com_google_absl//absl/strings",
    ],
)

cc_library(
    name = "stereo_mode",
    hdrs = [
        "stereo_mode.h",
    ],
)
