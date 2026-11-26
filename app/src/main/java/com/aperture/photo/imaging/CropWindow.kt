package com.aperture.photo.imaging

import kotlin.math.roundToInt

/** Crop coordinates are fractions of the image after rotation. */
data class CropWindow(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(left < right && top < bottom)
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun move(dx: Float, dy: Float): CropWindow {
        val deltaX = dx.coerceIn(-left, 1f - right)
        val deltaY = dy.coerceIn(-top, 1f - bottom)
        return CropWindow(left + deltaX, top + deltaY, right + deltaX, bottom + deltaY)
    }

    fun rotateClockwise(): CropWindow = CropWindow(1f - bottom, left, 1f - top, right)

    internal fun pixels(imageWidth: Int, imageHeight: Int): CropPixels {
        val x = (left * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
        val y = (top * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
        val rightEdge = (right * imageWidth).roundToInt().coerceIn(x + 1, imageWidth)
        val bottomEdge = (bottom * imageHeight).roundToInt().coerceIn(y + 1, imageHeight)
        return CropPixels(x, y, rightEdge - x, bottomEdge - y)
    }

    companion object {
        val Full = CropWindow(0f, 0f, 1f, 1f)

        fun centered(imageAspect: Float, targetAspect: Float): CropWindow {
            val width = (targetAspect / imageAspect).coerceAtMost(1f)
            val height = (imageAspect / targetAspect).coerceAtMost(1f)
            return CropWindow((1f - width) / 2f, (1f - height) / 2f, (1f + width) / 2f, (1f + height) / 2f)
        }
    }
}

internal data class CropPixels(val left: Int, val top: Int, val width: Int, val height: Int)
