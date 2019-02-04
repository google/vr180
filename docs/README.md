# VR180 Camera Integration Guide

This folder contains documents for building a VR180 camera.

A high-level overview is available in the [overview.pptx](./overview.pptx).

1.  Camera Hardware
    *   See [hardware.md](./hardware.md) for detailed guideline for choosing the
        right hardware.
    *   For initial evaluation of the camera app, you may use an Android phone
        as a camera emulator.
2.  Android Firmware
    *   Android 9.0 or above is recommended.
    *   A virtual stereo camera (left-Right-eye stitched image output) should be
        provided through the
        [Android Camera2 API](https://developer.android.com/reference/android/hardware/camera2/package-summary).
        *   **Implementation is NOT provided in this repo.**
        *   OEMs need to implement based on the hardware platform. Google's
            reference camera is built on the QCS605 platform.
        *   If the virtual camera id is not 0, customize the id in
            `CameraConfigurator` class.
    *   Real timestamps are needed for synchronizing data between image and IMU
        data. Synchronization is critical for producing high-quality VR180
        videos.
    *   It is recommended to pass
        [CTS](https://source.android.com/compatibility/cts) tests related to
        Camera2 API (and Bluetooth and WiFi if the camera needs connectivity to
        the VR180 companion app or Internet).
3.  Camera App Integration
    *   A reference implementation of the Camera App is provided in this repo.
        Customization is required for OEM-specific configurations.
    *   The Camera App requires system-level permission to be fully functional.
        The APK file should be pushed to `/system/priv-app/` and the native
        libraries (.so files) need to be pushed to `/system/lib/` (or
        `/system/lib64` depending on the architecture).
4.  Camera Calibration.
    *   [VR180](https://github.com/google/spatial-media/blob/master/docs/vr180.md)-specific
        camera calibration is required on a per-unit basis to generate the
        required metadata. The calibration tool is provided in the
        [/tools](../tools) folder. The Camera App has calibration recorder mode
        which can capture the required dataset.
    *   Once calibration is completed, the output files should be saved to the
        camera's storage. It is recommended to store the calibration data under
        `/persist` partition. Customize the `ProjectionMetadataProvider` class
        for the actual location of the calibration data.
    *   See [calibration.md](./calibration.md) for instructions.
5.  Validation Tool
    *   Once proper hardware, firmware, and software are developed, and
        calibration is completed, the output photos and videos should be
        validated for file format and geometry.
    *   The validation tool is provided in the [/tools](../tools) folder.
    *   See [validation.md](./validation.md) for details.
