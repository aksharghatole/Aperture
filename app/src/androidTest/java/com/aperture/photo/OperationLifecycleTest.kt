package com.aperture.photo

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.PhotoLibrary
import com.aperture.photo.ui.PhotoOperations
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationLifecycleTest {
    @Test fun interruptedCaptureReportsFailureAndCleansUpAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var retained: PhotoOperations
            lateinit var pending: File
            lateinit var finishCapture: (Boolean) -> Unit
            scenario.onActivity { activity ->
                retained = ViewModelProvider(activity)[PhotoOperations::class.java]
                retained.capture(Film.CLEAN) { file, complete ->
                    pending = file
                    finishCapture = complete
                }
                assertTrue(retained.captureBusy)
                retained.capture(Film.CLEAN) { _, _ -> fail("A second capture started") }
            }
            try {
                scenario.recreate()
                scenario.onActivity { activity ->
                    val recreated = ViewModelProvider(activity)[PhotoOperations::class.java]
                    assertSame(retained, recreated)
                    assertTrue(recreated.captureBusy)
                    finishCapture(false)
                    finishCapture(true)
                    assertFalse(recreated.captureBusy)
                    assertTrue(recreated.captureMessage.orEmpty().contains("interrupted"))
                    assertFalse(pending.exists())
                    assertEquals(0, recreated.libraryRevision)
                }
            } finally {
                retained.library.discardCapture(pending)
            }
        }
    }

    @Test fun exportReceiptSurvivesActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = PhotoLibrary(context)
        val input = library.beginCapture()
        val bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.GRAY)
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally {
            bitmap.recycle()
        }
        val photo = library.commitCapture(input, Film.CLEAN)
        val recipe = Recipe(exposure = .5f)
        var exported: Uri? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    ViewModelProvider(activity)[PhotoOperations::class.java].export(photo, recipe)
                }
                scenario.recreate()
                val deadline = SystemClock.elapsedRealtime() + 15_000L
                while (exported == null && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { activity ->
                        val result = ViewModelProvider(activity)[PhotoOperations::class.java].editorExport
                        assertEquals(photo, result?.photo)
                        if (result?.running == false) {
                            exported = result.result?.matching(recipe)
                            assertNotNull(result.message ?: "Export receipt missing", exported)
                            assertNull(result.result?.matching(Recipe()))
                        }
                    }
                    if (exported == null) SystemClock.sleep(50L)
                }
                assertNotNull(exported)
            }
        } finally {
            exported?.let { context.contentResolver.delete(it, null, null) }
            library.delete(photo)
        }
    }
}
