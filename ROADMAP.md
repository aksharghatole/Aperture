# Roadmap

- [x] Add a read-only photo information panel in Darkroom.
- [x] Add an opt-in volume-key shutter while the camera is ready.

## 0.1 · Camera and darkroom foundations

- CameraX preview, tap-to-focus, lens switching, JPEG capture, and permission/error handling.
- Thirds, center, and level guides using luminance, concentrated detail, and device orientation.
- Live film color rendering and vignettes, local library, image import, basic editing, and full-resolution JPEG export.
- Gradle verification, GitHub Actions APK artifacts, and device smoke testing.

## 0.2 · Shooting modes and reference overlay

- Seven shooting modes with scene-specific framing, tilt, and low-light thresholds.
- Directional guidance requires consecutive detail measurements.
- Pick a reference photo and adjust its viewfinder opacity for manual alignment.

## 0.3 · Portrait face framing

- Guide one confidently detected frontal face toward the upper portrait guide using the device’s offline face detector.

## Camera assistant

- Extend offline subject detection beyond single frontal faces and refine framing in other shooting modes.
- Validate lighting and background metrics against representative scenes. Gate distance or translation cues on evidence from calibrated depth or motion when supported.
- Add automatic reference image alignment, accessibility controls for voice and haptics, and opt-in automatic capture.

## AnalogLab

- Save custom recipes. Add adjustable grain, halation, bloom, and light leaks through a shared preview and export pipeline.
- Add local virtual rolls and opt-in delayed development.

## PocketDarkroom

- Delete unwanted local photos and their saved Darkroom edit histories with confirmation.
- Undo and redo for Darkroom edits, with an option to save and reopen each photo’s edit history.
- Live luminance histogram for the edited preview.
- Master RGB and per-channel curves with black, midtone, and white points.
- Aspect-ratio crop presets with drag positioning and non-destructive export.
- Batch export for selected photos using each saved Darkroom edit.

- Add perspective, HSL, and color grading.
- Add local adjustment masks, sharpening, and noise reduction. Explore Camera2 RAW capability and DNG decode and export on supported hardware.
- Profile GPU processing, full-resolution memory use, and compatibility on Android 10+ devices.
