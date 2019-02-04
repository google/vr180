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
    name = "delayed_low_pass_filter",
    hdrs = ["delayed_low_pass_filter.h"],
    deps = [
        ":low_pass_filter",
        "@com_google_glog//:glog",
        "@eigen//:eigen",
    ],
)

cc_library(
    name = "geometry_toolbox_mahony",
    hdrs = ["geometry_toolbox_mahony.h"],
    deps = [
        "@com_google_glog//:glog",
        "@eigen//:eigen",
    ],
)

cc_library(
    name = "high_pass_filter",
    hdrs = ["high_pass_filter.h"],
    deps = [
        ":geometry_toolbox_mahony",
        "@com_google_glog//:glog",
        "@eigen//:eigen",
    ],
)

cc_library(
    name = "low_pass_filter",
    hdrs = ["low_pass_filter.h"],
    deps = [
        ":geometry_toolbox_mahony",
        "@com_google_glog//:glog",
        "@eigen//:eigen",
    ],
)

cc_library(
    name = "orientation_filter_mahony",
    srcs = [
        "orientation_filter.cc",
        "quaternion_integrator.cc",
    ],
    hdrs = [
        "orientation_filter.h",
        "quaternion_integrator.h",
    ],
    deps = [
        ":geometry_toolbox_mahony",
        ":low_pass_filter",
        ":stationary_detector",
        "@com_google_glog//:glog",
        "@eigen//:eigen",
    ],
)

cc_library(
    name = "stationary_detector",
    srcs = ["stationary_detector.cc"],
    hdrs = ["stationary_detector.h"],
    deps = [
        ":delayed_low_pass_filter",
        ":geometry_toolbox_mahony",
        ":high_pass_filter",
        ":low_pass_filter",
        "@com_google_glog//:glog",
        "@eigen//:eigen",
    ],
)

cc_library(
    name = "online_sensor_fusion",
    srcs = [
        "online_sensor_fusion.cc",
    ],
    hdrs = [
        "online_sensor_fusion.h",
    ],
    deps = [
        ":orientation_filter_mahony",
        "@com_google_glog//:glog",
        "@com_google_absl//absl/memory",
        "@eigen//:eigen",
    ],
)

cc_test(
    name = "orientation_filter_mahony_test",
    size = "small",
    srcs = ["orientation_filter_test.cc"],
    tags = [
        "linux-native-test",
    ],
    deps = [
        ":geometry_toolbox_mahony",
        ":orientation_filter_mahony",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "delayed_low_pass_filter_test",
    size = "small",
    srcs = ["delayed_low_pass_filter_test.cc"],
    tags = [
        "linux-native-test",
    ],
    deps = [
        ":delayed_low_pass_filter",
        "@com_google_googletest//:gtest_main",
        "@eigen//:eigen",
    ],
)

cc_test(
    name = "high_pass_filter_test",
    size = "small",
    srcs = ["high_pass_filter_test.cc"],
    tags = [
        "linux-native-test",
    ],
    deps = [
        ":high_pass_filter",
        "@com_google_googletest//:gtest_main",
        "@eigen//:eigen",
    ],
)

cc_test(
    name = "stationary_detector_test",
    size = "small",
    srcs = ["stationary_detector_test.cc"],
    tags = [
        "linux-native-test",
    ],
    deps = [
        ":stationary_detector",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "low_pass_filter_test",
    size = "small",
    srcs = ["low_pass_filter_test.cc"],
    tags = [
        "linux-native-test",
    ],
    deps = [
        ":low_pass_filter",
        "@com_google_googletest//:gtest_main",
        "@eigen//:eigen",
    ],
)

cc_test(
    name = "online_sensor_fusion_test",
    srcs = [
        "online_sensor_fusion_test.cc",
    ],
    deps = [
        ":online_sensor_fusion",
        "@com_google_googletest//:gtest_main",
        "@com_google_googletest//:gtest",
        "@eigen//:eigen",
    ],
)
