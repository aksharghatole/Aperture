package com.aperture.photo.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class FrameAnalyzer(private val mirrored: Boolean, private val onScene: (Scene) -> Unit) :
    ImageAnalysis.Analyzer {
    private var lastFrame = 0L
    private val samples = IntArray(Size * Size)
    private val detailTracker = DetailTracker()

    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrame < 200) return
            if (now - lastFrame > 800) detailTracker.update(null)
            lastFrame = now
            val crop = image.cropRect
            val plane = image.planes[0]
            val buffer = plane.buffer
            val start = buffer.position()
            val rotation = image.imageInfo.rotationDegrees
            for (y in 0 until Size) {
                for (x in 0 until Size) {
                    val u = ((if (mirrored) Size - 1 - x else x) + .5f) / Size
                    val v = (y + .5f) / Size
                    val (sourceX, sourceY) = when (rotation) {
                        90 -> v to 1f - u
                        180 -> 1f - u to 1f - v
                        270 -> 1f - v to u
                        else -> u to v
                    }
                    val row = crop.top + (sourceY * crop.height()).toInt()
                    val column = crop.left + (sourceX * crop.width()).toInt()
                    val offset = start + row * plane.rowStride + column * plane.pixelStride
                    if (offset >= buffer.limit()) return
                    samples[y * Size + x] = buffer.get(offset).toInt() and 255
                }
            }
            val scene = Composition.measure(samples, Size, Size)
            onScene(scene.copy(focus = detailTracker.update(scene.focus)))
        } finally {
            image.close()
        }
    }

    private companion object { const val Size = 40 }
}
