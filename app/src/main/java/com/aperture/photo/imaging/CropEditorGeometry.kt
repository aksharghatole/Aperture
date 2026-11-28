package com.aperture.photo.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal enum class CropHandle(val x: Int, val y: Int) {
    MOVE(0, 0), LEFT(-1, 0), RIGHT(1, 0), TOP(0, -1), BOTTOM(0, 1),
    TOP_LEFT(-1, -1), TOP_RIGHT(1, -1), BOTTOM_LEFT(-1, 1), BOTTOM_RIGHT(1, 1)
}

/** Geometry uses oriented original pixels; the preview is only a normalized coordinate surface. */
internal class CropEditorGeometry(private val image: ImageSize) {
    val aspect: Float = image.width.toFloat() / image.height
    val minimumWidth = minimum(image.width)
    val minimumHeight = minimum(image.height)

    private fun minimum(pixels: Int): Float = max(.05f, 24f / pixels).coerceAtMost(1f)

    fun preset(ratio: Float): CropWindow {
        val (width, height) = lockedExtent(ratio)
        return centeredAt(.5f, .5f, width, height)
    }

    private fun lockedExtent(ratio: Float): Pair<Float, Float> {
        require(ratio.isFinite() && ratio > 0f)
        val normalized = ratio / aspect
        return min(1f, normalized) to min(1f, 1f / normalized)
    }

    fun minimumScale(ratio: Float): Float {
        val (width, height) = lockedExtent(ratio)
        val shortest = min(width * image.width, height * image.height)
        return max(.05f, 24f / shortest).coerceAtMost(1f)
    }

    fun scale(crop: CropWindow, ratio: Float): Float {
        val (width, height) = lockedExtent(ratio)
        return min(crop.width / width, crop.height / height).coerceIn(0f, 1f)
    }

    fun resizeCentered(crop: CropWindow, width: Float = crop.width, height: Float = crop.height): CropWindow =
        centeredAt((crop.left + crop.right) / 2, (crop.top + crop.bottom) / 2,
            width.coerceIn(minimumWidth, 1f), height.coerceIn(minimumHeight, 1f))

    fun scaleCentered(crop: CropWindow, ratio: Float, scale: Float): CropWindow {
        val (width, height) = lockedExtent(ratio)
        val amount = scale.coerceIn(minimumScale(ratio), 1f)
        return centeredAt((crop.left + crop.right) / 2, (crop.top + crop.bottom) / 2,
            width * amount, height * amount)
    }

    fun horizontalPosition(crop: CropWindow): Float =
        if (crop.width >= 1f) 0f else (crop.left / (1f - crop.width)).coerceIn(0f, 1f)

    fun verticalPosition(crop: CropWindow): Float =
        if (crop.height >= 1f) 0f else (crop.top / (1f - crop.height)).coerceIn(0f, 1f)

    fun position(crop: CropWindow, horizontal: Float = horizontalPosition(crop),
                 vertical: Float = verticalPosition(crop)): CropWindow =
        at((1f - crop.width) * horizontal.coerceIn(0f, 1f),
            (1f - crop.height) * vertical.coerceIn(0f, 1f), crop.width, crop.height)

    fun drag(crop: CropWindow, handle: CropHandle, dx: Float, dy: Float, ratio: Float?): CropWindow {
        if (!dx.isFinite() || !dy.isFinite()) return crop
        if (handle == CropHandle.MOVE) return at(crop.left + dx, crop.top + dy, crop.width, crop.height)
        return if (ratio == null) freeDrag(crop, handle, dx, dy)
            else lockedDrag(crop, handle, dx, dy, ratio)
    }

    private fun freeDrag(crop: CropWindow, handle: CropHandle, dx: Float, dy: Float): CropWindow {
        // The edge opposite the drag stays fixed. Legacy smaller crops can still be enlarged.
        val left = if (handle.x < 0) (crop.left + dx).coerceIn(0f, crop.right - min(minimumWidth, crop.right)) else crop.left
        val right = if (handle.x > 0) (crop.right + dx).coerceIn(crop.left + min(minimumWidth, 1f - crop.left), 1f) else crop.right
        val top = if (handle.y < 0) (crop.top + dy).coerceIn(0f, crop.bottom - min(minimumHeight, crop.bottom)) else crop.top
        val bottom = if (handle.y > 0) (crop.bottom + dy).coerceIn(crop.top + min(minimumHeight, 1f - crop.top), 1f) else crop.bottom
        return CropWindow(left, top, right, bottom)
    }

