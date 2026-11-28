package com.aperture.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.imaging.CropEditorGeometry
import com.aperture.photo.imaging.CropHandle
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.ImageSize
import com.aperture.photo.imaging.PhotoRenderer
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.PhotoLibrary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CropResizeWorkflowTest {
    @Test fun resizedCropMatchesPreviewPixelsAndFullResolutionExportAfterRotation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = PhotoLibrary(context)
        val fixture = File.createTempFile("crop-resize", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(800 * 600) { index ->
                val x = index % 800
                val y = index / 800
                if (x < 400) if (y < 300) Color.RED else Color.GREEN
                else if (y < 300) Color.BLUE else Color.YELLOW
            }
            bitmap.setPixels(pixels, 0, 800, 0, 0, 800, 600)
            fixture.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val photo = library.import(Uri.fromFile(fixture))
            val original = photo.file.readBytes()
            try {
                val originalSize = library.exportDimensions(photo, Recipe(quarterTurns = 1))
                assertEquals(ImageSize(600, 800), originalSize)
                val geometry = CropEditorGeometry(originalSize)
                val resized = geometry.drag(CropWindow.Full, CropHandle.TOP_LEFT, .25f, .25f, null)
                val crop = geometry.drag(resized, CropHandle.BOTTOM_RIGHT, -.25f, -.25f, null)
                val recipe = Recipe(quarterTurns = 1, crop = crop)
                library.saveEdits(photo, EditHistory(Recipe()).commit(recipe))
                val source = PhotoRenderer.decode(photo.file, 400)
                val preview = PhotoRenderer.render(source, recipe)
                val uri = library.exportSavedEdits(photo)
                try {
                    assertEquals(ImageSize(300, 400), library.exportDimensions(photo))
                    val exported = checkNotNull(context.contentResolver.openInputStream(uri)).use {
                        checkNotNull(BitmapFactory.decodeStream(it))
                    }
                    try {
                        assertEquals(300, exported.width)
                        assertEquals(400, exported.height)
                        assertEquals(150, preview.width)
                        assertEquals(200, preview.height)
                        for (x in listOf(.25f, .75f)) for (y in listOf(.25f, .75f)) {
                            val expected = preview.getPixel((x * preview.width).toInt(), (y * preview.height).toInt())
                            val actual = exported.getPixel((x * exported.width).toInt(), (y * exported.height).toInt())
                            assertTrue(kotlin.math.abs(Color.red(expected) - Color.red(actual)) < 10)
                            assertTrue(kotlin.math.abs(Color.green(expected) - Color.green(actual)) < 10)
                            assertTrue(kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) < 10)
                        }
                        assertArrayEquals(original, photo.file.readBytes())
                        assertEquals(recipe, library.loadEdits(photo)?.current)
                    } finally { exported.recycle() }
                } finally {
                    context.contentResolver.delete(uri, null, null)
                    preview.recycle()
                    source.recycle()
                }
            } finally { library.delete(photo) }
        } finally {
            fixture.delete()
            bitmap.recycle()
        }
    }
}
