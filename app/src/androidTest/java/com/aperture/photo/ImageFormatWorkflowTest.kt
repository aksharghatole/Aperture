package com.aperture.photo

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.imaging.ImageInputException
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.PhotoLibrary
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageFormatWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val library = PhotoLibrary(context)

    @Test fun importsTransparentStillsAndFlattensWithWhiteWithoutChangingSource() {
        for (name in listOf("transparent.png", "transparent.webp")) {
            withFixture(name) { fixture, original ->
                val photo = library.import(Uri.fromFile(fixture))
                try {
                    val exported = library.export(photo, Recipe(vignette = 1f))
                    try {
                        val jpeg = context.contentResolver.openInputStream(exported)!!.use { BitmapFactory.decodeStream(it) }
                        try {
                            val matte = jpeg.getPixel(2, 2)
                            assertTrue("White matte for $name",
                                Color.red(matte) >= 248 && Color.green(matte) >= 248 && Color.blue(matte) >= 248)
                            val opaque = jpeg.getPixel(24, 16)
                            assertTrue("Opaque content for $name", Color.red(opaque) > Color.green(opaque) + 60)
                        } finally {
                            jpeg.recycle()
                        }
                    } finally {
                        context.contentResolver.delete(exported, null, null)
                    }
                    assertArrayEquals(original, photo.file.readBytes())
                } finally {
                    library.delete(photo)
                }
            }
        }
    }

    @Test fun preservesStandardJpegAndRejectsAnimationsAndDamagedFiles() {
        withFixture("still.jpg") { fixture, original ->
            val photo = library.import(Uri.fromFile(fixture))
            try {
                assertArrayEquals(original, photo.file.readBytes())
            } finally {
                library.delete(photo)
            }
        }
        val beforeRejectedImports = library.all()
        for (name in listOf("animated.gif", "animated.webp")) {
            withFixture(name) { fixture, _ ->
                val failure = assertThrows(ImageInputException::class.java) { library.import(Uri.fromFile(fixture)) }
                assertTrue(failure.message.orEmpty().contains("Animated"))
            }
        }

        val corrupt = File.createTempFile("format-test-", ".jpg", context.cacheDir)
        try {
            corrupt.writeText("This is not an image.")
            val failure = assertThrows(ImageInputException::class.java) { library.import(Uri.fromFile(corrupt)) }
            assertTrue(failure.message.orEmpty().contains("damaged"))
        } finally {
            corrupt.delete()
        }
        assertEquals(beforeRejectedImports, library.all())
    }

    private fun withFixture(name: String, check: (File, ByteArray) -> Unit) {
        val bytes = instrumentation.context.assets.open("formats/$name").use { it.readBytes() }
        val file = File.createTempFile("format-test-", ".${name.substringAfterLast('.')}", context.cacheDir)
        try {
            file.writeBytes(bytes)
            check(file, bytes)
        } finally {
            file.delete()
        }
    }
}
