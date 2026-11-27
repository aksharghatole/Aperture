package com.aperture.photo.camera

internal object VolumeShutterPolicy {
    fun shouldCapture(
        enabled: Boolean,
        cameraReady: Boolean,
        permissionGranted: Boolean,
        captureBusy: Boolean,
        hasSession: Boolean,
        repeatCount: Int
    ): Boolean = enabled && cameraReady && permissionGranted && !captureBusy && hasSession && repeatCount == 0
}
