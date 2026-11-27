package com.aperture.photo

import com.aperture.photo.library.RecoveryPolicy
import org.junit.Assert.*
import org.junit.Test

class RecoveryPolicyTest {
    private val now = 200_000_000L
    private val old = (now - RecoveryPolicy.MaxAgeMillis) / 1000 - 1
    private val app = "com.aperture.photo"

    @Test fun onlyRecognizesStaleApertureTemporaryNames() {
        assertTrue(RecoveryPolicy.isCapture("capture-123.jpg", old * 1000, now))
        assertFalse(RecoveryPolicy.isCapture("capture-123.jpg", now, now))
        assertFalse(RecoveryPolicy.isCapture("capture-123.jpg", 0, now))
        assertFalse(RecoveryPolicy.isCapture("camera-123.jpg", old * 1000, now))
        val original = "1200-deadbeef-0000-4000-8000-000000000001_CLEAN.photo"
        assertEquals(original, RecoveryPolicy.orphanCandidate("$original.edits.bak", old * 1000, now))
        assertNull(RecoveryPolicy.orphanCandidate(original, old * 1000, now))
        assertNull(RecoveryPolicy.orphanCandidate("$original.edits", now, now))
    }

    @Test fun pendingExportRequiresAgeOwnershipAndTheExpectedPath() {
        fun candidate(owner: String? = app, path: String = "Pictures/Aperture/",
            pending: Boolean = true, date: Long = old, name: String = "Aperture-123.jpg"): Boolean =
            RecoveryPolicy.isPendingExport(name, path, owner, app, date, pending, now)
        assertTrue(candidate())
        assertTrue(candidate(name = "Aperture-123-deadbeef-0000-4000-8000-000000000001.jpg"))
        assertFalse(candidate(owner = "another.app"))
        assertFalse(candidate(owner = null))
        assertFalse(candidate(path = "Pictures/Other/"))
        assertFalse(candidate(pending = false))
        assertFalse(candidate(date = now / 1000))
        assertFalse(candidate(date = 0))
        assertFalse(candidate(name = "capture-123.jpg"))
    }
}
