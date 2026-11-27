package com.aperture.photo.camera

internal data class ExposureCompensation(
    val index: Int,
    val minIndex: Int,
    val maxIndex: Int,
    val stepEv: Float
) {
    val ev: Float get() = index * stepEv
    val supported: Boolean get() = minIndex < maxIndex && stepEv > 0f
    fun clamp(requestedIndex: Int): Int = requestedIndex.coerceIn(minIndex, maxIndex)
}
