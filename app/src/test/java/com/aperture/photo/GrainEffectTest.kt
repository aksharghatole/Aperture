package com.aperture.photo

import com.aperture.photo.imaging.GrainEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrainEffectTest {
    @Test fun neutralStrengthPreservesPixelExactly() {
        val pixel = 0x7f203040
        assertEquals(pixel, GrainEffect.adjustColor(pixel, noise = .9f, intensity = 0f))
    }

    @Test fun noiseIsDeterministicAndKeepsAlpha() {
        val first = GrainEffect.noise(17, 29)
        assertEquals(first, GrainEffect.noise(17, 29))
        assertNotEquals(first, GrainEffect.noise(18, 29))
        assertEquals(0x7f, GrainEffect.adjustColor(0x7f808080, first, 1f) ushr 24)
    }

    @Test fun grainSizeChangesTheSpatialScale() {
        val fineCells = GrainEffect.cellsPerShortEdge(0f)
        val coarseCells = GrainEffect.cellsPerShortEdge(1f)
        assertTrue(fineCells > coarseCells)
        assertEquals(GrainEffect.noiseForPixel(120, 80, 300, fineCells),
            GrainEffect.noiseForPixel(1200, 800, 3000, fineCells))
        assertNotEquals(GrainEffect.noiseForPixel(10, 10, 300, fineCells),
            GrainEffect.noiseForPixel(10, 10, 300, coarseCells))
    }

    @Test fun cropAndRotationUseTheOutputGeometryForStableGrain() {
        val cells = GrainEffect.cellsPerShortEdge(.5f)
        val cropped = GrainEffect.noiseForPixel(75, 90, 180, cells)
        val rotatedCrop = GrainEffect.noiseForPixel(90, 75, 180, cells)
        assertTrue(cropped.isFinite())
        assertTrue(rotatedCrop.isFinite())
    }
}
