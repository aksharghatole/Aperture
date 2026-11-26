package com.aperture.photo

import com.aperture.photo.camera.Composition
import com.aperture.photo.camera.FocusRegion
import com.aperture.photo.camera.Framing
import com.aperture.photo.camera.Scene
import org.junit.Assert.*
import org.junit.Test

class CompositionTest {
    @Test fun blankFrameWithholdsFocusCue() {
        val scene = Composition.measure(IntArray(400) { 130 }, 20, 20)
        assertEquals(130, scene.brightness)
        assertNull(scene.focus)
        assertEquals("Use the grid to frame your subject", Composition.guidance(scene, 0f, Framing.THIRDS))
    }

    @Test fun detectsLightingWithoutInventingSubject() {
        val dark = Composition.measure(IntArray(400) { 32 }, 20, 20)
        val bright = Composition.measure(IntArray(400) { 237 }, 20, 20)
        assertEquals(1f, dark.shadowFraction, 0f)
        assertEquals(1f, bright.highlightFraction, 0f)
        assertEquals("Low light · hold steady", Composition.guidance(dark, null, Framing.CENTER))
        assertEquals("Bright areas may lose detail", Composition.guidance(bright, null, Framing.CENTER))
    }

    @Test fun detailOnRightYieldsRightwardAim() {
        val samples = IntArray(1600) { 100 }
        for (y in 18..21) for (x in 33..36) samples[y * 40 + x] = 220
        val scene = Composition.measure(samples, 40, 40)
        assertTrue(requireNotNull(scene.focus).x > .70f)
        assertEquals("Aim a little right", Composition.guidance(scene, 0f, Framing.CENTER))
    }

    @Test fun widespreadTextureWithholdsAimCue() {
        val samples = IntArray(1600) { index -> if (index % 40 % 4 < 2) 80 else 180 }
        assertNull(Composition.measure(samples, 40, 40).focus)
    }

    @Test fun unavailableAnalysisFallsBackToLevelAndGrid() {
        assertEquals("Camera level", Composition.guidance(null, 0f, Framing.HORIZON, analysisAvailable = false))
        assertEquals("Use the grid to frame your subject",
            Composition.guidance(null, null, Framing.CENTER, analysisAvailable = false))
    }

    @Test fun centerGuideGivesVerticalCueWhenDetailIsBelow() {
        val scene = Scene(100, 0f, 0f, FocusRegion(.5f, .8f))
        assertEquals("Aim a little lower", Composition.guidance(scene, null, Framing.CENTER))
    }

    @Test fun gravityRollPrefersCardinalOrientationsAndWithholdsNearFlatReading() {
        assertEquals(0f, Composition.sensorRoll(9.8f, 0f, 0f)!!, .001f)
        assertEquals(0f, Composition.sensorRoll(0f, -9.8f, 0f)!!, .001f)
        assertEquals(11.53f, Composition.sensorRoll(2f, 9.8f, 0f)!!, .02f)
        assertNull(Composition.sensorRoll(.2f, .1f, 9.8f))
        assertEquals("Rotate clockwise · 12°", Composition.guidance(null, 12f, Framing.HORIZON))
    }
}
