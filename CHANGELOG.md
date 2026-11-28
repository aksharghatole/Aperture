# Changelog

## Unreleased

- Added reusable named AnalogLab presets for Darkroom looks; applying one is undoable and preserves crop and rotation.

- Added deterministic, resolution-relative film grain with intensity and size controls; saved edits remain compatible with earlier history formats.

- Retained reference overlays through rotation and Library navigation, with manual drag, pinch, sliders, and alignment reset.
- Scaled export workspace estimates for small images so they can export with less available heap.

- Added camera exposure compensation in EV steps when supported by the active lens, with reset and failure feedback.
- Added an opt-in live camera luma histogram and sampled-preview shadow/highlight estimates, capability-gated on frame analysis.
- Added capability-gated tap autofocus with a positioned progress/result reticle and stale request cancellation.
- Added an optional cancellable 3- or 10-second shutter countdown with snapshotted film/camera settings and haptic ticks.
- Added capability-gated still flash Off/Auto/On and torch modes; torch is disabled on camera rebind and shutdown.
- Added pinch-to-zoom and accessible zoom presets limited to each camera's observed range.
- Added an optional RGB channel histogram with per-channel near-black and near-white estimates and accessible text summaries.
- Added a Darkroom Before toggle that keeps the current crop and rotation and shows the capture look for comparison.
- Added zoom and pan for inspecting the Darkroom preview, with a shared position across Before and After.
- Added import-date and EXIF capture-date sorting options to the library.
- Added durable local favorites with an All/Favorites library filter and Darkroom toggle.
- Added persistent camera guide choices for automatic, thirds, center, diagonal, golden-ratio, and off overlays.
- Added a Darkroom photo information panel with original pixel dimensions, format, file size and date, and available EXIF capture details.
- Added an opt-in volume-key shutter shortcut, disabled by default and active only while the camera can capture.
- Reject recognized animations on import and fill transparent pixels with white for JPEG export.
- Retain capture outcomes, single exports, and batch progress across Activity recreation.
- Check export memory before decoding and offer an explicit smaller JPEG when needed.
- Recover stale Aperture temporary files, orphan edit files, and verified unfinished exports.
- Added regression fixtures for cropped, rotated, and mirrored camera luma sampling.
- Added Master RGB and individual red, green, and blue curves with live previews.
- Clear frame-analysis guidance after invalid frames or 800 ms without a fresh reading.
- Rebind all CameraX use cases on display rotation, including same-size 180° changes, and measure roll in display coordinates.
- Added a confirmed Delete action in Darkroom that removes a photo and its saved edit history from the local library.
- Added Library batch export for selected photos with full-resolution output and saved edits.
- Added non-destructive crop presets with drag positioning and full-resolution export.
- Added a live Darkroom luminance histogram with near-black and near-white percentages.
- Added a Save edits action that restores a photo’s undo and redo history when reopened.
- Preserve unreadable Darkroom edit files and offer original export or confirmed replacement.
- Prompt before leaving unsaved Darkroom edits and associate save and share status with the edits that were actually saved or exported.
- Show saved Darkroom edits in Library thumbnails and use the original framing if the edit file fails to load.
- Added Darkroom undo and redo for film selection, adjustments, rotation, and reset. A slider gesture creates one history step.

## 0.3.0

- Added offline frontal face framing in Portrait mode, with confidence and stability checks before showing aim cues.

## 0.2.0

- Added seven shooting modes with framing, level, and lighting guidance.
- Stabilized directional cues by requiring consecutive detail measurements.
- Added a manual reference-photo overlay with adjustable opacity.

## 0.1.0

- Added a native CameraX viewfinder with tap-to-focus, lens switching, JPEG capture, and camera capability handling.
- Added real-time level, brightness, and framing cues.
- Added five live film looks, a local photo library with import, and a non-destructive darkroom with full-resolution JPEG export.
- Added composition tests and GitHub Actions APK builds.