    private fun lockedDrag(crop: CropWindow, handle: CropHandle, dx: Float, dy: Float, ratio: Float): CropWindow {
        val (maxWidth, maxHeight) = lockedExtent(ratio)
        val anchorX = when (handle.x) { -1 -> crop.right; 1 -> crop.left; else -> (crop.left + crop.right) / 2 }
        val anchorY = when (handle.y) { -1 -> crop.bottom; 1 -> crop.top; else -> (crop.top + crop.bottom) / 2 }
        fun available(anchor: Float, direction: Int): Float = when (direction) {
            -1 -> anchor
            1 -> 1f - anchor
            else -> 2f * min(anchor, 1f - anchor)
        }
        val maximum = min(available(anchorX, handle.x) / maxWidth,
            available(anchorY, handle.y) / maxHeight).coerceAtMost(1f)
        if (maximum <= 0f) return crop
        val deltaScale = when {
            handle.x == 0 -> dy * handle.y / maxHeight
            handle.y == 0 -> dx * handle.x / maxWidth
            else -> {
                // Project a diagonal drag onto the locked aspect in original pixel space.
                val widthPixels = maxWidth.toDouble() * image.width
                val heightPixels = maxHeight.toDouble() * image.height
                ((dx.toDouble() * handle.x * image.width * widthPixels +
                    dy.toDouble() * handle.y * image.height * heightPixels) /
                    (widthPixels * widthPixels + heightPixels * heightPixels)).toFloat()
            }
        }
        val amount = (scale(crop, ratio) + deltaScale)
            .coerceIn(min(minimumScale(ratio), maximum), maximum)
        val width = maxWidth * amount
        val height = maxHeight * amount
        fun origin(anchor: Float, direction: Int, extent: Float) = when (direction) {
            -1 -> anchor - extent
            1 -> anchor
            else -> anchor - extent / 2
        }
        return at(origin(anchorX, handle.x, width), origin(anchorY, handle.y, height), width, height)
    }

    private fun centeredAt(x: Float, y: Float, width: Float, height: Float): CropWindow =
        at(x - width / 2f, y - height / 2f, width, height)

    private fun at(x: Float, y: Float, width: Float, height: Float): CropWindow {
        val left = x.coerceIn(0f, (1f - width).coerceAtLeast(0f)).coerceAtMost(Math.nextDown(1f))
        val top = y.coerceIn(0f, (1f - height).coerceAtLeast(0f)).coerceAtMost(Math.nextDown(1f))
        // A legacy sub-pixel crop may be narrower than Float precision at its new position.
        val right = (left + width).coerceIn(Math.nextUp(left), 1f)
        val bottom = (top + height).coerceIn(Math.nextUp(top), 1f)
        return CropWindow(left, top, right, bottom)
    }
}

/** Hit-test in displayed pixels so corner/edge handles work at any preview size. */
internal fun cropHandleAt(
    crop: CropWindow, x: Float, y: Float, width: Float, height: Float, tolerance: Float
): CropHandle? {
    if (width <= 0f || height <= 0f || tolerance <= 0f) return null
    val left = crop.left * width
    val right = crop.right * width
    val top = crop.top * height
    val bottom = crop.bottom * height
    if (x < left - tolerance || x > right + tolerance || y < top - tolerance || y > bottom + tolerance) return null
    fun direction(value: Float, low: Float, high: Float): Int {
        val edgeTolerance = min(tolerance, (high - low) / 3f)
        return when {
            abs(value - low) <= edgeTolerance -> -1
            abs(value - high) <= edgeTolerance -> 1
            else -> 0
        }
    }
    val horizontal = direction(x, left, right)
    val vertical = direction(y, top, bottom)
    if (horizontal == 0 && vertical == 0 && (x !in left..right || y !in top..bottom)) return null
    return CropHandle.entries.firstOrNull { it.x == horizontal && it.y == vertical }
}
