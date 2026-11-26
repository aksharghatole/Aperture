package com.aperture.photo.camera

internal object PortraitFraming {
    fun eyePoint(
        faceCount: Int, confidence: Float, x: Float, y: Float, eyeSpan: Float, mirrored: Boolean
    ): FocusRegion? {
        if (faceCount != 1 || confidence !in .65f..1f || x !in 0f..1f || y !in 0f..1f ||
            eyeSpan !in 0.035f..0.65f) return null
        if (x - eyeSpan / 2f < 0f || x + eyeSpan / 2f > 1f) return null
        return FocusRegion(if (mirrored) 1f - x else x, y)
    }
}
