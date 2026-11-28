package com.aperture.photo

import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.ExportMemory
import com.aperture.photo.imaging.ExportMemoryException
import com.aperture.photo.imaging.ExportPlan
import com.aperture.photo.imaging.ExportSettings
import com.aperture.photo.imaging.ImageSize
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.publishPendingExport
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ExportSettingsTest {
    @Test fun defaultIsFullSizeAndHistoricalQuality() {
        assertNull(ExportSettings().longEdge)
        assertEquals(96, ExportSettings().quality)
        assertThrows(IllegalArgumentException::class.java) { ExportSettings(0) }
        assertThrows(IllegalArgumentException::class.java) { ExportSettings(quality = 101) }
    }

    @Test fun longestEdgeIsMeasuredAfterRotationAndCrop() {
        val source = ImageSize(4000, 3000)
        val recipe = Recipe(quarterTurns = 1, crop = CropWindow(.25f, .125f, .75f, .875f))
        val full = ExportPlan.create(source, recipe, null)
        assertEquals(ImageSize(1500, 3000), full.output)
        val reduced = ExportPlan.create(source, recipe, 2048)
        assertEquals(ImageSize(1024, 2048), reduced.output)
        assertEquals(ImageSize(2731, 2048), reduced.decode)
        assertEquals(full.crop, reduced.crop)
        assertEquals(full.rotated, reduced.rotated)
    }

    @Test fun neverUpscalesAndHandlesExtremeAspects() {
        val source = ImageSize(15, 9)
        val unchanged = ExportPlan.create(source, Recipe(), 8000)
        assertEquals(source, unchanged.output)
        assertEquals(source, unchanged.decode)
        assertEquals(ImageSize(1, 13), ImageSize(1, 4000).within(13))
        assertEquals(ImageSize(13, 1), ImageSize(4000, 1).within(13))
        assertEquals(ImageSize(1, 1), ImageSize(6000, 4000).within(1))
    }

    @Test fun memoryFallbackIsExplicitAndFitsFinishedCrop() {
        val recipe = Recipe(crop = CropWindow(.25f, .25f, .75f, .75f))
        val budget = 32L * 1024 * 1024
        val failure = assertThrows(ExportMemoryException::class.java) {
            ExportMemory.check(12000, 9000, recipe, 2048, budget)
        }
        val edge = checkNotNull(failure.suggestedMaxEdge)
        assertTrue(edge < 2048)
        val plan = ExportMemory.check(12000, 9000, recipe, edge, budget)
        assertEquals(edge, plan.output.longest)
        assertTrue(ExportMemory.estimate(plan) <= budget)
    }

    @Test fun failedWriteOrPublishRemovesPendingOutputAndRetainsOriginalError() {
        val error = IOException("simulated failure")
        for (failWrite in listOf(true, false)) {
            val operations = mutableListOf<String>()
            val thrown = assertThrows(IOException::class.java) {
                publishPendingExport("pending", write = {
                    operations.add("write $it")
                    if (failWrite) throw error
                }, publish = {
                    operations.add("publish $it")
                    throw error
                }, cleanup = {
                    operations.add("delete $it")
                    throw IOException("cleanup failed")
                })
            }
            assertSame(error, thrown)
            val expected = if (failWrite) listOf("write pending", "delete pending")
                else listOf("write pending", "publish pending", "delete pending")
            assertEquals(expected, operations)
        }
        val sequence = mutableListOf<String>()
        assertEquals("pending", publishPendingExport("pending",
            write = { sequence.add("write") }, publish = { sequence.add("publish") },
            cleanup = { sequence.add("delete") }))
        assertEquals(listOf("write", "publish"), sequence)
    }
}
