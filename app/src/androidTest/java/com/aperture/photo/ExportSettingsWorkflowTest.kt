package com.aperture.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.ExportSettings
import com.aperture.photo.imaging.ImageSize
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.PhotoLibrary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ExportSettingsWorkflowTest {
    @Test fun resizedExportsUsePostCropDimensionsAndQualityWithoutChangingOriginalOrHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = PhotoLibrary(context)
        val resolver = context.contentResolver
        val fixture = File.createTempFile("export-options", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888)
        try {
            val random = Random(123)
            val pixels = IntArray(720 * 480) {
                val value = random.nextInt(256)
                Color.rgb(value, (value + 40) % 256, (value + 90) % 256)
            }
            bitmap.setPixels(pixels, 0, 720, 0, 0, 720, 480)
            fixture.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            ExifInterface(fixture).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val recipe = Recipe(quarterTurns = 1, crop = CropWindow(.125f, .25f, .875f, .75f))
            val photo = library.import(Uri.fromFile(fixture))
            val original = photo.file.readBytes()
            try {
                library.saveEdits(photo, EditHistory(Recipe()).commit(recipe))
                val sidecar = File(photo.file.parentFile, "${photo.file.name}.edits")
                val saved = sidecar.readBytes()
                assertEquals(ImageSize(540, 240), library.exportDimensions(photo))
                val outputs = mutableListOf<Uri>()
                try {
                    val high = library.export(photo, recipe, ExportSettings(333, 96)).also(outputs::add)
                    val low = library.exportSavedEdits(photo, ExportSettings(333, 15)).also(outputs::add)
                    val noUpscale = library.exportSavedEdits(photo, ExportSettings(1000, 50)).also(outputs::add)
                    fun jpeg(uri: Uri, width: Int, height: Int): Int {
                        val bytes = checkNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
                        val decoded = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                        try {
                            assertEquals(width, decoded.width)
                            assertEquals(height, decoded.height)
                        } finally { decoded.recycle() }
                        return bytes.size
                    }
                    val highBytes = jpeg(high, 333, 148)
                    val lowBytes = jpeg(low, 333, 148)
                    assertTrue("Quality should affect JPEG encoding", lowBytes < highBytes)
                    jpeg(noUpscale, 540, 240)
                    assertArrayEquals(original, photo.file.readBytes())
                    assertArrayEquals(saved, sidecar.readBytes())
                    assertEquals(recipe, library.loadEdits(photo)?.current)
                } finally {
                    outputs.forEach { resolver.delete(it, null, null) }
                }
            } finally {
                library.delete(photo)
            }
        } finally {
            bitmap.recycle()
            fixture.delete()
        }
    }
}
