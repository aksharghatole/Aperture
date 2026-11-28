package com.aperture.photo

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.ui.ImportResult
import com.aperture.photo.ui.PhotoOperations
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiImportTest {
    @Test fun keepsGoodSourcesWhenAnotherFileFailsAndImportsDuplicateSelectionOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cache = instrumentation.targetContext.cacheDir
        val first = File.createTempFile("batch-first-", ".jpg", cache)
        val broken = File.createTempFile("batch-broken-", ".jpg", cache)
        val last = File.createTempFile("batch-last-", ".png", cache)
        try {
            val jpeg = instrumentation.context.assets.open("formats/still.jpg").use { it.readBytes() }
            val png = instrumentation.context.assets.open("formats/transparent.png").use { it.readBytes() }
            first.writeBytes(jpeg)
            broken.writeText("not an image")
            last.writeBytes(png)
            val pendingBefore = cache.list().orEmpty().filter { it.startsWith("capture-") }.toSet()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var operations: PhotoOperations
                scenario.onActivity {
                    operations = ViewModelProvider(it)[PhotoOperations::class.java]
                    operations.importPhotos(listOf(first, broken, first, last).map(Uri::fromFile))
                }
                val result = awaitResult(scenario, operations)
                try {
                    assertEquals(listOf(first.name, broken.name, last.name), result.outcomes.map { it.name })
                    assertEquals(2, result.imported)
                    assertEquals(1, result.failed)
                    assertEquals(0, result.skipped)
                    assertNull(result.openSingle)
                    assertTrue(result.outcomes[1].error.orEmpty().contains("damaged"))
                    assertArrayEquals(jpeg, requireNotNull(result.outcomes[0].photo).file.readBytes())
                    assertArrayEquals(png, requireNotNull(result.outcomes[2].photo).file.readBytes())
                    assertEquals(pendingBefore, cache.list().orEmpty().filter { it.startsWith("capture-") }.toSet())
                } finally {
                    result.outcomes.mapNotNull { it.photo }.forEach { operations.library.delete(it) }
                }
            }
        } finally {
            listOf(first, broken, last).forEach(File::delete)
        }
    }

    private fun awaitResult(scenario: ActivityScenario<MainActivity>, operations: PhotoOperations): ImportResult {
        var result: ImportResult? = null
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (result == null && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { result = operations.importResult.takeIf { operations.importProgress == null } }
            if (result == null) SystemClock.sleep(20)
        }
        return checkNotNull(result) { "Batch import did not finish" }
    }
}
