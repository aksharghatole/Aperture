package com.aperture.photo

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.ImageSize
import com.aperture.photo.ui.EditorCrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CropControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun accessibleSlidersResizeAndMoveCropWithoutDragging() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        var applied: CropWindow? = null
        val visible = mutableStateOf(true)
        try {
            compose.setContent {
                MaterialTheme {
                    if (visible.value) EditorCrop(Result.success(bitmap), Result.success(ImageSize(200, 100)),
                        CropWindow.Full, { visible.value = false }, { applied = it; visible.value = false })
                }
            }
            compose.onNodeWithContentDescription("Crop width").performScrollTo().assertIsEnabled()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
            compose.onNodeWithContentDescription("Horizontal position").performScrollTo().assertIsEnabled()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
            compose.onNodeWithText("Apply").performClick()
            compose.runOnIdle {
                val crop = checkNotNull(applied)
                assertEquals(.5f, crop.width, .0001f)
                assertEquals(1f, crop.right, .0001f)
                assertEquals(1f, crop.height, .0001f)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
