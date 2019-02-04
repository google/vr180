// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.vr180.media.rtmp;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class BitrateEstimatorTest {
  @Test
  public void testSimpleEstimate() throws Exception {
    BitrateEstimator estimator =
        new BitrateEstimator(/* minTimeBetweenSamplesMs= */ 100, /* numSamples= */ 2);
    estimator.addSample(0, 0);

    assertThat(estimator.getBitrateEstimate()).isEqualTo(0.0);

    // This sample should be ignored because it is too soon after the previous.
    estimator.addSample(50, 25);
    assertThat(estimator.getBitrateEstimate()).isEqualTo(0.0);

    estimator.addSample(100, 50);
    assertThat(estimator.getBitrateEstimate()).isWithin(0.1).of((50.0 * 8) / 0.1);
  }

  @Test
  public void testRollover() throws Exception {
    BitrateEstimator estimator =
        new BitrateEstimator(/* minTimeBetweenSamplesMs= */ 100, /* numSamples= */ 3);
    estimator.addSample(0, 0);
    estimator.addSample(100, 25);
    estimator.addSample(200, 50);
    estimator.addSample(300, 100);

    // Estimate should be based on samples [1] and [3].
    assertThat(estimator.getBitrateEstimate()).isWithin(0.1).of((75.0 * 8) / 0.2);
  }
}
