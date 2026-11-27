package com.aperture.photo

import com.aperture.photo.camera.Composition
import com.aperture.photo.camera.DetailTracker
import com.aperture.photo.camera.ShootingMode
import com.aperture.photo.camera.PortraitFraming
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

    @Test fun shootingModesUseDifferentMeasuredCues() {
        val dim = Scene(62, .5f, 0f, null, portraitEyes = FocusRegion(.5f, 1f / 3f))
        assertEquals("Eyes near the framing guide", Composition.guidance(dim, 0f, ShootingMode.PORTRAIT))
        assertEquals("Low light · hold steady", Composition.guidance(dim, 0f, ShootingMode.NIGHT))
        assertEquals("Rotate clockwise · 3°", Composition.guidance(dim, 3f, ShootingMode.ARCHITECTURE))
        assertEquals("Camera level", Composition.guidance(dim, 0f, ShootingMode.LANDSCAPE))
        assertEquals("Use the horizon guide",
            Composition.guidance(null, null, ShootingMode.LANDSCAPE, analysisAvailable = false))
    }

    @Test fun trackerWithholdsSingleFramesAndDropsDisappearingOrMovingDetail() {
        val tracker = DetailTracker()
        val left = FocusRegion(.2f, .3f)
        val right = FocusRegion(.8f, .3f)
        assertNull(tracker.update(left))
        assertEquals(left, tracker.update(left))
        assertNull(tracker.update(right))
        assertEquals(right, tracker.update(right))
        assertNull(tracker.update(null))
        assertNull(tracker.update(right))
    }

    @Test fun portraitUsesEyesAndWithholdsAimingWhenOnlyVisualDetailIsAvailable() {
        val scene = Scene(130, 0f, 0f, FocusRegion(.8f, .5f))
        assertEquals("Place the eyes near the upper guide", Composition.guidance(scene, 0f, ShootingMode.PORTRAIT))
        assertNull(Composition.framingPoint(scene, ShootingMode.PORTRAIT))
        val face = scene.copy(portraitEyes = FocusRegion(.2f, 1f / 3f))
        assertEquals("Aim a little left", Composition.guidance(face, 0f, ShootingMode.PORTRAIT))
        assertEquals(face.portraitEyes, Composition.framingPoint(face, ShootingMode.PORTRAIT))
        assertEquals(scene.focus, Composition.framingPoint(face, ShootingMode.STREET))
    }

    @Test fun portraitRejectsAmbiguousDetectionsAndMirrorsPreviewCoordinates() {
        assertNull(PortraitFraming.eyePoint(2, .95f, .2f, .4f, .1f, false))
        assertNull(PortraitFraming.eyePoint(1, .5f, .2f, .4f, .1f, false))
        assertNull(PortraitFraming.eyePoint(1, .9f, .02f, .4f, .1f, false))
        assertNull(PortraitFraming.eyePoint(1, .9f, .2f, .4f, .01f, false))
        assertEquals(FocusRegion(.2f, .4f), PortraitFraming.eyePoint(1, .9f, .2f, .4f, .1f, false))
        assertEquals(FocusRegion(.8f, .4f), PortraitFraming.eyePoint(1, .9f, .2f, .4f, .1f, true))
    }

    @Test fun gravityRollTracksDisplayAxesInEveryRotation() {
        val tiltedAxes = listOf(2f to 9.8f, 9.8f to -2f, -2f to -9.8f, -9.8f to 2f)
        tiltedAxes.forEachIndexed { rotation, (x, y) ->
            assertEquals(11.53f, Composition.sensorRoll(x, y, 0f, rotation)!!, .02f)
        }
        assertEquals(90f, Composition.sensorRoll(9.8f, 0f, 0f, 0)!!, .001f)
        assertEquals(0f, Composition.sensorRoll(9.8f, 0f, 0f, 1)!!, .001f)
        assertEquals(0f, Composition.sensorRoll(0f, -9.8f, 0f, 2)!!, .001f)
        assertNull(Composition.sensorRoll(.2f, .1f, 9.8f, 0))
        assertNull(Composition.sensorRoll(0f, 9.8f, 0f, 4))
        assertEquals("Rotate clockwise · 12°", Composition.guidance(null, 12f, Framing.HORIZON))
    }
}
