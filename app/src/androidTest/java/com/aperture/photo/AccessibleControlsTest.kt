package com.aperture.photo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aperture.photo.ui.LabeledEditSlider
import com.aperture.photo.ui.ScreenHeader
import org.junit.Rule
import org.junit.Test

class AccessibleControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeTextHeaderKeepsBackAndActionsAccessible() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MaterialTheme {
                    Column(Modifier.width(300.dp)) {
                        ScreenHeader("Darkroom", "Library", {}) {
                            TextButton(onClick = {}) { Text("Export original") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("‹ Library").assertHasClickAction().assertIsDisplayed()
        compose.onNodeWithText("Export original").assertHasClickAction().assertIsDisplayed()
    }

    @Test fun adjustmentHasLabelAndAccessibleProgressAction() {
        compose.setContent {
            MaterialTheme {
                var value by remember { mutableFloatStateOf(0f) }
                LabeledEditSlider("Exposure", value, -2f..2f, { value = it }, {})
            }
        }
        val slider = compose.onNodeWithContentDescription("Exposure")
        slider.assertIsDisplayed()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithText("Exposure · " + String.format(java.util.Locale.getDefault(), "%.2f", 1f))
            .assertIsDisplayed()
    }
}
