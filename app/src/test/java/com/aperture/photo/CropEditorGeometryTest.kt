package com.aperture.photo

import com.aperture.photo.imaging.CropEditorGeometry
import com.aperture.photo.imaging.CropHandle
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.EditHistoryCodec
import com.aperture.photo.imaging.ExportPlan
import com.aperture.photo.imaging.ImageSize
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.imaging.cropHandleAt
import org.junit.Assert.*
import org.junit.Test

class CropEditorGeometryTest {
    private val geometry = CropEditorGeometry(ImageSize(800, 600))
    private val crop = CropWindow(.125f, .25f, .875f, .75f)

    @Test fun freeformEdgesAndCornersKeepOppositeAnchor() {
        val corner = geometry.drag(crop, CropHandle.TOP_LEFT, .25f, -.5f, null)
        assertEquals(.375f, corner.left, .00001f)
        assertEquals(0f, corner.top, .00001f)
        assertEquals(crop.right, corner.right, .00001f)
        assertEquals(crop.bottom, corner.bottom, .00001f)
        val edge = geometry.drag(crop, CropHandle.RIGHT, 5f, -5f, null)
        assertEquals(1f, edge.right, .00001f)
        assertEquals(crop.top, edge.top, .00001f)
        assertEquals(crop.bottom, edge.bottom, .00001f)
        assertEquals(crop.left, edge.left, .00001f)
    }

    @Test fun freeformCannotCrossOppositeEdgeOrShrinkBelowMinimum() {
        val resized = geometry.drag(crop, CropHandle.BOTTOM_RIGHT, -10f, -10f, null)
        assertEquals(geometry.minimumWidth, resized.width, .00001f)
        assertEquals(geometry.minimumHeight, resized.height, .00001f)
        assertEquals(crop.left, resized.left, .00001f)
        assertEquals(crop.top, resized.top, .00001f)
        val moved = geometry.drag(resized, CropHandle.MOVE, 10f, -10f, null)
        assertEquals(1f, moved.right, .00001f)
        assertEquals(0f, moved.top, .00001f)
        assertEquals(resized.width, moved.width, .00001f)
        assertEquals(resized.height, moved.height, .00001f)
    }

    @Test fun lockedCornerAndEdgeResizesPreserveRatioAndOppositeAnchor() {
        val square = geometry.preset(1f)
        val corner = geometry.drag(square, CropHandle.TOP_LEFT, .2f, .2f, 1f)
        assertTrue(corner.width < square.width)
        assertEquals(square.right, corner.right, .00001f)
        assertEquals(square.bottom, corner.bottom, .00001f)
        assertEquals(1f, corner.width * 800 / (corner.height * 600), .0001f)
        val edge = geometry.drag(corner, CropHandle.LEFT, -.1f, 0f, 1f)
        assertEquals(corner.right, edge.right, .00001f)
        assertEquals((corner.top + corner.bottom) / 2, (edge.top + edge.bottom) / 2, .00001f)
        assertEquals(1f, edge.width * 800 / (edge.height * 600), .0001f)
        val maximum = geometry.drag(edge, CropHandle.TOP_LEFT, -4f, -4f, 1f)
        val minimum = geometry.drag(maximum, CropHandle.TOP_LEFT, 4f, 4f, 1f)
        assertTrue(maximum.left >= 0f && maximum.top >= 0f)
        assertEquals(geometry.minimumScale(1f), geometry.scale(minimum, 1f), .0001f)
    }

    @Test fun sliderResizeAndPositionShareTheSameBounds() {
        val resized = geometry.resizeCentered(CropWindow.Full, .5f, .25f)
        assertEquals(CropWindow(.25f, .375f, .75f, .625f), resized)
        val moved = geometry.position(resized, horizontal = 1f, vertical = 0f)
        assertEquals(CropWindow(.5f, 0f, 1f, .25f), moved)
        assertEquals(1f, geometry.horizontalPosition(moved), .00001f)
        assertEquals(0f, geometry.verticalPosition(moved), .00001f)
        val locked = geometry.scaleCentered(geometry.preset(1.5f), 1.5f, .5f)
        assertEquals(1.5f, locked.width * 800 / (locked.height * 600), .0001f)
    }

    @Test fun smallAndExtremeAspectImagesNeverProduceEmptyPixelCrops() {
        for (image in listOf(ImageSize(1, 1), ImageSize(8, 3), ImageSize(1, 10_000), ImageSize(20_000, 2))) {
            val editor = CropEditorGeometry(image)
            for (ratio in listOf(.8f, 1f, 1.5f, 16f / 9)) {
                val preset = editor.preset(ratio)
                val changed = editor.drag(preset, CropHandle.BOTTOM_RIGHT, -10f, -10f, ratio)
                val pixels = changed.pixels(image.width, image.height)
                assertTrue(pixels.width in 1..image.width)
                assertTrue(pixels.height in 1..image.height)
                assertTrue(pixels.left + pixels.width <= image.width)
                assertTrue(pixels.top + pixels.height <= image.height)
            }
            val full = editor.resizeCentered(CropWindow.Full, 0f, 0f)
            assertTrue(full.width >= editor.minimumWidth - .00001f)
            assertTrue(full.height >= editor.minimumHeight - .00001f)
        }
    }

    @Test fun handleHitTestingPrefersEdgesCornersAndInteriorMove() {
        assertEquals(CropHandle.TOP_LEFT, cropHandleAt(crop, 25f, 30f, 200f, 120f, 24f))
        assertEquals(CropHandle.TOP, cropHandleAt(crop, 100f, 30f, 200f, 120f, 24f))
        assertEquals(CropHandle.RIGHT, cropHandleAt(crop, 175f, 60f, 200f, 120f, 24f))
        assertEquals(CropHandle.MOVE, cropHandleAt(crop, 100f, 60f, 200f, 120f, 24f))
        assertNull(cropHandleAt(crop, 199f, 1f, 200f, 120f, 12f))
    }

    @Test fun resizedCropRemainsInHistoryAndRotatesWithOriginalPixels() {
        val resized = geometry.resizeCentered(CropWindow.Full, .5f, .25f)
        val initial = Recipe()
        val edited = initial.copy(crop = resized)
        val rotated = edited.copy(quarterTurns = 1, crop = resized.rotateClockwise())
        val history = EditHistory(initial).commit(edited).commit(rotated)
        val decoded = checkNotNull(EditHistoryCodec.decode(EditHistoryCodec.encode(history)))
        assertEquals(rotated, decoded.current)
        assertEquals(edited, decoded.undo().current)
        assertEquals(rotated, decoded.undo().redo().current)
        assertEquals(ImageSize(400, 150), ExportPlan.create(ImageSize(800, 600), edited, null).output)
        assertEquals(ImageSize(150, 400), ExportPlan.create(ImageSize(800, 600), rotated, null).output)
    }
}
