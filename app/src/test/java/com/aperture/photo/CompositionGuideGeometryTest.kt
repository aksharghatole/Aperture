package com.aperture.photo

import com.aperture.photo.camera.CompositionGuide
import com.aperture.photo.camera.CompositionGuideGeometry
import com.aperture.photo.camera.NormalizedGuideLine
import com.aperture.photo.camera.ShootingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionGuideGeometryTest {
    @Test
    fun automaticGuidesKeepModeDefaultsAndOffDrawsNoGrid() {
        assertEquals(4, CompositionGuideGeometry.lines(CompositionGuide.AUTO, ShootingMode.STREET).size)
        assertTrue(CompositionGuideGeometry.lines(CompositionGuide.AUTO, ShootingMode.PORTRAIT).isEmpty())
        assertTrue(CompositionGuideGeometry.lines(CompositionGuide.OFF, ShootingMode.STREET).isEmpty())
        assertEquals(1, CompositionGuideGeometry.lines(CompositionGuide.AUTO, ShootingMode.LANDSCAPE).size)
    }

    @Test
    fun gridsUseNormalizedPreviewCoordinatesAcrossOrientations() {
        val lines = CompositionGuideGeometry.lines(CompositionGuide.THIRDS, ShootingMode.PORTRAIT)
        assertEquals(4, lines.size)
        lines.forEach { line ->
            listOf(line.startX, line.startY, line.endX, line.endY).forEach {
                assertTrue(it in 0f..1f)
            }
        }
        assertEquals(NormalizedGuideLine(.382f, 0f, .382f, 1f),
            CompositionGuideGeometry.lines(CompositionGuide.GOLDEN, ShootingMode.STREET).first())
    }
}
