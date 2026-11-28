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
- [x] Add a version-tagged GitHub Release workflow for signed APK releases and in-place sideloaded updates.

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

- Motion-sensor steadiness indicator and opt-in one-shot steady shutter with a ten-second timeout. Calibrate thresholds on physical devices before expanding to framing-triggered capture.

- Shooting coach for all seven modes, with framing, light and technique lessons, fresh preview observations, optional focus/metering at detected eyes or a user marker, and a still-scene timer shortcut.

- Guided and opt-in one-shot Auto exposure assistance using stable preview luma and supported compensation. Next: calibrate on real scenes, add shooting-mode-specific lighting and focus coaching, and explore capability-gated shutter/ISO advice.

- Verify accessible camera and Darkroom controls on physical devices with TalkBack, switch access, landscape and 200% font scaling; basic scroll layouts and labeled controls are implemented.

- Extend offline subject detection beyond single frontal faces and refine framing in other shooting modes.
- Validate lighting and background metrics against representative scenes. Gate distance or translation cues on evidence from calibrated depth or motion when supported.
- [x] Add optional spoken and haptic cues backed by stable preview evidence.
- Add automatic reference image alignment and opt-in automatic capture.

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
- [x] Resizable edge/corner crops with locked presets, freeform bounds, accessible sliders, and non-destructive export.
- Batch export for selected photos using each saved Darkroom edit.
- [x] Choose a full-size or limited long edge and JPEG quality for single and batch exports.
- Import multiple photos with progress, per-file results, and storage limits.
- Cache recent thumbnails within a memory budget and restore the Library scroll position.

- Add perspective, HSL, and color grading.
- Add local adjustment masks, sharpening, and noise reduction. Explore Camera2 RAW capability and DNG decode and export on supported hardware.
- Profile GPU processing, full-resolution memory use, and compatibility on Android 10+ devices.
