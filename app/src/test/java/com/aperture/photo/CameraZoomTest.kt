package com.aperture.photo

import com.aperture.photo.camera.CameraZoom
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraZoomTest {
    @Test
    fun clampsRequestedRatiosToObservedCameraLimits() {
        val zoom = CameraZoom(2f, 1f, 2.5f)
        assertEquals(1f, zoom.clamp(.5f))
        assertEquals(2.25f, zoom.clamp(2.25f))
        assertEquals(2.5f, zoom.clamp(9f))
    }

    @Test
    fun onlyOffersPresetsWithinSupportedRange() {
        assertEquals(listOf(1f, 2f), CameraZoom(1f, 1f, 2.4f).presets())
        assertEquals(listOf(2f, 3f), CameraZoom(2f, 1.5f, 3.5f).presets())
    }
}
