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

package com.google.vr180.common.opengl;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.view.Surface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * A class that creates an EGL surface to render into it using GL ES 2.
 *
 * 
 */
public class EglSurface {
  // See www.khronos.org/registry/egl/extensions/ANDROID/EGL_ANDROID_recordable.txt
  private static final int EGL_RECORDABLE_ANDROID = 0x3142;

  private Surface surface;
  private EGLDisplay display;
  private EGLContext context;
  private EGLSurface eglSurface;

  /**
   * Creates an offscreen EGL surface of size 1x1.
   */
  public EglSurface() {
    this(null, 1, 1);
  }

  /**
   * Creates an offscreen EGL surface that shares a given context.
   * The passed context can be null, in which case a new
   * context will be created.
   */
  public EglSurface(EGLContext sharedContext, int width, int height) {
    surface = null;
    display = createDisplay();
    EGLConfig config = chooseConfig(display, false, true);
    context = createContext(display, sharedContext == null
        ? EGL14.EGL_NO_CONTEXT : sharedContext, config);
    eglSurface = createOffscreenSurface(display, config, width, height);
  }

  /**
   * Creates an EGL surface that shares a given context and that may be
   * recordable. The passed context can be null, in which case a new
   * context will be created.
   */
  public EglSurface(EGLContext sharedContext, Surface surface,
      boolean recordable) {
    this.surface = surface;
    display = createDisplay();
    EGLConfig config = chooseConfig(display, recordable, false);
    context = createContext(display, sharedContext == null
        ? EGL14.EGL_NO_CONTEXT : sharedContext, config);
    eglSurface = createWindowSurface(display, config, surface);
  }

  /**
   * Releases all resources held by this class and the {@link Surface}
   * that was passed to the constructor.
   */
  public void release() {
    if (!Objects.equals(display, EGL14.EGL_NO_DISPLAY)) {
          EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
              EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
          EGL14.eglDestroySurface(display, eglSurface);
          EGL14.eglDestroyContext(display, context);
          EGL14.eglReleaseThread();
          EGL14.eglTerminate(display);
      }

      display = EGL14.EGL_NO_DISPLAY;
      context = EGL14.EGL_NO_CONTEXT;
      eglSurface = EGL14.EGL_NO_SURFACE;
      if (surface != null) {
        surface.release();
      }
  }

  /**
   * Makes the EGL context and surface current.
   */
  public boolean makeCurrent() {
    return EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context);
  }

  /**
   * Makes the EGL context and surface no longer current.
   */
  public boolean makeNonCurrent() {
    return EGL14
        .eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
  }

  /**
   * Swaps the buffers to "publish" the current frame.
   */
  public boolean swapBuffers() {
    return EGL14.eglSwapBuffers(display, eglSurface);
  }

  /**
   * Sends the presentation time stamp to EGL.
   */
  public void setPresentationTime(long timestampNs) {
    EGLExt.eglPresentationTimeANDROID(display, eglSurface, timestampNs);
  }

  private static EGLConfig chooseConfig(EGLDisplay display,
      boolean recordable, boolean offscreen) {
    ArrayList<Integer> attributes = new ArrayList<Integer>();
    attributes.addAll(Arrays.asList(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT));
    if (recordable) {
      attributes.addAll(Arrays.asList(EGL_RECORDABLE_ANDROID, 1));
    }
    if (offscreen) {
      attributes.addAll(Arrays.asList(EGL14.EGL_SURFACE_TYPE,
          EGL14.EGL_PBUFFER_BIT));
    }
    attributes.add(EGL14.EGL_NONE);
    int[] attribList = new int[attributes.size()];
    for (int i = 0; i < attributes.size(); ++i) {
      attribList[i] = attributes.get(i);
    }

    EGLConfig[] configs = new EGLConfig[1];
    int[] numConfigs = new int[1];
    EGL14.eglChooseConfig(display, attribList, 0, configs, 0, configs.length,
        numConfigs, 0);
    if (numConfigs[0] == 0) {
      throw new RuntimeException("Could not find a valid EGL configuration");
    }
    return configs[0];
  }

  private static EGLDisplay createDisplay() {
    EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    if (Objects.equals(display, EGL14.EGL_NO_DISPLAY)) {
        throw new RuntimeException("unable to get EGL14 display");
    }
    int[] version = new int[2];
    if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
        throw new RuntimeException("unable to initialize EGL14");
    }
    return display;
  }

  private static EGLSurface createWindowSurface(EGLDisplay display,
      EGLConfig config, Surface surface) {
    int[] surfaceAttribs = {
            EGL14.EGL_NONE
    };
    return EGL14.eglCreateWindowSurface(display, config,
        surface, surfaceAttribs, 0);
  }

  private static EGLSurface createOffscreenSurface(EGLDisplay display,
      EGLConfig config, int width, int height) {
    int[] surfaceAttribs = {
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
    };
    return EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0);
  }

  private static EGLContext createContext(EGLDisplay display,
      EGLContext sharedContext, EGLConfig config) {
    int[] attribList = {
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
    };
    return EGL14.eglCreateContext(display, config,
        sharedContext, attribList, 0);
  }
}
