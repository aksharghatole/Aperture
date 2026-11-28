package com.aperture.photo.camera

import android.graphics.Bitmap
import android.graphics.PointF
import android.media.FaceDetector

/** Runs the device's offline frontal face detector on downsampled, upright luma. */
internal class PortraitDetector {
    // The extra slot lets us suppress cues when the detector reaches capacity.
    private val faces = arrayOfNulls<FaceDetector.Face>(PortraitFraming.MaxGroupSize + 1)
    private val midpoint = PointF()
    private var detector: FaceDetector? = null
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private var failed = false

    fun detect(frame: LumaFrame, mirrored: Boolean): List<DetectedEyes>? {
        if (failed) return null
        val longest = maxOf(frame.width, frame.height)
        val width = (frame.width * 320 / longest).coerceAtLeast(2) and -2
        val height = (frame.height * 320 / longest).coerceAtLeast(2)
        if (width < 32 || height < 32) return null
        return try {
            val existing = bitmap
            val input = if (existing != null && existing.width == width && existing.height == height) {
                existing
            } else {
                existing?.recycle()
                detector = FaceDetector(width, height, faces.size)
                pixels = IntArray(width * height)
                Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also { bitmap = it }
            }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val luma = frame.sample(x, y, width, height)
                    pixels[y * width + x] = 0xff000000.toInt() or (luma * 0x010101)
                }
            }
            input.setPixels(pixels, 0, width, 0, 0, width, height)
            val count = checkNotNull(detector).findFaces(input, faces)
            if (count !in 1..PortraitFraming.MaxGroupSize) return null
            val detected = ArrayList<DetectedEyes>(count)
            for (index in 0 until count) {
                val face = faces[index] ?: return null
                face.getMidPoint(midpoint)
                val span = face.eyesDistance()
                detected.add(DetectedEyes(face.confidence(), midpoint.x / width, midpoint.y / height,
                    span / width, span / height))
            }
            PortraitFraming.validate(detected, mirrored)
        } catch (_: RuntimeException) {
            failed = true
            null
        } catch (_: LinkageError) {
            failed = true
            null
        }
    }
}
