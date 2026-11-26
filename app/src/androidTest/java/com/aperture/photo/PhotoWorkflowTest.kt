package com.aperture.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.PhotoLibrary
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoWorkflowTest {
    @Test fun originalSurvivesFilmEditAndFullResolutionExport() {
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
            val uri = library.export(photo, Recipe(photo.film, quarterTurns = 1))
            try {
                context.contentResolver.openInputStream(uri)!!.use {
                    val output = BitmapFactory.decodeStream(it)
                    try {
                        assertEquals(640, output.width)
                        assertEquals(960, output.height)
                        val pixel = output.getPixel(320, 480)
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
            photo.file.delete()
        }
    }
}
