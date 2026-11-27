package com.aperture.photo

import com.aperture.photo.camera.FocusFeedbackTracker
import com.aperture.photo.camera.FocusStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusFeedbackTest {
    @Test fun supportedRequestTransitionsFromPendingToCameraReportedResult() {
        val tracker = FocusFeedbackTracker()
        val pending = tracker.request(.25f, .7f, autofocusSupported = true, exposureMeteringSupported = true)
        assertEquals(FocusStatus.PENDING, pending.status)
        val success = tracker.complete(pending.requestId, successful = true)
        assertEquals(FocusStatus.SUCCESS, success?.status)
        assertEquals(.25f, success?.x)
        assertEquals(.7f, success?.y)
    }

    @Test fun cameraReportedFailureAndUnsupportedRequestDoNotClaimSuccess() {
        val tracker = FocusFeedbackTracker()
        val pending = tracker.request(.5f, .5f, autofocusSupported = true, exposureMeteringSupported = false)
        assertEquals(FocusStatus.FAILED, tracker.complete(pending.requestId, successful = false)?.status)
        val metering = tracker.request(.3f, .4f, autofocusSupported = false, exposureMeteringSupported = true)
        assertEquals(FocusStatus.METERING, metering.status)
        assertEquals(FocusStatus.METERED, tracker.complete(metering.requestId, successful = true)?.status)
        val unsupported = tracker.request(.3f, .4f, autofocusSupported = false, exposureMeteringSupported = false)
        assertEquals(FocusStatus.UNSUPPORTED, unsupported.status)
        assertNull(tracker.complete(unsupported.requestId, successful = true))
    }

    @Test fun newerTapAndSessionClearDiscardStaleCompletions() {
        val tracker = FocusFeedbackTracker()
        val first = tracker.request(.2f, .3f, autofocusSupported = true, exposureMeteringSupported = true)
        val second = tracker.request(.8f, .7f, autofocusSupported = true, exposureMeteringSupported = true)
        assertNull(tracker.complete(first.requestId, successful = true))
        assertTrue(tracker.complete(second.requestId, successful = false) != null)

        val third = tracker.request(.5f, .5f, autofocusSupported = true, exposureMeteringSupported = true)
        tracker.clear()
        assertNull(tracker.complete(third.requestId, successful = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCoordinatesOutsideTheViewfinder() {
        FocusFeedbackTracker().request(1.1f, .5f, autofocusSupported = true, exposureMeteringSupported = true)
    }
}
