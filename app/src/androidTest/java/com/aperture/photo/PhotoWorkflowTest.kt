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
import com.aperture.photo.library.PhotoLibrary
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoWorkflowTest {
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
            val initial = Recipe(photo.film)
            val rotated = initial.copy(quarterTurns = 1, crop = CropWindow(.25f, .125f, .75f, .875f))
            val adjusted = rotated.copy(exposure = .5f)
            library.saveEdits(photo, EditHistory(initial).commit(rotated).commit(adjusted).undo())
            val reloaded = checkNotNull(PhotoLibrary(context).loadEdits(photo))
            assertEquals(rotated, reloaded.current)
            assertEquals(initial, reloaded.undo().current)
            assertEquals(adjusted, reloaded.redo().current)
            assertEquals(listOf(photo), library.all().filter { it.file.name.startsWith(photo.file.name) })
            val uri = library.export(photo, reloaded.current)
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
