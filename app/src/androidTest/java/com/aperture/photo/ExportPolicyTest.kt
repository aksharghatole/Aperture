package com.aperture.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.PhotoLibrary
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ExportPolicyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val library = PhotoLibrary(context)

    @Test fun bakesExifOrientationAndCropWithoutCopyingSourceMetadata() {
        val fixture = File.createTempFile("exif-export-", ".jpg", context.cacheDir)
        try {
            val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
            try {
                val pixels = IntArray(80 * 40) { if (it % 80 < 40) Color.RED else Color.BLUE }
                source.setPixels(pixels, 0, 80, 0, 0, 80, 40)
                fixture.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            } finally {
                source.recycle()
            }
            ExifInterface(fixture).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2025:08:01 12:34:56")
                setAttribute(ExifInterface.TAG_F_NUMBER, "28/10")
                setAttribute(ExifInterface.TAG_MAKE, "Fixture Camera")
                setLatLong(38.1, -17.3)
                saveAttributes()
            }
            val sourceBytes = fixture.readBytes()
            val photo = library.import(Uri.fromFile(fixture))
            try {
                val uri = library.export(photo, Recipe(quarterTurns = 1, crop = CropWindow(.125f, 0f, .75f, 1f)))
                try {
                    val output = checkNotNull(resolver.openInputStream(uri)).use { BitmapFactory.decodeStream(it) }
                    try {
                        assertEquals(50, output.width)
                        assertEquals(40, output.height)
                        val left = output.getPixel(6, 20)
                        val right = output.getPixel(43, 20)
                        assertTrue(Color.blue(left) > 220 && Color.red(left) < 35)
                        assertTrue(Color.red(right) > 220 && Color.blue(right) < 35)
                    } finally {
                        output.recycle()
                    }
                    checkNotNull(resolver.openInputStream(uri)).use { stream ->
                        val exif = ExifInterface(stream)
                        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                        assertTrue(orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL)
                        assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                        assertNull(exif.getAttribute(ExifInterface.TAG_F_NUMBER))
                        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
                        assertNull(exif.latLong)
                    }
                } finally {
                    resolver.delete(uri, null, null)
                }
                assertArrayEquals(sourceBytes, photo.file.readBytes())
            } finally {
                library.delete(photo)
            }
        } finally {
            fixture.delete()
        }
    }

    @Test fun convertsAnEmbeddedWideGamutProfileToSrgbPixels() {
        val fixture = File.createTempFile("p3-export-", ".png", context.cacheDir)
        try {
            val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
            val srgb = ColorSpace.get(ColorSpace.Named.SRGB)
            val fill = Color.valueOf(.8f, .5f, .3f, 1f, p3)
            val source = Bitmap.createBitmap(24, 24, Bitmap.Config.RGBA_F16, true, p3)
            try {
                source.eraseColor(fill.pack())
                fixture.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                source.recycle()
            }
            var hasWideProfile = false
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(fixture)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                hasWideProfile = info.colorSpace?.isWideGamut == true
            }.recycle()
            assertTrue("Fixture must carry a wide gamut profile", hasWideProfile)
            val photo = library.import(Uri.fromFile(fixture))
            try {
                val uri = library.export(photo, Recipe())
                try {
                    val output = checkNotNull(resolver.openInputStream(uri)).use { BitmapFactory.decodeStream(it) }
                    try {
                        assertTrue(output.colorSpace?.isSrgb == true)
                        val expected = fill.convert(srgb).toArgb()
                        val pixel = output.getPixel(12, 12)
                        assertTrue(abs(Color.red(pixel) - Color.red(expected)) <= 5)
                        assertTrue(abs(Color.green(pixel) - Color.green(expected)) <= 5)
                        assertTrue(abs(Color.blue(pixel) - Color.blue(expected)) <= 5)
                    } finally {
                        output.recycle()
                    }
                } finally {
                    resolver.delete(uri, null, null)
                }
            } finally {
                library.delete(photo)
            }
        } finally {
            fixture.delete()
        }
    }
}
