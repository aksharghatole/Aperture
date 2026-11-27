package com.aperture.photo

import com.aperture.photo.camera.ExposureCompensation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureCompensationTest {
    @Test
    fun convertsExposureIndicesToEvAndClampsToDeviceRange() {
        val exposure = ExposureCompensation(index = 3, minIndex = -6, maxIndex = 6, stepEv = .3333f)
        assertEquals(.9999f, exposure.ev, .0001f)
        assertEquals(-6, exposure.clamp(-12))
        assertEquals(4, exposure.clamp(4))
        assertEquals(6, exposure.clamp(12))
    }

    @Test
    fun hidesControlWhenDeviceHasNoCompensationRange() {
        assertFalse(ExposureCompensation(0, 0, 0, .5f).supported)
        assertFalse(ExposureCompensation(0, -3, 3, 0f).supported)
        assertTrue(ExposureCompensation(0, -3, 3, .5f).supported)
    }
}
