package com.aperture.photo.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

internal class FrameAnalyzer(
    private val mirrored: Boolean,
    private val shootingMode: () -> ShootingMode,
    private val onScene: (ShootingMode, SceneReading?) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastFrame = 0L
    private var previousMode: ShootingMode? = null
    private val samples = IntArray(Size * Size)
    private val detailTracker = DetailTracker()
    private val faceTracker = DetailTracker()
    private val portraitDetector by lazy { PortraitDetector() }

    override fun analyze(image: ImageProxy) {
        val mode = shootingMode()
        try {
            val now = SystemClock.elapsedRealtime()
            val interval = if (mode == ShootingMode.PORTRAIT) 350 else 200
            if (mode == previousMode && now - lastFrame < interval) return
            if (now - lastFrame >= SceneReading.MaxAgeMillis || mode != previousMode) {
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
                invalidate(mode)
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
            val tracked = scene.copy(focus = detailTracker.update(scene.focus), portraitEyes = eyes)
            onScene(mode, SceneReading(tracked, now))
        } catch (_: RuntimeException) {
            invalidate(mode)
        } finally {
            image.close()
        }
    }

    private fun invalidate(mode: ShootingMode) {
        detailTracker.update(null)
        faceTracker.update(null)
        onScene(mode, null)
    }

    private companion object { const val Size = 40 }
}
