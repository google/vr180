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

package com.google.vr180.common.media;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class VrVideoCropTest {

  private static final int IN_WIDTH = 384;
  private static final int IN_HEIGHT = 216;
  private static final int OUT_WIDTH = 100;
  private static final int OUT_HEIGHT = 50;
  private static final int MAX_SIZE_MB = 10;
  private static final BitmapPool bitmapPool = new LruBitmapPool(MAX_SIZE_MB * 1024 * 1024);

  @Test
  public void testTransform() {
    VrVideoCrop vrVideoCrop = new VrVideoCrop(StereoMode.LEFT_RIGHT);
    Bitmap input = bitmapPool.get(IN_WIDTH, IN_HEIGHT, Bitmap.Config.ARGB_8888);
    Bitmap output = vrVideoCrop.transform(bitmapPool, input, OUT_WIDTH, OUT_HEIGHT);
    Assert.assertEquals(OUT_WIDTH, output.getWidth());
    Assert.assertEquals(OUT_HEIGHT, output.getHeight());
  }

  @Test
  public void testInputVrCrop() {
    Bitmap input = bitmapPool.get(IN_WIDTH, IN_HEIGHT, Bitmap.Config.ARGB_8888);
    VrVideoCrop vrVideoCrop;
    Rect cropRect;

    vrVideoCrop = new VrVideoCrop(StereoMode.LEFT_RIGHT);
    cropRect = vrVideoCrop.getInputVrCropRect(input);
    Assert.assertEquals(IN_WIDTH / 2, cropRect.right);
    Assert.assertEquals(IN_HEIGHT, cropRect.bottom);

    vrVideoCrop = new VrVideoCrop(StereoMode.TOP_BOTTOM);
    cropRect = vrVideoCrop.getInputVrCropRect(input);
    Assert.assertEquals(IN_WIDTH, cropRect.right);
    Assert.assertEquals(IN_HEIGHT / 2, cropRect.bottom);

    vrVideoCrop = new VrVideoCrop(StereoMode.MONO);
    cropRect = vrVideoCrop.getInputVrCropRect(input);
    Assert.assertEquals(IN_WIDTH, cropRect.right);
    Assert.assertEquals(IN_HEIGHT, cropRect.bottom);
  }

  @Test
  public void testCenterCropRect() {
    Rect crop;

    // Same aspect ratio. Just scale
    crop = VrVideoCrop.getInputCenterCropRect(
        new Rect(0, 0, 100, 100),
        new Rect(0, 0, 50, 50)
    );
    Assert.assertEquals(0, crop.left);
    Assert.assertEquals(0, crop.top);
    Assert.assertEquals(100, crop.right);
    Assert.assertEquals(100, crop.bottom);

    // Input is wider than output. Crop left and right and scale
    crop = VrVideoCrop.getInputCenterCropRect(
        new Rect(0, 0, 100, 100),
        new Rect(0, 0, 25, 50)
    );
    Assert.assertEquals(25, crop.left);
    Assert.assertEquals(0, crop.top);
    Assert.assertEquals(75, crop.right);
    Assert.assertEquals(100, crop.bottom);

    // Input is taller than output. Crop top and bottom and scale
    crop = VrVideoCrop.getInputCenterCropRect(
        new Rect(0, 0, 100, 100),
        new Rect(0, 0, 50, 25)
    );
    Assert.assertEquals(0, crop.left);
    Assert.assertEquals(25, crop.top);
    Assert.assertEquals(100, crop.right);
    Assert.assertEquals(75, crop.bottom);
  }
}
