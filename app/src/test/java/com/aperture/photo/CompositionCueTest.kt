package com.aperture.photo

import com.aperture.photo.camera.Composition
import com.aperture.photo.camera.CompositionAnchor
import com.aperture.photo.camera.CompositionCue
import com.aperture.photo.camera.CompositionCueGate
import com.aperture.photo.camera.FocusRegion
import com.aperture.photo.camera.Scene
import com.aperture.photo.camera.ShootingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompositionCueTest {
    private val left = CompositionCue.AIM_LEFT
    private val right = CompositionCue.AIM_RIGHT

    @Test fun requiresDistinctStableFreshSamples() {
        val gate = CompositionCueGate()
        assertNull(gate.observe(left, 100L, 100L))
        assertNull(gate.observe(left, 100L, 400L))
        assertNull(gate.observe(left, 200L, 400L))
        assertEquals(left, gate.observe(left, 300L, 400L))
    }

    @Test fun throttlesChangedCuesAndRepeatsSeparately() {
        val gate = CompositionCueGate()
        assertNull(gate.observe(left, 1_000L, 1_000L))
        assertEquals(left, gate.observe(left, 1_200L, 1_200L))
        assertNull(gate.observe(right, 1_400L, 1_400L))
        assertNull(gate.observe(right, 1_600L, 1_600L))
        assertNull(gate.observe(right, 3_500L, 3_500L))
        assertEquals(right, gate.observe(right, 3_700L, 3_700L))
        assertNull(gate.observe(right, 4_000L, 4_000L))
        assertNull(gate.observe(right, 11_500L, 11_500L))
        assertEquals(right, gate.observe(right, 11_700L, 11_700L))
    }

    @Test fun staleMissingAndFutureEvidenceCannotBridgeReadings() {
        val gate = CompositionCueGate()
        assertNull(gate.observe(left, 100L, 100L))
        assertNull(gate.observe(left, 300L, 1_100L))
        assertNull(gate.observe(left, 1_200L, 1_199L))
        assertNull(gate.observe(left, 1_200L, 1_200L))
        assertNull(gate.observe(null, 1_400L, 1_400L))
        assertNull(gate.observe(left, 1_600L, 1_600L))
        assertEquals(left, gate.observe(left, 1_800L, 1_800L))
        gate.clearEvidence()
        assertNull(gate.observe(left, 10_000L, 10_000L))
    }

    @Test fun guidanceSharesTheCueWithVisualTextAndKeepsFallbackSilent() {
        val scene = Scene(120, 0f, 0f, FocusRegion(.12f, .5f))
        fun advice(reading: Scene?, roll: Float? = null, marked: Boolean = false) =
            Composition.advice(reading, roll, ShootingMode.STREET, true, CompositionAnchor.CENTER, marked)

        assertEquals(left, advice(scene).cue)
        assertEquals(left.words, advice(scene).text)
        assertEquals("Rotate clockwise · 9°", advice(scene, 9f).text)
        assertEquals(CompositionCue.CLOCKWISE, advice(scene, 9f).cue)
        assertNull(advice(null).cue)
        assertNull(advice(scene, marked = true).cue)
        assertNull(advice(scene.copy(focus = FocusRegion(.5f, .5f))).cue)
    }
}
