package com.aperture.photo.camera

/** Offsets are fractions of the current viewfinder width and height. */
@ConsistentCopyVisibility
internal data class ReferenceAlignment private constructor(val x: Float, val y: Float, val scale: Float) {
    fun transform(pivotX: Float, pivotY: Float, panX: Float, panY: Float, zoom: Float): ReferenceAlignment {
        if (!pivotX.isFinite() || !pivotY.isFinite() || !panX.isFinite() || !panY.isFinite() ||
            !zoom.isFinite() || zoom <= 0f) return this
        val nextScale = (scale * zoom).coerceIn(ScaleRange)
        val ratio = nextScale / scale
        return of(
            pivotX + (x - pivotX) * ratio + panX,
            pivotY + (y - pivotY) * ratio + panY,
            nextScale
        )
    }

    fun bounds(imageWidth: Int, imageHeight: Int, viewWidth: Float, viewHeight: Float): ReferenceBounds {
        val cover = maxOf(viewWidth / imageWidth, viewHeight / imageHeight) * scale
        val width = imageWidth * cover
        val height = imageHeight * cover
        return ReferenceBounds(viewWidth * (.5f + x) - width / 2, viewHeight * (.5f + y) - height / 2,
            width, height)
    }

    companion object {
        val OffsetRange = -0.5f..0.5f
        val ScaleRange = .5f..3f
        val Centered = ReferenceAlignment(0f, 0f, 1f)

        fun of(x: Float, y: Float, scale: Float) = ReferenceAlignment(
            x.coerceIn(OffsetRange), y.coerceIn(OffsetRange), scale.coerceIn(ScaleRange)
        )
    }
}

internal data class ReferenceBounds(val left: Float, val top: Float, val width: Float, val height: Float)
