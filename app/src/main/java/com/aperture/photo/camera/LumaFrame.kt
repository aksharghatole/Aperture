package com.aperture.photo.camera

import java.nio.ByteBuffer

/** Reads the cropped camera luma plane in upright viewfinder coordinates. */
internal class LumaFrame(
    buffer: ByteBuffer,
    imageWidth: Int,
    imageHeight: Int,
    private val rowStride: Int,
    private val pixelStride: Int,
    private val left: Int,
    private val top: Int,
    right: Int,
    bottom: Int,
    private val rotation: Int
) {
    private val plane = buffer.duplicate()
    private val start = plane.position()
    private val cropWidth = right - left
    private val cropHeight = bottom - top
    private val transposed = rotation == 90 || rotation == 270
    val width = if (transposed) cropHeight else cropWidth
    val height = if (transposed) cropWidth else cropHeight

    val isValid = left >= 0 && top >= 0 && right in 1..imageWidth && bottom in 1..imageHeight &&
        left < right && top < bottom && rowStride > 0 && pixelStride > 0 &&
        (rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270) &&
        (right - 1L) * pixelStride < rowStride &&
        start + (bottom - 1L) * rowStride + (right - 1L) * pixelStride < plane.limit()

    fun sample(x: Int, y: Int, sampleWidth: Int, sampleHeight: Int, mirrored: Boolean = false): Int {
        check(isValid)
        require(x in 0 until sampleWidth && y in 0 until sampleHeight)
        val column = if (mirrored) sampleWidth - 1 - x else x
        val u = (column + .5) / sampleWidth
        val v = (y + .5) / sampleHeight
        val sourceX = when (rotation) {
            90 -> v
            180 -> 1.0 - u
            270 -> 1.0 - v
            else -> u
        }
        val sourceY = when (rotation) {
            90 -> 1.0 - u
            180 -> 1.0 - v
            270 -> u
            else -> v
        }
        val row = top + (sourceY * cropHeight).toInt()
        val col = left + (sourceX * cropWidth).toInt()
        return plane.get(start + row * rowStride + col * pixelStride).toInt() and 255
    }
}
