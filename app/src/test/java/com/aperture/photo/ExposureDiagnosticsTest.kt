package com.aperture.photo

import com.aperture.photo.camera.ExposureDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureDiagnosticsTest {
    @Test fun histogramUsesBoundedBinsAndCountsEverySample() {
        val samples = intArrayOf(0, 8, 9, 127, 247, 255)
        val result = ExposureDiagnostics.from(samples)
        assertEquals(ExposureDiagnostics.HistogramBins, result.histogram.size)
        assertEquals(samples.size, result.histogram.sum())
        assertEquals(.33333334f, result.darkFraction, .000001f)
        assertEquals(.33333334f, result.brightFraction, .000001f)
    }

    @Test fun warningsRequireAtLeastFivePercentNearTheSampleBounds() {
        val evenlyLit = ExposureDiagnostics.from(IntArray(100) { 128 })
        assertFalse(evenlyLit.darkWarning)
        assertFalse(evenlyLit.brightWarning)

        val backlit = ExposureDiagnostics.from(IntArray(100) { if (it < 10) 255 else 128 })
        assertTrue(backlit.brightWarning)
        assertFalse(backlit.darkWarning)

        val underexposed = ExposureDiagnostics.from(IntArray(100) { if (it < 50) 0 else 32 })
        assertTrue(underexposed.darkWarning)
        assertFalse(underexposed.brightWarning)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyFrames() {
        ExposureDiagnostics.from(intArrayOf())
    }
}
