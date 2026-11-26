# Roadmap

## 0.1 · Camera and darkroom foundations

- CameraX preview, tap-to-focus, lens switching, JPEG capture, and permission/error handling.
- Thirds, center, and level guides using luminance, concentrated detail, and device orientation.
- Live film color rendering and vignettes, local library, image import, basic editing, and full-resolution JPEG export.
- Gradle verification, GitHub Actions APK artifacts, and device smoke testing.

## Camera assistant

- Add offline subject detection with confidence gating and mode-specific framing for portrait, landscape, street, architecture, food, macro, and night.
- Validate lighting and background metrics against representative scenes. Gate distance or translation cues on evidence from calibrated depth or motion when supported.
- Add reference image alignment and ghost overlays, accessibility controls for voice and haptics, and opt-in automatic capture.

## AnalogLab

- Save custom recipes. Add adjustable grain, halation, bloom, and light leaks through a shared preview and export pipeline.
- Add local virtual rolls and opt-in delayed development.

## PocketDarkroom

- Add persistent edit history, batch export, crop and perspective, histograms, RGB curves, HSL, and color grading.
- Add local adjustment masks, sharpening, and noise reduction. Explore Camera2 RAW capability and DNG decode and export on supported hardware.
- Profile GPU processing, full-resolution memory use, and compatibility on Android 10+ devices.
