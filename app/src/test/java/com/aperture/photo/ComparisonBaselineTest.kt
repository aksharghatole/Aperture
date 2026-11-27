package com.aperture.photo

import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.imaging.RgbCurves
import com.aperture.photo.imaging.ToneCurve
import com.aperture.photo.imaging.comparisonBaseline
import org.junit.Assert.assertEquals
import org.junit.Test

class ComparisonBaselineTest {
    @Test fun keepsCropAndRotationWhileRestoringTheCaptureLook() {
        val crop = CropWindow(.125f, .25f, .875f, .75f)
        val edited = Recipe(Film.SILVER, exposure = 1f, contrast = .25f, saturation = -.5f,
            warmth = .25f, vignette = .75f, quarterTurns = 3, crop = crop,
            shadows = .5f, highlights = -.5f, curves = RgbCurves(red = ToneCurve(midtone = 96)))
        assertEquals(Recipe(Film.AMBER, quarterTurns = 3, crop = crop), edited.comparisonBaseline(Film.AMBER))
        assertEquals(Recipe(quarterTurns = 3, crop = crop), edited.comparisonBaseline(Film.CLEAN))
    }

    @Test fun aCleanPhotoWithoutGeometryChangesCanUseItsDecodedPreviewDirectly() {
        assertEquals(Recipe(), Recipe(Film.DUSK, exposure = -1f).comparisonBaseline(Film.CLEAN))
    }
}
