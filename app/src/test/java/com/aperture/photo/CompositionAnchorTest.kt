package com.aperture.photo

import com.aperture.photo.camera.Composition
import com.aperture.photo.camera.CompositionAnchor
import com.aperture.photo.camera.FocusRegion
import com.aperture.photo.camera.Framing
import com.aperture.photo.camera.ManualSubject
import com.aperture.photo.camera.Scene
import com.aperture.photo.camera.SceneReading
import com.aperture.photo.camera.ShootingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompositionAnchorTest {
    @Test fun explicitTargetsUseFixedScreenCoordinatesIncludingLowerThirds() {
        val evidence = FocusRegion(.9f, .1f)
        assertEquals(FocusRegion(.5f, .5f), CompositionAnchor.CENTER.target(Framing.THIRDS, evidence))
        assertEquals(FocusRegion(1f / 3f, 1f / 3f), CompositionAnchor.UPPER_LEFT.target(Framing.CENTER, evidence))
        assertEquals(FocusRegion(2f / 3f, 1f / 3f), CompositionAnchor.UPPER_RIGHT.target(Framing.CENTER, evidence))
        assertEquals(FocusRegion(1f / 3f, 2f / 3f), CompositionAnchor.LOWER_LEFT.target(Framing.CENTER, evidence))
        assertEquals(FocusRegion(2f / 3f, 2f / 3f), CompositionAnchor.LOWER_RIGHT.target(Framing.CENTER, evidence))
        assertEquals(FocusRegion(1f - 1f / 3f, 1f / 3f), CompositionAnchor.AUTO.target(Framing.THIRDS, evidence))
    }

    @Test fun aimSignUsesAlreadyMirroredScreenEvidenceAndTheSelectedTarget() {
        val mode = ShootingMode.STREET
        val point = FocusRegion(.25f, .45f)
        val rear = Scene(120, 0f, 0f, point)
        val front = rear.copy(focus = point.copy(x = 1f - point.x))
        assertEquals("Aim a little left", Composition.guidance(rear, null, mode, anchor = CompositionAnchor.CENTER))
        assertEquals("Aim a little right", Composition.guidance(front, null, mode, anchor = CompositionAnchor.CENTER))
        val below = rear.copy(focus = FocusRegion(1f / 3f, .45f))
        assertEquals("Aim a little higher", Composition.guidance(below, null, mode, anchor = CompositionAnchor.LOWER_LEFT))
    }

    @Test fun missingOrExpiredReadingLeavesTheFixedTargetAsAManualGuide() {
        val reading = SceneReading(Scene(120, 0f, 0f, FocusRegion(.9f, .5f)), 100L)
        val anchor = CompositionAnchor.CENTER
        val evidence = SceneReading.accept(reading, 300L)?.scene
        assertEquals("Aim a little right", Composition.guidance(evidence, null, ShootingMode.LANDSCAPE, anchor = anchor))
        val expired = SceneReading.accept(reading, 900L)?.scene
        assertNull(Composition.framingPoint(expired, ShootingMode.LANDSCAPE, anchor))
        assertEquals("Frame your subject at the target",
            Composition.guidance(expired, null, ShootingMode.LANDSCAPE, anchor = anchor))
    }

    @Test fun aMarkedRegionDoesNotUseAnUnrelatedDetectedSubject() {
        val scene = Scene(120, 0f, 0f, FocusRegion(.9f, .8f))
        assertNull(Composition.framingPoint(scene, ShootingMode.STREET, manualSubject = true))
        assertEquals("Manual marker · move the camera to frame your subject",
            Composition.guidance(scene, null, ShootingMode.STREET, manualSubject = true))
        assertEquals("Aim a little right", Composition.guidance(scene.copy(focus = FocusRegion(.9f, .33f)), null,
            ShootingMode.STREET, manualSubject = false))
    }

    @Test fun definesTheSameRegionForEquivalentPortraitAndLandscapeTaps() {
        val portrait = ManualSubject.fromTap(30f, 160f, 100, 200)
        assertEquals(portrait, ManualSubject.fromTap(60f, 80f, 200, 100))
        assertEquals(FocusRegion(.06f, .94f), ManualSubject.fromTap(0f, 200f, 100, 200)?.center)
        assertNull(ManualSubject.fromTap(Float.NaN, 80f, 200, 100))
        assertNull(ManualSubject.fromTap(30f, 80f, 0, 100))
    }
}
