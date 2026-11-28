package com.aperture.photo

import com.aperture.photo.camera.*
import org.junit.Assert.*
import org.junit.Test

class SteadinessTrackerTest {
    private fun SteadinessTracker.quiet(at: Long) {
        sample(MotionChannel.ROTATION, at, 0f, 0f, 0f)
        sample(MotionChannel.TRANSLATION, at, 0f, 0f, 0f)
    }
    @Test fun requiresBothSensorsAndContinuousEvidence() {
        val tracker = SteadinessTracker()
        assertEquals(Steadiness.UNAVAILABLE, tracker.status(0))
        tracker.start(true)
        tracker.sample(MotionChannel.ROTATION, 100, 0f, 0f, 0f)
        assertEquals(Steadiness.WAITING, tracker.status(100))
        for (t in 200L..1000L step 100) tracker.quiet(t)
        assertFalse(tracker.canCapture(200, 1000))
        tracker.quiet(1100)
        assertEquals(Steadiness.STEADY, tracker.status(1100))
        assertTrue(tracker.canCapture(200, 1100))
        assertFalse(tracker.canCapture(300, 1100))
        assertFalse(tracker.canCapture(200, 1400))
    }
    @Test fun movementAndGapsResetHold() {
        val tracker = SteadinessTracker()
        tracker.start(true)
        for (t in 0L..1000L step 100) tracker.quiet(t)
        assertEquals(Steadiness.STEADY, tracker.status(1000))
        tracker.sample(MotionChannel.TRANSLATION, 1050, .5f, 0f, 0f)
        assertEquals(Steadiness.MOVING, tracker.status(1050))
        tracker.quiet(1100)
        tracker.quiet(1500)
        assertEquals(Steadiness.MOVING, tracker.status(1500))
        assertEquals(Steadiness.WAITING, tracker.status(1800))
        tracker.clear()
        assertEquals(Steadiness.UNAVAILABLE, tracker.status(1800))
    }
    @Test fun invalidSamplesAndTimeoutNeverTrigger() {
        val tracker = SteadinessTracker()
        tracker.start(true)
        for (t in 10000L..11000L step 100) tracker.quiet(t)
        assertFalse(tracker.canCapture(1000, 11000))
        tracker.sample(MotionChannel.ROTATION, 11001, Float.NaN, 0f, 0f)
        assertEquals(Steadiness.WAITING, tracker.status(11001))
        assertFalse(tracker.canCapture(10000, 11001))
    }
}
