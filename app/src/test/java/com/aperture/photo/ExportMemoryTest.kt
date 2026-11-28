package com.aperture.photo

import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.ExportMemory
import com.aperture.photo.imaging.ExportMemoryException
import com.aperture.photo.imaging.Recipe
import org.junit.Assert.*
import org.junit.Test

class ExportMemoryTest {
    @Test fun estimatesLargeImagesAndRequiresExplicitReducedExport() {
        val budget = 192L * 1024 * 1024
        ExportMemory.check(4000, 3000, Recipe(), null, budget)
        for ((width, height) in listOf(8000 to 6000, 12000 to 9000)) {
            val failure = assertThrows(ExportMemoryException::class.java) {
                ExportMemory.check(width, height, Recipe(), null, budget)
            }
            val edge = checkNotNull(failure.suggestedMaxEdge)
            assertTrue(edge < width)
            ExportMemory.check(width, height, Recipe(), edge, budget)
        }
    }

    @Test fun accountsForRotatedCropWithoutIgnoringOriginalDecode() {
        val crop = CropWindow(0f, 0f, .5f, 1f)
        val recipe = Recipe(quarterTurns = 1, crop = crop)
        val full = ExportMemory.estimate(8000, 6000, Recipe())
        val cropped = ExportMemory.estimate(8000, 6000, recipe)
        assertEquals(96_000_000L, full - cropped)
        assertTrue(cropped > 384_000_000L)
    }

    @Test fun lowHeapCanRejectEvenReducedExports() {
        val failure = assertThrows(ExportMemoryException::class.java) {
            ExportMemory.check(4000, 3000, Recipe(), null, 1024 * 1024L)
        }
        assertNull(failure.suggestedMaxEdge)
        assertEquals(80L, ExportMemory.budget(400L, 320L))
    }

    @Test fun grainUsesNoAdditionalFullImageBuffer() {
        val full = ExportMemory.estimate(4000, 3000, Recipe())
        val grained = ExportMemory.estimate(4000, 3000, Recipe(grainIntensity = .8f, grainSize = .6f))
        assertEquals(full, grained)
    }

    @Test fun smallImagesCanExportWithLimitedHeapHeadroom() {
        val budget = ExportMemory.budget(24L * 1024 * 1024, 16L * 1024 * 1024)
        ExportMemory.check(320, 240, Recipe(), null, budget)
        val larger = assertThrows(ExportMemoryException::class.java) {
            ExportMemory.check(960, 640, Recipe(), null, budget)
        }
        val edge = checkNotNull(larger.suggestedMaxEdge)
        ExportMemory.check(960, 640, Recipe(), edge, budget)
    }
}
