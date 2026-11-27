package com.aperture.photo.camera

/** Bounded statistics from sampled preview luma, never a RAW sensor clipping measurement. */
data class ExposureDiagnostics(
    val histogram: List<Int>,
    val darkFraction: Float,
    val brightFraction: Float
) {
    val darkWarning: Boolean get() = darkFraction >= WarningFraction
    val brightWarning: Boolean get() = brightFraction >= WarningFraction

    companion object {
        const val HistogramBins = 32
        const val WarningFraction = .05f
        const val DarkThreshold = 8
        const val BrightThreshold = 247

        fun from(samples: IntArray): ExposureDiagnostics {
            require(samples.isNotEmpty())
            val bins = IntArray(HistogramBins)
            var dark = 0
            var bright = 0
            samples.forEach { sample ->
                val value = sample.coerceIn(0, 255)
                bins[value * HistogramBins / 256]++
                if (value <= DarkThreshold) dark++
                if (value >= BrightThreshold) bright++
            }
            return ExposureDiagnostics(bins.toList(), dark.toFloat() / samples.size,
                bright.toFloat() / samples.size)
        }
    }
}
