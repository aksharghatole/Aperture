package com.aperture.photo

import com.aperture.photo.camera.*
import org.junit.Assert.*
import org.junit.Test

class SmartExposureTest {
    private val dark = Scene(42, .8f, .01f, null)
    private val bright = Scene(200, .01f, .5f, null)
    private val mixed = Scene(100, .5f, .3f, null)
    private val balanced = Scene(125, .2f, .1f, null)
    private val ev = ExposureCompensation(0, -6, 6, 1f / 3f)

    @Test fun distinguishesStrongEvidenceAndPreservesNightMood() {
        assertEquals(ExposureSuggestion.DARK, SmartExposure.classify(dark, ShootingMode.STREET))
        assertEquals(ExposureSuggestion.BRIGHT, SmartExposure.classify(bright, ShootingMode.LANDSCAPE))
        assertEquals(ExposureSuggestion.MIXED, SmartExposure.classify(mixed, ShootingMode.PORTRAIT))
        assertEquals(ExposureSuggestion.NIGHT, SmartExposure.classify(dark, ShootingMode.NIGHT))
        assertEquals(ExposureSuggestion.BALANCED, SmartExposure.classify(balanced, ShootingMode.STREET))
    }

    @Test fun clampsToCapabilityAndNeverAdjustsAmbiguousScenes() {
        assertEquals(2, SmartExposure.target(ExposureSuggestion.DARK, ev))
        assertEquals(-2, SmartExposure.target(ExposureSuggestion.BRIGHT, ev))
        assertEquals(1, SmartExposure.target(ExposureSuggestion.DARK, ev.copy(maxIndex = 1)))
        assertNull(SmartExposure.target(ExposureSuggestion.DARK, ev.copy(index = 3)))
        assertNull(SmartExposure.target(ExposureSuggestion.DARK, ev.copy(index = 4)))
        assertNull(SmartExposure.target(ExposureSuggestion.BRIGHT, ev.copy(stepEv = 2f)))
        assertNull(SmartExposure.target(ExposureSuggestion.DARK, null))
        assertNull(SmartExposure.target(ExposureSuggestion.DARK, ev.copy(minIndex = 0, maxIndex = 0)))
        for (s in listOf(ExposureSuggestion.MIXED, ExposureSuggestion.NIGHT, ExposureSuggestion.BALANCED))
            assertNull(SmartExposure.target(s, ev))
    }

    @Test fun requiresDistinctFreshConsistentFramesAndResetsOnStalls() {
        val tracker = ExposureStability()
        fun sample(scene: Scene, at: Long, now: Long = at) =
            tracker.update(SceneReading(scene, at), now, ShootingMode.STREET)
        assertNull(sample(dark, 1000))
        assertNull(sample(dark, 1000, 1100))
        assertNull(sample(dark, 1200))
        assertEquals(ExposureSuggestion.DARK, sample(dark, 1400))
        assertNull(sample(dark, 1400, 2200))
        assertNull(sample(dark, 2300))
        assertNull(sample(bright, 2500))
        assertNull(sample(bright, 2700))
        assertEquals(ExposureSuggestion.BRIGHT, sample(bright, 2900))
        assertNull(sample(bright, 3900))
        assertNull(tracker.update(null, 4000, ShootingMode.STREET))
    }
}
