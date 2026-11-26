package com.aperture.photo

import com.aperture.photo.imaging.LuminanceHistogram
import org.junit.Assert.assertEquals
import org.junit.Test

class LuminanceHistogramTest {
    @Test fun countsVisiblePixelsAndNearEndpointSamples() {
        val histogram = LuminanceHistogram.Builder().apply {
            add(0x00000000)
            add(0xff000000.toInt())
            add(0xff050505.toInt())
            add(0xff808080.toInt())
            add(0xfffafafa.toInt())
            add(0xffffffff.toInt())
        }.build()
        assertEquals(5, histogram.sampleCount)
        assertEquals(5, histogram.bins.sum())
        assertEquals(2, histogram.nearBlackCount)
        assertEquals(2, histogram.nearWhiteCount)
        assertEquals(1, histogram.bins[32])
    }

    @Test fun usesPerceivedLuminanceForColorSamples() {
        val histogram = LuminanceHistogram.Builder().apply {
            add(0xffff0000.toInt())
            add(0xff00ff00.toInt())
            add(0xff0000ff.toInt())
        }.build()
        assertEquals(1, histogram.bins[13])
        assertEquals(1, histogram.bins[45])
        assertEquals(1, histogram.bins[4])
    }
}
