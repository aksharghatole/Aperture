package com.aperture.photo

import com.aperture.photo.imaging.InspectionTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class InspectionTransformTest {
    @Test fun fitIgnoresPanningAndZoomingOutReturnsToFit() {
        val fit = InspectionTransform()
        assertEquals(fit, fit.update(1f, .5f, 1f, panX = .4f, panY = .4f))
        val zoomed = fit.update(1f, .5f, 3f, panY = .75f)
        assertEquals(fit, zoomed.update(1f, .5f, .1f))
    }

    @Test fun portraitAndLandscapeKeepLetterboxedAxesCentered() {
        val fit = InspectionTransform()
        val portrait = fit.update(1f, .5f, 2f, panX = 1f, panY = 1f)
        val landscape = fit.update(1f, 2f, 2f, panX = 1f, panY = 1f)
        assertEquals(InspectionTransform(2f, 0f, .5f), portrait)
        assertEquals(InspectionTransform(2f, .5f, 0f), landscape)
    }

    @Test fun zoomAnchorsAtTheGestureAndClampsPanToTheFittedCrop() {
        val fit = InspectionTransform()
        assertEquals(InspectionTransform(2f, -.25f, .125f),
            fit.update(1f, 1f, 2f, pivotX = .25f, pivotY = -.125f))
        val wideCropInPortraitViewport = fit.update(.5f, 2f, 12f, panX = 10f, panY = 10f)
        assertEquals(InspectionTransform(4f, 1.5f, 0f), wideCropInPortraitViewport)
    }
}
