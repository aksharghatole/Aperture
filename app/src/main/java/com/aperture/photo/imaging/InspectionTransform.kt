package com.aperture.photo.imaging

/** Pan is measured in fractions of the visible viewport. */
internal data class InspectionTransform(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun update(
        viewportAspect: Float,
        imageAspect: Float,
        zoomFactor: Float,
        pivotX: Float = 0f,
        pivotY: Float = 0f,
        panX: Float = 0f,
        panY: Float = 0f
    ): InspectionTransform {
        val nextZoom = (zoom * zoomFactor).coerceIn(1f, MaxZoom)
        val ratio = nextZoom / zoom
        val fitWidth = (imageAspect / viewportAspect).coerceAtMost(1f)
        val fitHeight = (viewportAspect / imageAspect).coerceAtMost(1f)
        val limitX = ((fitWidth * nextZoom - 1f) / 2f).coerceAtLeast(0f)
        val limitY = ((fitHeight * nextZoom - 1f) / 2f).coerceAtLeast(0f)
        return InspectionTransform(
            nextZoom,
            (pivotX + (x - pivotX) * ratio + panX).coerceIn(-limitX, limitX),
            (pivotY + (y - pivotY) * ratio + panY).coerceIn(-limitY, limitY)
        )
    }

    companion object {
        const val MaxZoom = 4f
    }
}
