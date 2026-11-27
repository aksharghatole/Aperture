package com.aperture.photo

import com.aperture.photo.imaging.RgbHistogram
import org.junit.Assert.assertEquals
import org.junit.Test

class RgbHistogramTest {
    @Test
    fun countsPerChannelClippingForSaturatedPrimariesAndIgnoresTransparentPixels() {
        val histogram = RgbHistogram.Builder().apply {
            add(0x00ffffff)
            add(0xffff0000.toInt())
            add(0xff00ff00.toInt())
            add(0xff0000ff.toInt())
        }.build()
        assertEquals(3, histogram.sampleCount)
        assertEquals(1, histogram.redNearWhite)
        assertEquals(1, histogram.greenNearWhite)
        assertEquals(1, histogram.blueNearWhite)
        assertEquals(1, histogram.red[31])
        assertEquals(1, histogram.green[31])
        assertEquals(1, histogram.blue[31])
    }

    @Test
    fun grayscaleAndMixedPixelsPopulateAllChannels() {
        val histogram = RgbHistogram.Builder().apply {
            add(0xff808080.toInt())
            add(0xff804020.toInt())
        }.build()
        assertEquals(2, histogram.sampleCount)
        assertEquals(2, histogram.red.sum())
        assertEquals(2, histogram.green.sum())
        assertEquals(2, histogram.blue.sum())
        assertEquals(2, histogram.red[16])
        assertEquals(1, histogram.green[16])
        assertEquals(1, histogram.blue[16])
        assertEquals(1, histogram.green[8])
        assertEquals(1, histogram.blue[4])
    }
}
