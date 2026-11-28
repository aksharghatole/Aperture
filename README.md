# Aperture

Aperture is an offline photography app for Android. Use its camera guides and film looks while shooting, then make edits in the on-device darkroom.

- Frame a shot with composition and level guides, guided exposure and a shooting coach for seven photography modes.
- Browse your photos and adjust color, crop, curves, and more.
- Keep the original and export an edited JPEG when you’re ready.

Camera features vary by phone, so check them on your device before relying on a guide.

### Build it

You’ll need JDK 17 or 21 and Android SDK Platform 35. Build and check the app with:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions also builds a debug APK for testing. Versioned, signed APK releases can be published from GitHub Releases; see the [release setup](README-REFERENCE.md#installing-and-updating-release-apks). See [Actions](https://github.com/aksharghatole/Aperture/actions), the [roadmap](ROADMAP.md), or the [project reference](README-REFERENCE.md) for more detail.
