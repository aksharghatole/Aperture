package com.aperture.photo.camera

internal data class CameraZoom(val ratio: Float = 1f, val minRatio: Float = 1f, val maxRatio: Float = 1f) {
    fun clamp(requested: Float): Float = requested.coerceIn(minRatio, maxRatio)

    fun presets(): List<Float> = listOf(1f, 2f, 3f, 5f).filter { it in minRatio..maxRatio }
}
