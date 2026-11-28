# Roadmap

- [x] Add CameraX exposure compensation with capability-gated controls.
- [x] Add capability-gated pinch zoom and supported zoom presets.
- [x] Add switchable composition guides independent of shooting mode.
- [x] Let photographers choose a framing target and place a manual subject marker.
- [x] Add locally stored photo favorites and a favorites-only library filter.
- [x] Add optional per-channel RGB histograms and clipping estimates.
- [x] Add a read-only photo information panel in Darkroom.
- [x] Add library sorting by import or known capture date with explicit fallback.
- [x] Add an opt-in volume-key shutter while the camera is ready.
- [x] Add an opt-in live camera luma histogram and preview-only shadow/highlight warnings.
- [x] Add capability-gated tap autofocus feedback with an accessible viewfinder reticle.
- [x] Add off, 3-second, and 10-second shutter timers with cancellation and optional haptic ticks.
- [x] Add flash Off/Auto/On and continuous torch controls when supported by the active camera.

Camera exposure diagnostics use a fixed 32-bin histogram over the existing 40×40 luma sample at the analyzer's bounded ~5 fps cadence. Thresholds use sampled 8-bit preview luma: deep shadows at or below 8, bright pixels at or above 247, and a warning when at least 5% of samples meet either threshold. These estimates are not RAW sensor clipping measurements.

## 0.1 · Camera and darkroom foundations

- CameraX preview, tap-to-focus, lens switching, JPEG capture, and permission/error handling.
- Thirds, center, and level guides using luminance, concentrated detail, and device orientation.
- Live film color rendering and vignettes, local library, image import, basic editing, and full-resolution JPEG export.
- Gradle verification, GitHub Actions APK artifacts, and device smoke testing.

## 0.2 · Shooting modes and reference overlay

- Seven shooting modes with scene-specific framing, tilt, and low-light thresholds.
- Directional guidance requires consecutive detail measurements.
- Pick a reference photo and adjust opacity, position, and scale. Keep the overlay through rotation and Library navigation.

## 0.3 · Portrait face framing

- Guide groups of two to four confidently detected frontal faces after three consistent readings.

- Guide one confidently detected frontal face toward the upper portrait guide using the device’s offline face detector.

## Camera assistant

- Extend offline subject detection beyond single frontal faces and refine framing in other shooting modes.
- Validate lighting and background metrics against representative scenes. Gate distance or translation cues on evidence from calibrated depth or motion when supported.
- Add automatic reference image alignment, accessibility controls for voice and haptics, and opt-in automatic capture.

## AnalogLab

- [x] Save, apply, rename, and delete named local Darkroom recipes without copying crop or rotation.
- Add halation, bloom, and light leaks through a shared preview and export pipeline.
- [x] Add deterministic, resolution-relative film grain with saved intensity and size controls.
- Add local virtual rolls and opt-in delayed development.

## PocketDarkroom

- Export sRGB JPEGs with orientation baked into the rendered pixels and source EXIF left in the original.
- Delete unwanted local photos and their saved Darkroom edit histories with confirmation.
- Undo and redo for Darkroom edits, with an option to save and reopen each photo’s edit history.
- Live luminance histogram for the edited preview.
- Before and after comparison with matching crop and rotation and the original capture look as the baseline.
- Darkroom preview inspection with pinch zoom, double tap, pan, and Fit.
- Master RGB and per-channel curves with black, midtone, and white points.
- Aspect-ratio crop presets with drag positioning and non-destructive export.
- Batch export for selected photos using each saved Darkroom edit.
- Import multiple photos with progress, per-file results, and storage limits.

- Add perspective, HSL, and color grading.
- Add local adjustment masks, sharpening, and noise reduction. Explore Camera2 RAW capability and DNG decode and export on supported hardware.
- Profile GPU processing, full-resolution memory use, and compatibility on Android 10+ devices.
