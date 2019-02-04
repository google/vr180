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

#include <memory>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "cpp/common/io.h"
#include "cpp/video/atoms/atom_moov.h"
#include "cpp/video/modify_moov.h"
#include "cpp/video/spherical_inject.h"
#include "cpp/video/stereo_mode.h"

namespace vr180 {

const char kStitcher[] = "VR180 Metadata Injector";

void TestInjectFromFile(const std::string& input_file, const std::string& expected_file,
                        bool inplace, bool inject_v1) {
  const std::string temp_file =  "vr180.mp4";

  std::string in = input_file;
  const std::string out = temp_file;
  if (inplace) {
    SetFileContents(temp_file, GetFileContentsOrEmpty(input_file));
    in = temp_file;
  }

  const std::string sv3d = GetFileContentsOrEmpty(
      
      "cpp/video/testdata/sv3d.bin");
  EXPECT_TRUE(ModifyMoov(
                  [&sv3d, &inject_v1](AtomMOOV* moov) {
                    if (inject_v1) {
                      EXPECT_TRUE(InjectSphericalV1MetadataToMoov(
                                      kStitcher, StereoMode::TOP_BOTTOM, 1072,
                                      1504, 180, 180, moov)
                                      .ok());
                    }
                    return InjectProjectionMetadataToMoov(
                        StereoMode::TOP_BOTTOM, sv3d, moov);
                  },
                  in, out)
                  .ok());

  const std::string result = GetFileContentsOrEmpty(temp_file);
  const std::string expected = GetFileContentsOrEmpty(expected_file);
  EXPECT_TRUE(result == expected) << result.size() << " " << expected.size();
}

TEST(InjectSphericalMetadata, InjectFromFile) {
  const std::string video_file_name =
      
      "cpp/video/testdata/video-sample_no_mesh.mp4";
  const std::string expected_video_file_name =
      
      "cpp/video/testdata/video-sample-inject.mp4";

  TestInjectFromFile(video_file_name, expected_video_file_name,
                     /*inplace=*/false, /*inject_v1=*/false);
}

TEST(InjectSphericalMetadata, InjectFromFileInplace) {
  const std::string video_file_name =
      
      "cpp/video/testdata/video-sample_no_mesh.mp4";
  const std::string expected_video_file_name =
      
      "cpp/video/testdata/"
      "video-sample-inplace-inject.mp4";

  TestInjectFromFile(video_file_name, expected_video_file_name,
                     /*inplace=*/true, /*inject_v1=*/false);
}

TEST(InjectSphericalMetadata, InjectFromFileWithV1) {
  const std::string video_file_name =
      
      "cpp/video/testdata/video-sample_no_mesh.mp4";
  const std::string expected_video_file_name =
      
      "cpp/video/testdata/"
      "video-sample-inject_v1.mp4";

  TestInjectFromFile(video_file_name, expected_video_file_name,
                     /*inplace=*/false, /*inject_v1=*/true);
}
}  // namespace vr180
