package com.aperture.photo

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.library.readPhotoInformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhotoInformationTest {
    @Test
    fun readsDimensionsAndReportsMissingExifAsUnavailable() {
        val file = fixture("plain.jpg")
        val info = readPhotoInformation(file)
        assertEquals("24 × 12", info.dimensions)
        assertEquals("image/jpeg", info.format)
        assertNull(info.captureDate)
        assertNull(info.orientation)
        file.delete()
    }

    @Test
    fun readsExifCaptureFieldsAndRotation() {
        val file = fixture("with-exif.jpg")
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2024:02:03 04:05:06")
            setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/125")
            setAttribute(ExifInterface.TAG_F_NUMBER, "2.8")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "50/1")
            setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "400")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val info = readPhotoInformation(file)
        assertEquals("2024:02:03 04:05:06", info.captureDate)
        assertEquals("1/125 s", info.exposure)
        assertEquals("f/2.8", info.aperture)
        assertEquals("50/1 mm", info.focalLength)
        assertEquals("400", info.iso)
        assertEquals("Rotated 90°", info.orientation)
        assertTrue(info.fileDate.isNotBlank())
        file.delete()
    }

    private fun fixture(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(24, 12, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output) }
        bitmap.recycle()
        return file
    }
}
