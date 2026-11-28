package com.aperture.photo

import com.aperture.photo.camera.*
import org.junit.Assert.*
import org.junit.Test

class ShootingCoachTest {
    private val eyes = FocusRegion(.4f, .33f)
    private val scene = Scene(42, .8f, .01f, null, portraitEyes = eyes)
    private val reading = SceneReading(scene, 1000L)

    @Test fun eachModeHasDistinctPracticalLessonWithoutAnalysis() {
        val lessons = ShootingMode.entries.map { ShootingCoach.advise(it, null, 1000, null) }
        assertEquals(7, lessons.map { it.lesson }.distinct().size)
        assertTrue(lessons.all { it.liveTip == null && it.focus == null })
        assertTrue(lessons.all { it.lesson.framing.isNotBlank() && it.lesson.light.isNotBlank() &&
            it.lesson.technique.isNotBlank() })
    }

    @Test fun freshEyesOnlyInPortraitAndMarkerOverrides() {
        val portrait = ShootingCoach.advise(ShootingMode.PORTRAIT, reading, 1100, null)
        assertEquals(eyes, portrait.focus?.point)
        assertNull(ShootingCoach.advise(ShootingMode.STREET, reading, 1100, null).focus)
        val marker = ManualSubject(FocusRegion(.8f, .5f))
        assertEquals(marker.center, ShootingCoach.advise(ShootingMode.PORTRAIT, reading, 1100, marker).focus?.point)
        assertNull(ShootingCoach.advise(ShootingMode.PORTRAIT, reading, 1800, null).focus)
        assertNull(ShootingCoach.advise(ShootingMode.PORTRAIT, reading, 900, null).liveTip)
    }

    @Test fun timerIsOnlySuggestedForNightOrDimMacro() {
        assertTrue(ShootingCoach.advise(ShootingMode.NIGHT, null, 1100, null).recommendTimer)
        assertTrue(ShootingCoach.advise(ShootingMode.MACRO, reading, 1100, null).recommendTimer)
        assertFalse(ShootingCoach.advise(ShootingMode.MACRO, reading, 1800, null).recommendTimer)
        assertFalse(ShootingCoach.advise(ShootingMode.STREET, reading, 1100, null).recommendTimer)
    }
}
