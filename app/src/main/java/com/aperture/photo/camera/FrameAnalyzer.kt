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
            val frame = LumaFrame(image)
            if (!frame.isValid) {
                invalidate(mode)
                return
            }
            for (y in 0 until Size) {
                for (x in 0 until Size) {
                    val column = if (mirrored) Size - 1 - x else x
                    samples[y * Size + x] = frame.sample(column, y, Size, Size)
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

internal class LumaFrame(image: ImageProxy) {
    private val crop = image.cropRect
    private val plane = image.planes[0]
    private val buffer = plane.buffer
    private val start = buffer.position()
    private val rotation = image.imageInfo.rotationDegrees
    private val transposed = rotation == 90 || rotation == 270
    val width = if (transposed) crop.height() else crop.width()
    val height = if (transposed) crop.width() else crop.height()

    val isValid = width > 0 && height > 0 && crop.left >= 0 && crop.top >= 0 &&
        start + (crop.bottom - 1) * plane.rowStride + (crop.right - 1) * plane.pixelStride < buffer.limit()

    fun sample(x: Int, y: Int, sampleWidth: Int, sampleHeight: Int): Int {
        val u = (x + .5f) / sampleWidth
        val v = (y + .5f) / sampleHeight
        val sourceX = when (rotation) { 90 -> v; 180 -> 1f - u; 270 -> 1f - v; else -> u }
        val sourceY = when (rotation) { 90 -> 1f - u; 180 -> 1f - v; 270 -> u; else -> v }
        val row = crop.top + (sourceY * crop.height()).toInt()
        val column = crop.left + (sourceX * crop.width()).toInt()
        val offset = start + row * plane.rowStride + column * plane.pixelStride
        return buffer.get(offset).toInt() and 255
    }
}
