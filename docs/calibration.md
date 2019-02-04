# VR180 Calibration Toolkit

This file documents how to use the VR180 calibration toolkit for developing
VR180 cameras.

[TOC]

## Capture setup

1.  Print the provided targets (target_*.pdf) at the original size
    *   Highly accurate print is the key for accuracy of geometry calculation.
    *   Make sure the scaling option is turned off for the printer.
    *   Use a ruler to measure the scale on the printed target to verify.
1.  Place the printed patterns on perpendicular flat surfaces
    *   The surface should be as flat as possible (e.g using metal for better
        accuracy) and aligned with gravity.
    *   More than one targets should be used, and they should be placed
        perpendicular to each other like a box, such that they can cover the
        entire field-of-view of the camera.
1.  Install ffmpeg binary to computer, such that it can be found in $PATH.
    *   This is used by the time-alignment calibration and video-based
        calibration to extract frames from video.

## Photo-based Lens Calibration

Lens calibration can be done with a single stereo fisheye photo. With the
reference camera implementation, stereo fisheye images are saved in photo mode
when calibration mode is enabled (`adb shell setprop debug.vr180.calibration 1`)
.

Suppose the full path of the photo for calibration is
[dataset_path]/calibration.jpg, the calibration tool can be run as follows:

```
calibrate.sh [dataset_path] [lens_prior_if_needed]
```

Lens prior is not needed for the default lens preset, otherwise lens prior needs
to be specified as `--focal_length_in_pixels=focal_length
[--pixel_aspect_ratio=1.0]`. More details about the tool can be found in the
provided calibrate.sh.

Calibration from a single photo would only work well if the target points cover
most of the field of the camera, for example, using a box configuration.

## Video-based Lens+IMU Calibration

Typically IMU is well aligned with camera by design and manufacturing, within 1
degree orientation difference. IF that is NOT the case, we need to calibrate
each device for the relative orientation between IMU and camera for correct
stabilization. This can be done by correlating both video under motion and the
corresponding IMU data.

With the reference camera implementation, a calibration dataset is saved in
video mode when calibration is enabled (`adb setprop debug.vr180.calibration
1`). Suppose the full path of the calibration dataset is [dataset_path], there
would be a list of files as follows

*   calibration.h264: the captured video
*   calibration_frame_timestamps.txt: timestamp of each video frame
*   gyro_data.txt: gyroscope reading during the capture
*   accel_data.txt: gyroscope reading during the capture
*   metadata.txt: device metadata

In order for this calibration to work, the device should go through a sequence
of rotation around each of its 3 axes (similar to the validation process). It is
OK to use hand motion for testing purpose, but factory deployment should use
robot arm to guarantee a consistent motion for every device.

The calibration tool can be run the same as follows:

```
calibrate.sh [dataset_path] [lens_prior_if_needed] [time_offset_prior_if_needed]
```

*   Inspect the [Status] section in the output calibration.txt.
    -   camera_calibration_pass: This verifies the dual lens geometry.
    -   time_alignment_pass:  This checks that time offset is successfully
        estimated
    -   imu_extrinsics_pass: This checks that the relative orientation between
        IMU and camera is successfully calculated and it is small. In addition,
        this checks if the reported gravity direction is correct in the input
        IMU readomgs, which can be used to verity IMU intrinsics calibration
        (look for gravity_error_in_degrees under [IMU]).
    -   all_pass: a simple and operation of the above three.
*   Calibration can be made faster by using a just a subset of frames
    -   The large number of frames is for accurate calculation of time offset
        between motion metadata and video frames, which is shared by all
        devices.
    -   Once the time offset is determined, this calculation step can be
        skipped, and fewer frames are needed.
    -   Add `-compute_time_offset=false -expected_time_offset_ms=?
        --frame_step=5`.

## Time-offset Calibration

A key to VR180 stabilization is accurate alignment of timestamps between motion
metadata and video frames. This type of calibration should be done with a camera
under a smooth rocking motion (e.g. use lab rocker) for accuracy.

This can be done by two methods:

1.  Record a calibration dataset with a rocking motion (30 seconds), run the
    provided time_align.sh to get time offset
1.  Record a VR180 video under a rocking motion, and use the video geometry
    validation tool to find the time offset.

For accuracy, time offset calculation should be run on a large dataset (e.g. 50)
then take the average. Once the time offset is determined, video recording
should compensate accordingly to make IMU and camera align.
