package com.aperture.photo

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.camera.ReferenceAlignment
import com.aperture.photo.ui.CameraReferenceState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraReferenceLifecycleTest {
    @Test fun retainsOverlayAndSettingsAcrossRecreationAndFailedReplacement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File.createTempFile("reference-test", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(1600, 100, Bitmap.Config.ARGB_8888)
        try {
            input.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var reference: CameraReferenceState
                scenario.onActivity {
                    reference = ViewModelProvider(it)[CameraReferenceState::class.java]
                    reference.load(Uri.fromFile(input))
                }
                awaitLoad(scenario, reference)
                val alignment = ReferenceAlignment.of(.25f, -.1f, 1.5f)
                scenario.onActivity {
                    assertNotNull(reference.error.orEmpty(), reference.image)
                    assertTrue(reference.image!!.width <= 1280)
                    reference.opacity = .6f
                    reference.alignment = alignment
                }
                scenario.recreate()
                scenario.onActivity {
                    assertSame(reference, ViewModelProvider(it)[CameraReferenceState::class.java])
                    assertEquals(.6f, reference.opacity, 0f)
                    assertEquals(alignment, reference.alignment)
                    assertTrue(input.delete())
                    reference.load(Uri.fromFile(input))
                }
                awaitLoad(scenario, reference)
                scenario.onActivity {
                    assertNotNull(reference.error)
                    assertNotNull(reference.image)
                    assertEquals(alignment, reference.alignment)
                    reference.remove()
                    assertNull(reference.image)
                    assertNull(reference.error)
                    assertEquals(ReferenceAlignment.Centered, reference.alignment)
                }
            }
        } finally {
            input.delete()
        }
    }

    private fun awaitLoad(scenario: ActivityScenario<MainActivity>, reference: CameraReferenceState) {
        var loading = true
        val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
        while (loading && android.os.SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { loading = reference.loading }
            if (loading) android.os.SystemClock.sleep(20)
        }
        assertTrue("Reference decode did not finish", !loading)
    }
}
