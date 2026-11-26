package com.aperture.photo

import com.aperture.photo.imaging.CropPixels
import com.aperture.photo.imaging.CropWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class CropWindowTest {
    @Test fun centeredPresetsKeepNativeScale() {
        val crop = CropWindow.centered(1.5f, 1f)
        assertEquals(CropPixels(160, 0, 640, 640), crop.pixels(960, 640))
        val portrait = CropWindow.centered(.8f, 1f)
        assertEquals(CropPixels(0, 100, 800, 800), portrait.pixels(800, 1000))
    }

    @Test fun movementClampsToImageAndRotationTracksSamePixels() {
        val crop = CropWindow(.125f, .25f, .625f, .75f).move(-1f, 1f)
        assertEquals(CropWindow(0f, .5f, .5f, 1f), crop)
        assertEquals(CropPixels(0, 300, 400, 300), crop.pixels(800, 600))
        assertEquals(CropPixels(0, 0, 300, 400), crop.rotateClockwise().pixels(600, 800))
    }
}
