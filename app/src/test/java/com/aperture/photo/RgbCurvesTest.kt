package com.aperture.photo

import com.aperture.photo.imaging.CurveLookup
import com.aperture.photo.imaging.RgbCurves
import com.aperture.photo.imaging.ToneCurve
import org.junit.Assert.assertEquals
import org.junit.Test

class RgbCurvesTest {
    @Test fun defaultsAreIdentityAcrossEntireRange() {
        val lookup = CurveLookup(RgbCurves())
        for (value in 0..255) {
            val pixel = 0x80320000.toInt() or (value shl 8) or (255 - value)
            assertEquals(pixel, lookup.apply(pixel))
        }
    }

    @Test fun masterRunsBeforeIndividualChannelsAndAlphaIsPreserved() {
        val lookup = CurveLookup(RgbCurves(master = ToneCurve(midtone = 64), red = ToneCurve(midtone = 192)))
        assertEquals(0x80604040.toInt(),
            lookup.apply(0x80808080.toInt()))
        assertEquals(0x00123456, lookup.apply(0x00123456))
    }

    @Test fun curveInterpolatesThroughItsThreePoints() {
        val curve = ToneCurve(16, 80, 240)
        assertEquals(listOf(16, 48, 80, 161, 240), listOf(0, 64, 128, 192, 255).map(curve::map))
    }
}
