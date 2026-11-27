package com.aperture.photo

import com.aperture.photo.camera.CameraFlashMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFlashTest {
    @Test fun unavailableCameraExposesOnlySafeDefaultMode() {
        assertEquals(listOf(CameraFlashMode.OFF), CameraFlashMode.choices(supported = false))
    }

    @Test fun flashUnitOffersOffByDefaultPlusStillAndTorchModes() {
        assertEquals(CameraFlashMode.OFF, CameraFlashMode.entries.first())
        assertEquals(listOf(CameraFlashMode.OFF, CameraFlashMode.AUTO, CameraFlashMode.ON, CameraFlashMode.TORCH),
            CameraFlashMode.choices(supported = true))
    }
}
