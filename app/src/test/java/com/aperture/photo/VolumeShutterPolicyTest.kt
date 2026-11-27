package com.aperture.photo

import com.aperture.photo.camera.VolumeShutterPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeShutterPolicyTest {
    @Test
    fun capturesOnlyWhenEnabledAndCameraCanAcceptAKeyPress() {
        assertTrue(VolumeShutterPolicy.shouldCapture(true, true, true, false, true, 0))
        assertFalse(VolumeShutterPolicy.shouldCapture(false, true, true, false, true, 0))
        assertFalse(VolumeShutterPolicy.shouldCapture(true, false, true, false, true, 0))
        assertFalse(VolumeShutterPolicy.shouldCapture(true, true, false, false, true, 0))
        assertFalse(VolumeShutterPolicy.shouldCapture(true, true, true, true, true, 0))
        assertFalse(VolumeShutterPolicy.shouldCapture(true, true, true, false, false, 0))
        assertFalse(VolumeShutterPolicy.shouldCapture(true, true, true, false, true, 1))
    }
}
