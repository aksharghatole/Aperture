package com.aperture.photo.imaging

internal class RgbHistogram private constructor(
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    val sampleCount: Int,
    val redNearBlack: Int,
    val redNearWhite: Int,
    val greenNearBlack: Int,
    val greenNearWhite: Int,
    val blueNearBlack: Int,
    val blueNearWhite: Int
) {
    class Builder {
        private val red = IntArray(32)
        private val green = IntArray(32)
        private val blue = IntArray(32)
        private var count = 0
        private var redBlack = 0
        private var redWhite = 0
        private var greenBlack = 0
        private var greenWhite = 0
        private var blueBlack = 0
        private var blueWhite = 0

        fun add(pixel: Int) {
            if ((pixel ushr 24) == 0) return
            val r = pixel ushr 16 and 255
            val g = pixel ushr 8 and 255
            val b = pixel and 255
            red[r / 8]++
            green[g / 8]++
            blue[b / 8]++
            if (r <= 5) redBlack++
            if (r >= 250) redWhite++
            if (g <= 5) greenBlack++
            if (g >= 250) greenWhite++
            if (b <= 5) blueBlack++
            if (b >= 250) blueWhite++
            count++
        }

        fun build() = RgbHistogram(red.copyOf(), green.copyOf(), blue.copyOf(), count,
            redBlack, redWhite, greenBlack, greenWhite, blueBlack, blueWhite)
    }
}
