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
    name = "atom_edts",
    srcs = [
        "atom_edts.cc",
    ],
    hdrs = [
        "atom_edts.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_registry",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_elst",
    srcs = [
        "atom_elst.cc",
    ],
    hdrs = [
        "atom_elst.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:format_status",
        "//cpp/video:full_atom",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_moov",
    srcs = [
        "atom_moov.cc",
    ],
    hdrs = [
        "atom_moov.h",
    ],
    deps = [
        ":atom_trak",
        "//cpp/video:atom",
        "//cpp/video:atom_helpers",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_tkhd",
    srcs = [
        "atom_tkhd.cc",
    ],
    hdrs = [
        "atom_tkhd.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:full_atom",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_trak",
    srcs = [
        "atom_trak.cc",
    ],
    hdrs = [
        "atom_trak.h",
    ],
    deps = [
        ":atom_hdlr",
        ":atom_mdia",
        ":atom_minf",
        ":atom_stbl",
        ":atom_stsd",
        ":common",
        ":visual_sample_entry",
        "//cpp/video:atom",
        "//cpp/video:atom_helpers",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_camm",
    srcs = [
        "atom_camm.cc",
    ],
    hdrs = [
        "atom_camm.h",
    ],
    deps = [
        ":common",
        "//cpp/video:atom",
        "//cpp/video:atom_helpers",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:format_status",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,  # buildozer: disable=alwayslink-with-hdrs
)

cc_library(
    name = "common",
    hdrs = [
        "common.h",
    ],
)

cc_library(
    name = "atom_hdlr",
    srcs = [
        "atom_hdlr.cc",
    ],
    hdrs = [
        "atom_hdlr.h",
    ],
    deps = [
        ":common",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:full_atom",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_stsd",
    srcs = [
        "atom_stsd.cc",
    ],
    hdrs = [
        "atom_stsd.h",
    ],
    deps = [
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:full_atom",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_stbl",
    srcs = [
        "atom_stbl.cc",
    ],
    hdrs = [
        "atom_stbl.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_registry",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_minf",
    srcs = [
        "atom_minf.cc",
    ],
    hdrs = [
        "atom_minf.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_registry",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_mdia",
    srcs = [
        "atom_mdia.cc",
    ],
    hdrs = [
        "atom_mdia.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_registry",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "visual_sample_entry",
    srcs = [
        "visual_sample_entry.cc",
    ],
    hdrs = [
        "visual_sample_entry.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:format_status",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_sdtp",
    srcs = [
        "atom_sdtp.cc",
    ],
    hdrs = [
        "atom_sdtp.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:format_status",
        "//cpp/video:full_atom",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_st3d",
    srcs = [
        "atom_st3d.cc",
    ],
    hdrs = [
        "atom_st3d.h",
    ],
    deps = [
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:full_atom",
        "//cpp/video:stereo_mode",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_stss",
    srcs = [
        "atom_stss.cc",
    ],
    hdrs = [
        "atom_stss.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:format_status",
        "//cpp/video:full_atom",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_stco",
    srcs = [
        "atom_stco.cc",
    ],
    hdrs = [
        "atom_stco.h",
    ],
    deps = [
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:format_status",
        "//cpp/video:full_atom",
        "@com_google_absl//absl/strings:str_format",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_sv3d",
    srcs = [
        "atom_sv3d.cc",
    ],
    hdrs = [
        "atom_sv3d.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)

cc_library(
    name = "atom_uuid",
    srcs = [
        "atom_uuid.cc",
    ],
    hdrs = [
        "atom_uuid.h",
    ],
    deps = [
        "//cpp/video:atom",
        "//cpp/video:atom_helpers",
        "//cpp/video:atom_registry",
        "//cpp/video:binary_reader",
        "//cpp/video:binary_writer",
        "//cpp/video:format_status",
        "//cpp/video/atoms:common",
        "@com_google_absl//absl/strings",
        "@com_google_glog//:glog",
    ],
    alwayslink = 1,
)
