package com.aperture.photo.imaging

internal class LuminanceHistogram private constructor(
    val bins: IntArray,
    val sampleCount: Int,
    val nearBlackCount: Int,
    val nearWhiteCount: Int
) {
    class Builder {
        private val bins = IntArray(64)
        private var count = 0
        private var nearBlack = 0
        private var nearWhite = 0

        fun add(pixel: Int) {
            if ((pixel ushr 24) == 0) return
            val red = (pixel ushr 16) and 255
            val green = (pixel ushr 8) and 255
            val blue = pixel and 255
            val luma = luma(red, green, blue)
            bins[luma / 4]++
            count++
            if (luma <= 5) nearBlack++
            if (luma >= 250) nearWhite++
        }

        fun build(): LuminanceHistogram =
            LuminanceHistogram(bins.copyOf(), count, nearBlack, nearWhite)
    }
}
