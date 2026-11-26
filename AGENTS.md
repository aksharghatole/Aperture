# Aperture development

- Build from the existing checkout. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` before closing a milestone.
- Use Kotlin, Jetpack Compose, CameraX, and Android 10+ APIs. Keep the camera, imaging, storage, and UI code separated.
- Keep the default workflow offline. Gate every camera capability and compose guidance on observed evidence. Keep camera transforms and displayed coordinates aligned.
- Preserve original images. Test full-resolution exports and track known device restrictions in the README.
- Add regression tests for image math and guidance. Check actual preview, capture, import, and export on an Android device when available.
- Update ROADMAP.md and CHANGELOG.md as milestones change.
