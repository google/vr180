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

package com.google.vr180.capture.camera;

import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import com.google.vr180.common.opengl.EglSurface;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/** An EGL10 context factory for GLSurfaceView that shares with an EGL14-based EglSurface */
public final class SharedEGLContextFactory implements GLSurfaceView.EGLContextFactory {
  private final EGLContext sharedContext;

  public SharedEGLContextFactory(EglSurface eglSurface) {
    eglSurface.makeCurrent();
    sharedContext = ((EGL10) EGLContext.getEGL()).eglGetCurrentContext();
    eglSurface.makeNonCurrent();
  }

  @Override
  public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
    int[] attrib = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
    return egl.eglCreateContext(display, config, sharedContext, attrib);
  }

  @Override
  public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
    egl.eglDestroyContext(display, context);
  }
}
