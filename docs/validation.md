# VR180 Validation Toolkit

This file documents how to use the VR180 validation toolkit for developing VR180
cameras. There are two types of validation : format and geometry. The format
validation tools verifies that the file is written according the format spec,
and the geometry validation tools verifies that the 3D geometry is valid.

Whenever there are no-trivial changes (e.g. sensor crop, imu frequency change)
that affect photo capture and video recording, the corresponding validation tool
should be re-run to validate the output again.

[TOC]

## Photo Format Validation

Capture any photo and run validation tool as follows:

```
validate_vr_photo_format_cli --input [name].jpg
```

*   Look for any log messages with 'ERROR' in it.
*   The tool will extract the right eye image.
*   Inspect stereo parallax by toggle between left & right view. Pixel should
    move horizontally at image center.

## Video Format Validation

Capture video with some motion around different axes for this step (no special
setup is needed), and run validation tool as follows:

```
validate_vr180_video_format_cli --input [mp4_file or sv3d_box_file]
```

*   Look for any log messages with 'ERROR' in it.
*   The validation tool will produce a set of Matlab scripts, which can be run
    using 'source' command in Octave or Matlab.
*   Run [name]_motions.m to inspect the recorded motion and see if it matches
    the actual motion. It should plot 3-axis rotation in a figure.
*   Run [name]_mesh_left.m and [recorded]_mesh_right.m to inspect the shape of
    the mesh. It should plot the mesh in 3D.

## Geometry Validation Setup

1.  Print one of the target_*.pdf at the original size
    *   Highly accurate print is the key for accuracy of geometry calculation.
    *   Make sure the scaling option is turned off for the printer.
    *   Use a ruler to measure the scale on the printed target to verify.
1.  Place the printed pattern on a flat surface
    *   The surface should be as flat as possible (e.g using metal for better
        accuracy).
    *   It is also OK to use more than one targets as long as they are
        different. In this case the targets should be placed perpendicular to
        each other like a box.
1.  Install ffmpeg binary to computer, so it can be found in $PATH.
    *   This is used by the validation tool to extract frames from video.

## Photo Geometry Validation

Capture a photo with the aforementioned setup. Make sure the pattern(s) cover a
large portion of the camera viewport. Run validation tools as follows:

```
validate_vr_photo_geometry_cli --input [name].jpg
```

*   Look for the PASS or FAIL result on geometry validation.

## Video Geometry and Motion Validation

Record video with the aforementioned setup as follows:

1.  Hold the camera at about 0.75m from the center of the pattern.

    *   For simple validation, hold the camera static and vertical for 4 seconds
        before recording
    *   For thorough validation of stabilization implementation, it is
        recommended to cover various start scenarios, for example
        *   Consecutive recording : verifies sensor fusion in between captures.
        *   Pick up camera flat on desk & recording : quick change of
            orientation
        *   Sleep & turn on & recording : verifies sensor fusion during sleep
        *   Power on & recording : verifies sensor fusion when booting up.

1.  Start mp4 recording. Without changing the position of the camera do 3 axis
    rotation as follows:

    1.  yaw around vertical axis 0 -> -30 -> 30 -> 0
    1.  tilt around horizontal axis: 0 -> -30 -> 30 -> 0
    1.  roll around optical axis 0 -> -90 -> 90 -> 0

    Besure to keep the motion smooth. Spent about 8 seconds on each axis.

1.  Stop video recording and transfer the recorded [name].mp4 to PC for
    validation.

Run the validation tool as follows:

```
calibrate_vr180_from_mp4_cli --in_mp4 [name].mp4
```

*   Inspect the [Status] section in the output [name]_calibration.txt.
    -   Refer to the "Calibration output format" doc for details.
    -   camera_calibration_pass should be true. This verifies the dual lens
        geometry.
    -   time_alignment_pass should be true. This checks that time offset is
        successfully estimated
    -   imu_extrinsics_pass should be true. This checks that the relative
        orientation between IMU and camera is successfully calculated and it is
        small.
    -   all_pass is a simple and operation of the above three.
*   Inspect stabilization under [Motion] in [name]_calibration.txt.
    -   This is only useful is imu_extrinsics_pass is true.
    -   motion_time_alignment_pass should be true: time_offset_in_ms is close
        to 0.
    -   motion_accuracy_pass should 'typically' be true:
        max_motion_error_in_degrees is small enough. This can sometime be
        affected by abrupt motion before video recording, where you should see
        start_motion_error_in_degrees to be very big.
*   To improve the accuracy of motion evaluation
    -   The pattern should be placed upright, such that one axis is well aligned
        with gravity
    -   Add --upright_pattern when running the validation tool
*   Use Octave or Matlab to inspect the comparison of IMU speed and camera
    speed.
    -   Typically they should match well.
    -   A curve mismatch typically means IMU coordinate is not right
    -   An offset between two curves means the IMU and camera timestamps are not
        synced.
*   Compare lens parameters
    -   By default, the lens parameters is inferred from the embedded mesh
    -   This tool can also run lens calibration to produce reference parameters.
    -   To ignore the mesh and recalibrate the lens, add `--recalibrate
        [lens_prior_if_needed]`
*   In case the validation tool fails:
    -   The circle detection step is a little sensitive to distortion. Some
        adjustment to the capture distance may be required depending on the FOV
        of the lens.
    -   If there are a large number of "too few target points" message, it means
        the capture distance is not right.
    -   This tool may be sensitive to motion blur. Besides smooth motion, use
        strong light to make sure the exposure time is short.
