# Aperture

[Build and install](#build-and-install) · [Camera and darkroom](#camera-and-darkroom) · [Image format policy](#imported-images-and-jpeg-transparency) · [Device verification](#device-verification) · [Actions](https://github.com/aksharghatole/Aperture/actions)

Aperture is an offline photography app for Android 10 and newer. It includes a CameraX viewfinder, composition and level guidance, live film looks, an on-device library, and a basic darkroom.

## Build and install

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
4. Export a full-resolution JPEG to `Pictures/Aperture`. The captured or imported original stays in the app library. Darkroom keeps up to 40 edit states for undo and redo. Use **Save edits** to restore that history when reopening the photo. Toggle **Histogram** to see the edited preview’s luminance distribution and sampled near-black and near-white percentages. Use **Crop** to select an aspect ratio and drag the frame into position before applying. Open **Curves** to map black, midtone, and white levels for RGB or an individual channel. If saved edits cannot be opened, Darkroom preserves the edit file and offers an original export. Darkroom asks before leaving an unsaved edit history. Returning to Library shows the saved version of each photo.
5. To export several photos, tap **Select** in Library, pick the photos, and tap **Export**. Batch export uses each photo’s saved edits. Failed and unprocessed photos stay selected for retry.

Use **Reference** to pick an image and adjust its opacity, scale, and position. Choose **Align in viewfinder** to drag or pinch the overlay over the live preview. Tap **Done** to resume normal focus and zoom gestures. The overlay and its settings persist through rotation and trips to Library during the camera session. Captures contain only the camera image.

Open **Presets** in Darkroom to save the current film, color, tone, curves, vignette, and grain as a named look. Apply a saved look to another photo as one undoable edit; crop and rotation are never copied. Rename or delete up to 50 named presets separately from photos and saved edit histories. Presets remain on-device across restarts. If the preset index is damaged or unreadable, Aperture preserves it and offers Retry instead of overwriting it.

In Darkroom, toggle **Before** to compare the current edits with the original tones or the film look chosen at capture. Both views use the current crop and rotation. Saving and exporting use the current edits in either view.

Pinch, double-tap, or use the zoom buttons to inspect a photo in Darkroom. Drag to pan while zoomed. **Fit** restores the full edited frame. Before and After share the same inspection position. Changing the crop or rotation returns to Fit. Inspection magnifies the preview, which is decoded at up to 1,400 pixels on its longest edge. Export uses the original photo.

Portrait mode uses Android’s offline frontal face detector. A single face shows an eye marker after two consistent readings. Groups of two to four confidently detected faces show an approximate framing box after three consistent readings. Profiles, small faces, overlaps, partial faces, and groups at the frame edge can be missed or withheld. Use the upper guide and check that everyone is visible.

The camera assistant uses frame luminance, a heuristic for concentrated image detail, and available gravity or accelerometer readings. Directional cues describe where to aim the camera within the frame. Performance and capture resolution depend on the device. If camera analysis stalls or returns an invalid frame, Aperture drops its scene cues and keeps manual guides available.

## Imported images and JPEG transparency

Use **Import** in Library to pick up to 50 images at a time. A single successful import opens in Darkroom. For a batch, Library shows the result for each processed file. **Stop** finishes the current file and skips the rest. Imports copy up to 256 MiB per file and keep a free storage reserve.

Aperture uses Android’s image decoder for still photos, including JPEG, PNG, and WebP. It rejects animations the decoder recognizes, such as animated GIF and WebP. Extract a still frame before importing one. Imports preserve the source file bytes. JPEG exports use a white background in transparent areas, including batch exports.

## Device verification

For group portraits, frame two to four people in Portrait mode with the rear and front cameras. Check that the box follows the visible group in portrait and landscape, and that the direction agrees with the mirrored preview. Have someone turn away, move into the frame edge, or step out. Check that the box disappears until the detector has enough consistent readings. Verify the single face marker with one person.

For a device smoke test, grant and deny camera permission, verify every film look and a capture in the preview, inspect it in Library, change the edit settings, and confirm the exported JPEG in system photos. Test switching lenses where supported and confirm that flat or low-detail scenes suppress unsupported guidance.

For presets: save a look from a cropped photo, close and reopen the app, apply it to a different rotated/cropped photo, and check Undo/Redo. Rename and delete the preset, then confirm the saved photo edits and original are unchanged.

For reference alignment:

- Select a reference with recognizable edges, adjust its opacity, and drag and pinch it in the viewfinder. Exit alignment and confirm that tap focus and camera pinch zoom respond again.
- Rotate to landscape and back, then open Library and return. Check that the chosen image, opacity, and relative alignment are retained.
- With the overlay visible, take a photo and export it from Darkroom. Check that both images contain only the captured scene. Reset the alignment, then remove the reference and verify that it stays cleared after navigating to Library and back.

Aperture checks for interrupted work on startup. It removes its recognized temporary files and orphan edit files after 24 hours. It also checks for old pending JPEG exports in Pictures/Aperture and only removes rows with verified app ownership and pending status.

CameraX refreshes preview, analysis, and capture when the display rotates, even if the viewfinder keeps the same dimensions. Camera luma fixtures cover the four rotations, front-camera mirroring, and cropped, padded camera buffers. Preview, tap-to-focus, and capture alignment still need verification on physical cameras across those rotations.

Darkroom checks export dimensions before allocating full-resolution buffers. If the estimated memory exceeds the available budget, it offers a smaller JPEG and waits for confirmation. Batch export keeps photos that exceed the budget selected so they can be opened in Darkroom individually. At 12, 48, and 108 megapixels, the uncropped buffer estimate is about 153, 565, and 1,252 MiB respectively, including a 16 MiB allowance. These are estimates. Actual peak memory still needs measurement on a constrained Android device.

Captures and exports report their results after activity recreation. Aperture blocks navigation during a capture or a single export. In a batch, **Stop** or Back lets the current photo finish and leaves the rest selected. Camera interruptions can cancel an in-flight capture. A terminated app process uses the startup recovery rules described above.


Darkroom grain is generated locally with a fixed-seed deterministic pattern. Its intensity defaults to zero, and grain size is scaled to the rendered image dimensions so preview and full-resolution exports keep the same grain character. Saved edit history written by this version uses a new versioned format and older versions load with grain disabled.
