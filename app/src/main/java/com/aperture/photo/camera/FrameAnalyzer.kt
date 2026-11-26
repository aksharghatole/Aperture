package com.aperture.photo.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class FrameAnalyzer(
    private val mirrored: Boolean,
    private val shootingMode: () -> ShootingMode,
    private val onScene: (ShootingMode, Scene) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastFrame = 0L
    private var previousMode: ShootingMode? = null
    private val samples = IntArray(Size * Size)
    private val detailTracker = DetailTracker()
    private val faceTracker = DetailTracker()
    private val portraitDetector by lazy { PortraitDetector() }

    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            val mode = shootingMode()
            val interval = if (mode == ShootingMode.PORTRAIT) 350 else 200
            if (mode == previousMode && now - lastFrame < interval) return
            if (now - lastFrame > 800 || mode != previousMode) {
                detailTracker.update(null)
                faceTracker.update(null)
            }
            lastFrame = now
            previousMode = mode
            val crop = image.cropRect
            val plane = image.planes.firstOrNull()
            val frame = plane?.let {
                LumaFrame(it.buffer, image.width, image.height, it.rowStride, it.pixelStride,
                    crop.left, crop.top, crop.right, crop.bottom, image.imageInfo.rotationDegrees)
            }
            if (frame == null || !frame.isValid) {
                detailTracker.update(null)
                faceTracker.update(null)
                return
            }
            for (y in 0 until Size) {
                for (x in 0 until Size) {
                    samples[y * Size + x] = frame.sample(x, y, Size, Size, mirrored)
                }
            }
            val scene = Composition.measure(samples, Size, Size)
            val eyes = if (mode == ShootingMode.PORTRAIT) {
                faceTracker.update(portraitDetector.detect(frame, mirrored))
            } else null
            onScene(mode, scene.copy(focus = detailTracker.update(scene.focus), portraitEyes = eyes))
        } finally {
            image.close()
        }
    }

    private companion object { const val Size = 40 }
}
