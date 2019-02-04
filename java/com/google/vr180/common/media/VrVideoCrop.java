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

import static java.nio.charset.StandardCharsets.UTF_16;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Rect;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.google.vr180.common.logging.Log;
import java.nio.charset.Charset;
import java.security.MessageDigest;

/**
 * Glide transformation to crop a vr video bitmap. When the video is top-bottom, split the video
 * horizontally, and when the video is left-right, split the video vertically.
 */
public final class VrVideoCrop extends CenterCrop {
  private static final String TAG = "VrVideoCrop";
  private static final String TRANSFORM_ID = "vr180";
  private static final Charset CHARSET = UTF_16;
  private final int stereoMode;

  public VrVideoCrop(int stereoMode) {
    this.stereoMode = stereoMode;
  }

  @Override
  protected Bitmap transform(BitmapPool bitmapPool, Bitmap bitmap, int outWidth, int outHeight) {
    Rect inRect = getInputVrCropRect(bitmap);
    Rect outRect = new Rect(0, 0, outWidth, outHeight);
    Rect centerCropRect = getInputCenterCropRect(inRect, outRect);
    Bitmap result = bitmapPool.get(outWidth, outHeight, Config.ARGB_8888);
    Canvas canvas = new Canvas(result);
    canvas.drawBitmap(bitmap, centerCropRect, outRect, null);
    Log.d(
        TAG,
        String.format(
            "Bitmap transform: %s, %dx%d+%d+%d -> %dx%d+%d+%d",
            stereoMode,
            inRect.width(),
            inRect.height(),
            inRect.left,
            inRect.top,
            centerCropRect.width(),
            centerCropRect.height(),
            centerCropRect.left,
            centerCropRect.top));
    return result;
  }

  @Override
  public void updateDiskCacheKey(MessageDigest messageDigest) {
    messageDigest.update(TRANSFORM_ID.getBytes(CHARSET));
  }

  Rect getInputVrCropRect(Bitmap bitmap) {
    int inWidth = bitmap.getWidth();
    int inHeight = bitmap.getHeight();
    if (stereoMode == StereoMode.LEFT_RIGHT) {
      inWidth = (int) (inWidth / 2);
    } else if (stereoMode == StereoMode.TOP_BOTTOM) {
      inHeight = (int) (inHeight / 2);
    }
    Rect cropRect = new Rect();
    cropRect.right = inWidth;
    cropRect.bottom = inHeight;
    return cropRect;
  }

  static Rect getInputCenterCropRect(Rect in, Rect out) {
    double scale = Math.min(1.0 * in.width() / out.width(), 1.0 * in.height() / out.height());
    return new Rect(
        in.centerX() - (int) (scale * out.width() / 2),
        in.centerY() - (int) (scale * out.height() / 2),
        in.centerX() + (int) (scale * out.width() / 2),
        in.centerY() + (int) (scale * out.height() / 2)
    );
  }
}
