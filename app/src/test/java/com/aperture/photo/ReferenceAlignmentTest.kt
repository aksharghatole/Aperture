package com.aperture.photo

import com.aperture.photo.camera.ReferenceAlignment
import com.aperture.photo.camera.ReferenceBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReferenceAlignmentTest {
    @Test fun defaultAlignmentCenterCropsInEitherOrientation() {
        val centered = ReferenceAlignment.Centered
        assertEquals(ReferenceBounds(-500f, 0f, 1600f, 800f), centered.bounds(200, 100, 600f, 800f))
        assertEquals(ReferenceBounds(0f, -500f, 800f, 1600f), centered.bounds(100, 200, 800f, 600f))
    }

    @Test fun translationScalesWithTheViewfinderDimensions() {
        val alignment = ReferenceAlignment.of(.25f, -.125f, .5f)
        assertEquals(ReferenceBounds(100f, 100f, 400f, 400f), alignment.bounds(100, 100, 400f, 800f))
        assertEquals(ReferenceBounds(200f, 200f, 800f, 800f), alignment.bounds(100, 100, 800f, 1600f))
    }

    @Test fun pinchKeepsTheReferencePointUnderTheGestureCenter() {
        val result = ReferenceAlignment.Centered.transform(.25f, -.125f, .125f, 0f, 2f)
        assertEquals(ReferenceAlignment.of(-.125f, .125f, 2f), result)
    }

    @Test fun clampedZoomUsesTheActualScaleChangeAndBoundsTranslation() {
        val maxZoom = ReferenceAlignment.of(0f, 0f, 3f)
        assertEquals(maxZoom, maxZoom.transform(.25f, .25f, 0f, 0f, 2f))
        assertEquals(ReferenceAlignment.of(.5f, -.5f, .5f),
            maxZoom.transform(0f, 0f, 2f, -2f, .1f))
        assertSame(maxZoom, maxZoom.transform(0f, 0f, 0f, 0f, Float.NaN))
    }
}
