package com.aperture.photo

import com.aperture.photo.camera.Composition
import com.aperture.photo.camera.FocusRegion
import com.aperture.photo.camera.Scene
import com.aperture.photo.camera.SceneReading
import com.aperture.photo.camera.ShootingMode
import org.junit.Assert.*
import org.junit.Test

class SceneReadingTest {
    private val scene = Scene(130, 0f, 0f, FocusRegion(.8f, .5f))

    @Test fun staleAndFutureFramesAreRejected() {
        val reading = SceneReading(scene, 1_000)
        assertNull(SceneReading.accept(reading, 999))
        assertSame(reading, SceneReading.accept(reading, 1_799))
        assertEquals(1L, reading.remainingMillis(1_799))
        assertNull(SceneReading.accept(reading, 1_800))
        assertNull(SceneReading.accept(reading, 5_000))
    }

    @Test fun invalidOrStaleAnalysisLeavesManualGuidesAndFreshFramesRestoreCues() {
        fun hint(reading: SceneReading?, now: Long) = Composition.guidance(
            SceneReading.accept(reading, now)?.scene, 0f, ShootingMode.MACRO, analysisAvailable = false
        )
        assertEquals("Aim a little right", hint(SceneReading(scene, 1_000), 1_000))
        assertEquals("Use the grid to frame your subject", hint(null, 1_001))
        assertEquals("Use the grid to frame your subject", hint(SceneReading(scene, 1_000), 1_800))
        assertEquals("Aim a little right", hint(SceneReading(scene, 1_900), 1_900))
    }
}
