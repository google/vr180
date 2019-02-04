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
import android.graphics.BitmapFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class BitmapIOTest {
  /** Size of the test image. */
  private static final int SIZE = 100;
  /** Color of the test bitmap. */
  private static final int TEST_COLOR = 0xffeeeeee;

  @Test
  public void testJpegByteArrayCompress() throws Exception {
    byte[] jpegBytes = BitmapIO.toByteArray(createTestBitmap(), 80 /* quality */);

    // Test that we can decode it.
    Bitmap jpegBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    Assert.assertEquals(SIZE, jpegBitmap.getWidth());
    Assert.assertEquals(SIZE, jpegBitmap.getHeight());
    // Robolectric doesn't actually implement encoding, so this is best we can do.
  }

  @Test
  public void testPngByteArrayCompress() throws Exception {
    byte[] pngBytes = BitmapIO.toByteArray(createTestBitmap(), -1 /* quality */);

    // Test that we can decode it.
    Bitmap pngBitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.length);
    Assert.assertEquals(SIZE, pngBitmap.getWidth());
    Assert.assertEquals(SIZE, pngBitmap.getHeight());
    // Robolectric doesn't actually implement encoding, so this is best we can do.
  }

  @Test
  public void testWebpByteArrayCompress() throws Exception {
    byte[] webpBytes = BitmapIO.toWebpByteArray(createTestBitmap(), 80 /* quality */);

    // Test that we can decode it.
    Bitmap webpBitmap = BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.length);
    Assert.assertEquals(SIZE, webpBitmap.getWidth());
    Assert.assertEquals(SIZE, webpBitmap.getHeight());
    // Robolectric doesn't actually implement encoding, so this is best we can do.
  }

  @Test
  public void testJpegFileCompress() throws Exception {
    BitmapIO.saveBitmap(createTestBitmap(), 80 /* quality */, "testfile.jpg");

    // Test that we can decode it.
    Bitmap jpegBitmap = BitmapFactory.decodeFile("testfile.jpg");
    Assert.assertEquals(SIZE, jpegBitmap.getWidth());
    Assert.assertEquals(SIZE, jpegBitmap.getHeight());
    // Robolectric doesn't actually implement encoding, so this is best we can do.
  }

  @Test
  public void testPngFileCompress() throws Exception {
    BitmapIO.saveBitmap(createTestBitmap(), -1 /* quality */, "testfile.png");

    // Test that we can decode it.
    Bitmap pngBitmap = BitmapFactory.decodeFile("testfile.png");
    Assert.assertEquals(SIZE, pngBitmap.getWidth());
    Assert.assertEquals(SIZE, pngBitmap.getHeight());
    // Robolectric doesn't actually implement encoding, so this is best we can do.
  }

  private static Bitmap createTestBitmap() {
    int[] colors = new int[SIZE * SIZE];
    for (int i = 0; i < SIZE * SIZE; ++i) {
      colors[i] = TEST_COLOR;
    }
    return Bitmap.createBitmap(colors, SIZE, SIZE, Bitmap.Config.ARGB_8888);
  }
}
