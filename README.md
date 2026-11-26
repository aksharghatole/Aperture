# Aperture

Aperture is an offline photography app for Android 10 and newer. It includes a CameraX viewfinder, composition and level guidance, live film looks, an on-device library, and a basic darkroom.

## Build

Use JDK 17 or 21, Android SDK Platform 35, and Build Tools 35.0.0. Set `ANDROID_HOME` to the SDK path or create an Android Studio `local.properties` file.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs the build checks and uploads an installable debug APK. With a device or emulator connected, run `./gradlew connectedDebugAndroidTest` to verify that a full-resolution export preserves the original. Subsequent milestones are in [ROADMAP.md](ROADMAP.md).

## Camera and darkroom

1. Open Aperture, enable the camera, and select Portrait, Landscape, Street, Architecture, Food, Macro, or Night. Each mode sets a framing target or level tolerance.
2. Tap the preview to focus, choose a film look, and take a photo. Flip is available on devices with both front and rear cameras.
3. Open Library, select a capture, or import an image. Darkroom adjusts exposure, shadows, highlights, contrast, saturation, warmth, vignette, and rotation.
4. Export a full-resolution JPEG to `Pictures/Aperture`. The captured or imported original stays in the app library. Darkroom keeps up to 40 edit states for undo and redo. Use **Save edits** to restore that history when reopening the photo. Toggle **Histogram** to see the edited preview’s luminance distribution and sampled near-black and near-white percentages. Use **Crop** to select an aspect ratio and drag the frame into position before applying. Open **Curves** to map black, midtone, and white levels for RGB or an individual channel. Returning to Library shows the saved version of each photo.
5. To export several photos, tap **Select** in Library, pick the photos, and tap **Export**. Batch export uses each photo’s saved edits. Failed and unprocessed photos stay selected for retry.

Use **Reference** to pick an image and adjust its opacity. The overlay is center-cropped to the viewfinder and stays in the camera session. It guides framing visually and is excluded from saved photos.

Portrait mode uses Android’s offline frontal face detector. It shows an eye-position cue after two nearby detections of a single confident face. Groups, profiles, small faces, and devices without working detection can use the upper guide manually.

The camera assistant uses frame luminance, a heuristic for concentrated image detail, and available gravity or accelerometer readings. Directional cues describe where to aim the camera within the frame. Performance and capture resolution depend on the device.

For a device smoke test, grant and deny camera permission, verify every film look and a capture in the preview, inspect it in Library, change the edit settings, and confirm the exported JPEG in system photos. Test switching lenses where supported and confirm that flat or low-detail scenes suppress unsupported guidance.
