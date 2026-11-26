package com.aperture.photo.imaging

import kotlin.math.roundToInt

internal fun luma(red: Int, green: Int, blue: Int): Int =
    (2126 * red + 7152 * green + 722 * blue + 5000) / 10000

/** Maps image luma to a blend toward white (positive) or black (negative). */
internal class ToneAdjustments(shadows: Float, highlights: Float) {
    private val blendByLuma = IntArray(256) { value ->
        val tone = value / 255f
        val inverse = 1f - tone
        val shadowDelta = shadows * inverse * inverse * if (shadows >= 0f) .30f * inverse else .75f * tone
        val highlightDelta = highlights * tone * tone * if (highlights >= 0f) .75f * inverse else .30f * tone
        val shift = shadowDelta + highlightDelta
        val available = if (shift > 0f) inverse else tone
        if (available <= 0f) 0 else (shift / available * BlendScale).roundToInt().coerceIn(-BlendScale, BlendScale)
    }

    fun apply(argb: Int): Int {
        if (argb ushr 24 == 0) return argb
        val red = (argb ushr 16) and 255
        val green = (argb ushr 8) and 255
        val blue = argb and 255
        val blend = blendByLuma[luma(red, green, blue)]
        if (blend == 0) return argb
        val target = if (blend > 0) 255 else 0
        val weight = kotlin.math.abs(blend)
        return (argb and -0x1000000) or
            (mix(red, target, weight) shl 16) or
            (mix(green, target, weight) shl 8) or mix(blue, target, weight)
    }

    private fun mix(channel: Int, target: Int, weight: Int): Int =
        (channel * (BlendScale - weight) + target * weight + BlendScale / 2) ushr 16

    private companion object { const val BlendScale = 1 shl 16 }
}
