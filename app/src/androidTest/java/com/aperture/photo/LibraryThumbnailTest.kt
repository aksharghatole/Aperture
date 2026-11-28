package com.aperture.photo

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.PhotoLibrary
import com.aperture.photo.library.ThumbnailCache
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryThumbnailTest {
    @Test fun cacheEvictsLeastRecentlyUsedWithoutRecyclingDisplayedBitmaps() = runBlocking {
        val cache = ThumbnailCache(32 * 1024)
        fun image() = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val a = cache.load(File("a"), ::image)
        val b = cache.load(File("b"), ::image)
        assertSame(a, cache.load(File("a"), ::image))
        cache.load(File("c"), ::image)
        assertFalse(requireNotNull(b.bitmap).isRecycled)
        assertSame(a, cache.load(File("a"), ::image))
        assertNotSame(b, cache.load(File("b"), ::image))
    }

    @Test fun invalidationDuringDecodeDiscardsTheObsoleteResult() = runBlocking {
        val cache = ThumbnailCache()
        val name = File("changed")
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        var loads = 0
        val request = async {
            cache.load(name) {
                val first = ++loads == 1
                if (first) {
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                }
                Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(if (first) Color.BLACK else Color.WHITE)
                }
            }
        }
        try {
            withTimeout(5_000) { started.await() }
            cache.invalidate(name)
        } finally {
            release.countDown()
        }
        val updated = request.await()
        assertEquals(Color.WHITE, updated.bitmap?.getPixel(0, 0))
        assertEquals(2, loads)
        assertSame(updated, cache.load(name) { error("Expected a cached bitmap") })
    }

    @Test fun savedEditsAndRemovalInvalidateTheLibraryCache() = runBlocking {
        withLibrary { library, root, bytes ->
            val fixture = File(root, "source.jpg").apply { writeBytes(bytes) }
            val photo = library.import(Uri.fromFile(fixture))
            val first = library.thumbnailPreview(photo)
            assertTrue(first.bitmap != null)
            assertSame(first, library.thumbnailPreview(photo))
            library.saveEdits(photo, EditHistory(Recipe(exposure = -2f)))
            val edited = library.thumbnailPreview(photo)
            assertNotSame(first.bitmap, edited.bitmap)
            assertTrue(edited.bitmap != null)
            library.delete(photo)
            assertNull(library.thumbnailPreview(photo).bitmap)
            assertFalse(requireNotNull(first.bitmap).isRecycled)
        }
    }

    @Test fun profilesOpeningAndScrollingAtThreeLibrarySizes() = runBlocking {
        for (count in listOf(100, 1_000, 5_000)) withLibrary { library, root, fixture ->
            val directory = File(root, "files/photos")
            repeat(count) { index -> File(directory, "$index-fixture_CLEAN.photo").writeBytes(fixture) }
            val started = SystemClock.elapsedRealtime()
            val photos = library.all()
            val listed = SystemClock.elapsedRealtime()
            assertEquals(count, photos.size)
            val windowSize = 12
            val first = photos.take(windowSize).map { library.thumbnailPreview(it) }
            val opened = SystemClock.elapsedRealtime()
            assertTrue(first.all { it.bitmap != null })
            // Simulate scrolling by loading a viewport at evenly spaced positions.
            for (step in 1..8) {
                val index = (photos.size - windowSize) * step / 8
                for (photo in photos.subList(index, index + windowSize)) {
                    assertTrue(library.thumbnailPreview(photo).bitmap != null)
                }
            }
            val scrolled = SystemClock.elapsedRealtime()
            Log.i("ApertureThumbnailProfile", "$count photos, list ${listed - started} ms, first viewport " +
                "${opened - listed} ms, 8 viewport loads ${scrolled - opened} ms")
        }
    }

    private suspend fun withLibrary(block: suspend (PhotoLibrary, File, ByteArray) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val root = kotlin.io.path.createTempDirectory(target.cacheDir.toPath(), "thumb-profile-").toFile()
        try {
            val isolated = object : ContextWrapper(target) {
                override fun getFilesDir() = File(root, "files").apply { mkdirs() }
                override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            }
            val fixture = instrumentation.context.assets.open("formats/still.jpg").use { it.readBytes() }
            block(PhotoLibrary(isolated), root, fixture)
        } finally {
            root.deleteRecursively()
        }
    }
}
