package com.aperture.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.imaging.PhotoRenderer
import com.aperture.photo.imaging.RgbCurves
import com.aperture.photo.imaging.ToneCurve
import com.aperture.photo.library.PhotoLibrary
import com.aperture.photo.library.SavedEditException
import com.aperture.photo.library.SavedEditProblem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoWorkflowTest {
    @Test fun invalidEditFilesArePreservedUntilExplicitReplacement() {
        val library = PhotoLibrary(InstrumentationRegistry.getInstrumentation().targetContext)
        val pending = library.beginCapture().apply { writeText("original") }
        val photo = library.commitCapture(pending, Film.CLEAN)
        val sidecar = File(photo.file.parentFile, "${photo.file.name}.edits")
        val replacement = EditHistory(Recipe(exposure = .5f))
        try {
            for ((encoded, expected) in listOf(
                "4,0\nCLEAN,0.0" to SavedEditProblem.CORRUPT,
                "99,0\nfuture-format" to SavedEditProblem.UNSUPPORTED
            )) {
                sidecar.writeText(encoded)
                assertEquals(expected, assertThrows(SavedEditException::class.java) { library.loadEdits(photo) }.problem)
                assertThrows(SavedEditException::class.java) { library.exportSavedEdits(photo) }
                assertThrows(SavedEditException::class.java) { library.saveEdits(photo, replacement) }
                assertEquals(encoded, sidecar.readText())
                library.saveEdits(photo, replacement, expected)
                assertEquals(replacement.current, library.loadEdits(photo)?.current)
            }
            assertTrue(sidecar.delete())
            assertTrue(sidecar.mkdir())
            assertEquals(SavedEditProblem.UNREADABLE,
                assertThrows(SavedEditException::class.java) { library.loadEdits(photo) }.problem)
            assertThrows(SavedEditException::class.java) {
                library.saveEdits(photo, replacement, SavedEditProblem.UNREADABLE)
            }
            assertTrue(sidecar.isDirectory)
            assertEquals("original", photo.file.readText())
        } finally {
            sidecar.delete()
            library.delete(photo)
        }
    }

    @Test fun rendererAppliesMasterAndChannelCurvesToOutput() {
        val source = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.rgb(128, 128, 128))
        source.setPixel(1, 0, Color.rgb(0, 0, 0))
        try {
            val recipe = Recipe(curves = RgbCurves(master = ToneCurve(midtone = 64), red = ToneCurve(midtone = 192)))
            val rendered = PhotoRenderer.render(source, recipe)
            try {
                assertEquals(Color.rgb(96, 64, 64), rendered.getPixel(0, 0))
                assertEquals(Color.BLACK, rendered.getPixel(1, 0))
                assertEquals(Color.rgb(128, 128, 128), source.getPixel(0, 0))
            } finally {
                rendered.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    @Test fun deletingPhotoWithoutSavedEditsRemovesOriginal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = PhotoLibrary(context)
        val pending = library.beginCapture()
        val source = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.BLUE)
        try {
            pending.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
        } finally {
            source.recycle()
        }
        val photo = library.commitCapture(pending, Film.CLEAN)
        try {
            library.delete(photo)
            assertTrue(!photo.file.exists())
            assertTrue(!File(photo.file.parentFile, "${photo.file.name}.edits").exists())
            assertTrue(!library.all().contains(photo))
        } finally {
            photo.file.delete()
            File(photo.file.parentFile, "${photo.file.name}.edits").delete()
        }
    }

    @Test fun deletingPhotoWithSavedEditsRemovesOriginalAndHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = PhotoLibrary(context)
        val pending = library.beginCapture()
        val source = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        try {
            pending.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
        } finally {
            source.recycle()
        }
        val photo = library.commitCapture(pending, Film.CLEAN)
        try {
            library.saveEdits(photo, EditHistory(Recipe(photo.film)))
            assertTrue(File(photo.file.parentFile, "${photo.file.name}.edits").exists())
            library.delete(photo)
            assertTrue(!photo.file.exists())
            assertTrue(!File(photo.file.parentFile, "${photo.file.name}.edits").exists())
            assertTrue(!library.all().contains(photo))
        } finally {
            photo.file.delete()
            File(photo.file.parentFile, "${photo.file.name}.edits").delete()
        }
    }

    @Test fun originalSurvivesSavedEditsAndFullResolutionExport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = PhotoLibrary(context)
        val pending = library.beginCapture()
        val source = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(224, 80, 23))
        try {
            pending.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
        } finally {
            source.recycle()
        }
        val bytes = pending.readBytes()
        val photo = library.commitCapture(pending, Film.SILVER)
        try {
            assertTrue(library.all().contains(photo))
            assertNull(library.loadEdits(photo))
            val originalThumbnail = library.thumbnail(photo)
            try {
                assertTrue(originalThumbnail.width > originalThumbnail.height)
            } finally {
                originalThumbnail.recycle()
            }
            val initial = Recipe(photo.film)
            val rotated = initial.copy(quarterTurns = 1, crop = CropWindow(.25f, .125f, .75f, .875f))
            val adjusted = rotated.copy(exposure = .5f)
            library.saveEdits(photo, EditHistory(initial).commit(rotated).commit(adjusted).undo())
            val reloaded = checkNotNull(PhotoLibrary(context).loadEdits(photo))
            assertEquals(rotated, reloaded.current)
            val editedThumbnail = library.thumbnail(photo)
            try {
                assertTrue(editedThumbnail.height > editedThumbnail.width)
            } finally {
                editedThumbnail.recycle()
            }
            assertEquals(initial, reloaded.undo().current)
            assertEquals(adjusted, reloaded.redo().current)
            assertEquals(listOf(photo), library.all().filter { it.file.name.startsWith(photo.file.name) })
            val uri = library.exportSavedEdits(photo)
            try {
                context.contentResolver.openInputStream(uri)!!.use {
                    val output = BitmapFactory.decodeStream(it)
                    try {
                        assertEquals(320, output.width)
                        assertEquals(720, output.height)
                        val pixel = output.getPixel(160, 360)
                        assertEquals(Color.red(pixel), Color.green(pixel))
                        assertEquals(Color.green(pixel), Color.blue(pixel))
                    } finally {
                        output.recycle()
                    }
                }
                val sidecar = File(photo.file.parentFile, "${photo.file.name}.edits")
                sidecar.writeText("invalid")
                val fallback = library.thumbnail(photo)
                try {
                    assertTrue(fallback.width > fallback.height)
                } finally {
                    fallback.recycle()
                }
                assertArrayEquals(bytes, photo.file.readBytes())
            } finally {
                context.contentResolver.delete(uri, null, null)
            }
        } finally {
            File(photo.file.parentFile, "${photo.file.name}.edits").delete()
            photo.file.delete()
        }
    }
}
