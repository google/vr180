# VR180 Camera - Reference Implementation

This repository contains a reference implementation of an Android-based VR180
camera app and related developer tools.

Documentation for building a VR180 camera is available in the [docs/](./docs) folder.

## About VR180

[VR180](https://vr.google.com/vr180/) camera is a new category of VR camera that
uses two wide angle lenses to capture the world as you see it with point and
shoot simplicity. The first of consumer VR180 camera is
[Lenovo's Mirage camera](https://www.lenovo.com/us/en/virtual-reality-and-smart-devices/virtual-and-augmented-reality/lenovo-mirage-camera/Mirage-Camera/p/ZA3A0022US).
VR180 cameras can capture
[VR video](https://github.com/google/spatial-media/blob/master/docs/vr180.md),
[VR photos](https://developers.google.com/vr/reference/cardboard-camera-vr-photo-format),
and do VR180 YouTube live streaming. These media formats are supported by
Google's VR180 companion app
([Android](https://play.google.com/store/apps/details?id=com.google.vr.eva&hl=en_US),
[iOS](https://itunes.apple.com/us/app/vr180/id1345095721?mt=8)), Google Photos, and
Youtube.

## Reference VR180 Camera App

The reference VR180 camera app aims to provide a simple solution for developing
new VR180 cameras, in particular, about handling the hardest part of VR media
formatting and 3D correctness, and motion stabilization. To build a new VR180
camera based on this implementation, most of the logic can be reused , and only
the following instances needs be customized:

*   CapabilitiesProvider : provides the supported video, photo, live modes;
*   PreviewConfigProvider : maps video/photo/live modes to camera preview
    config;
*   CameraConfigurator : OEM-specific configuration of cameras for each mode;
*   ProjectionMetadataProvider provides VR metadata for each mode. This can be
    developed using the providec calibration tools;
*   DeviceInfo: provides global information about device;
*   Hardware: connects device's custom UI with the camera software.

## Calibration and Validation Tools

Binary tools for factory calibration is provided, which can be used to calibrate
the cameras in factory and produce the necessary VR metadata for the reference
camera app.

Additional tools for validating VR video and VR photo is provided. These can be
used for cameras not using the reference implementation to validate the
correctness of the format implementation.

# Getting Started

## Prerequisite
The reference camera app uses the [Bazel](https://bazel.build/) build system. The following tools need to be installed:

* Setup [Android SDK](https://developer.android.com/studio/) and `export ANDROID_HOME=$HOME/Android/Sdk/`
* Setup [Android NDK](https://developer.android.com/ndk/downloads/) and `export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk-bundle`
* Setup [Maven](https://maven.apache.org/install.html)
* Setup [Bazel](https://docs.bazel.build/versions/master/install-ubuntu.html)

### Hardware

An Android 9.0 device is recommended. For development purpose, you may use an
Android phone as a VR180 camera emulator.

The app is last verified on a Pixel XL Android 9.0. It should run on other
Android 9.0 devices, although a Pixel is recommended to avoid compatibility
issues.

Running on Android versions below 9.0 may cause potential compatibility issues
and is not supported by this repo.

## Build

To build the apk: `bazel build java/com/google/vr180/app:camera`

To build and install: `bazel mobile-install java/com/google/vr180/app:camera`

### Android Studio with Bazel

You may also import this project into Android Studio with
[Bazel plugin](https://ij.bazel.build/).

## Usage

The camera app runs out-of-the-box on an Android phone for basic
functions including video and photo capture with proper VR180 file format.

**Note:** If you use a phone as a VR180 camera emulator, the output files will
not have stereo effect.

Pair it with the VR180 companion app enables richer functionality, including
live streaming and photo/video download, viewing, and sharing.

* [Android VR180 App](https://play.google.com/store/apps/details?id=com.google.vr.eva)
* [iOS VR180 App](https://itunes.apple.com/us/app/vr180/id1345095721)

**Note:** It is also possible to develop your own companion app following the
interface defined in `/proto` and matching the communication protocal in
`java/com/google/vr180/communication`.

To pair with the companion app, prepare two phones and follow these steps:

1. Download the VR180 App on Phone 1 (Android or iOS).
2. Compile the camera app and install on Phone 2 (must be Android).
3. Run the VR180 companion app on Phone 1 and follow the pairing instructions
  * You will need to long press the UI shutter button on the Phone 2 to start
    pairing, and short press to confirm pairing.
4. If everything works fine, the Bluetooth and WiFi connection between these two
   phones should establish and you will be able to remote control and transfer
   files.
  * If the connection does not establish, try toggle off and on WiFi and
    Bluetooth on both phones.

## Troubleshooting

* Check the Android SDK/NDK installation path, version, and environment
  variables.
* Check the device's Android version. Some feature might not work below 9.0.
* Some camera app features require system permission. You will need a rooted
  engineering device and push the camera app to `/system/priv-app/` and
  native libraries (.so files) to `/system/lib/` or `/system/lib64/`.
  See the permissions used in the AndroidManifest.xml file for details.
* If you encounter similar
  [issues](https://github.com/bazelbuild/bazel/issues/6814) for
  `bazel mobile-install`, try using `bazel build` and then `adb install`.

# Code Structure
| Module   | Path                          | Description |
| -------- | ----------------------------- | ----------- |
| api      | java/com/google/vr180/api     | Core interfaces and implementations for the camera device. |
| app      | java/com/google/vr180/app     | Reference implementation of the VR180 camera app. |
| capture  | java/com/google/vr180/capture | VR180 video and photo capture module. |
| common   | java/com/google/vr180/common  | Common utility functions. |
| communication | java/com/google/vr180/communication | Bluetooth and WiFi communication with the companion app. |
| device   | java/com/google/vr180/device  | Device-specific configurations. |
| media    | java/com/google/vr180/media   | File and stream format for VR180 videos and photos. |
| tools    | tools/                        | Calibration and validation tools. |
| photo    | cpp/photo                     | VR180 photo format writer. |
| video    | cpp/video                     | VR180 video format writer. |
| sensor fusion | cpp/sensor_fusion        | On-device orientation estimation based on gyroscope and accelerometer. |
| proto    | proto                         | The external (with the companion app) and internal data interface definition using [Protocol Buffers](https://developers.google.com/protocol-buffers/). |
