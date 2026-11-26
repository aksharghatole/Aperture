# Changelog

## Unreleased

- Added Master RGB and individual red, green, and blue curves with live previews.
- Added a confirmed Delete action in Darkroom that removes a photo and its saved edit history from the local library.
- Added Library batch export for selected photos with full-resolution output and saved edits.
- Added non-destructive crop presets with drag positioning and full-resolution export.
- Added a live Darkroom luminance histogram with near-black and near-white percentages.
- Added a Save edits action that restores a photo’s undo and redo history when reopened.
- Preserve unreadable Darkroom edit files and offer original export or confirmed replacement.
- Prompt before leaving unsaved Darkroom edits and associate save and share status with the edits that were actually saved or exported.
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
