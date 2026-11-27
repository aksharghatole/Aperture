package com.aperture.photo

import com.aperture.photo.camera.SelfTimer
import com.aperture.photo.camera.SelfTimerStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTimerTest {
    @Test fun threeSecondCountdownAdvancesToCaptureWithoutClockDependencies() {
        val start = SelfTimer.start(7, 3)
        assertEquals(3, start.secondsRemaining)
        val two = (SelfTimer.advance(start) as SelfTimerStep.Continue).countdown
        val one = (SelfTimer.advance(two) as SelfTimerStep.Continue).countdown
        assertEquals(2, two.secondsRemaining)
        assertEquals(1, one.secondsRemaining)
        assertTrue(SelfTimer.advance(one) is SelfTimerStep.Capture)
    }

    @Test fun tenSecondTimerKeepsItsRequestIdentityAcrossTicks() {
        val start = SelfTimer.start(12, 10)
        val next = (SelfTimer.advance(start) as SelfTimerStep.Continue).countdown
        assertEquals(12, next.id)
        assertEquals(9, next.secondsRemaining)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedDurations() {
        SelfTimer.start(1, 5)
    }
}
