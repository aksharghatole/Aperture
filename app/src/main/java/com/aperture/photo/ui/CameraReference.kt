package com.aperture.photo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aperture.photo.camera.ReferenceAlignment
import kotlin.math.roundToInt

@Composable
internal fun CameraReferenceDialog(
    reference: CameraReferenceState,
    canAlign: Boolean,
    dismiss: () -> Unit,
    align: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(reference::load)
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Reference photo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    reference.loading -> Text("Loading reference…")
                    reference.error != null -> Text(reference.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    reference.image == null -> Text("Choose a photo to overlay on the viewfinder.")
                }
                if (reference.image != null) {
                    val current = reference.alignment
                    ReferenceSlider("Opacity", reference.opacity, 0.1f..0.8f) { reference.opacity = it }
                    ReferenceSlider("Scale", current.scale, ReferenceAlignment.ScaleRange) {
                        reference.alignment = ReferenceAlignment.of(reference.alignment.x, reference.alignment.y, it)
                    }
                    ReferenceSlider("Horizontal position", current.x, ReferenceAlignment.OffsetRange) {
                        reference.alignment = ReferenceAlignment.of(it, reference.alignment.y, reference.alignment.scale)
                    }
                    ReferenceSlider("Vertical position", current.y, ReferenceAlignment.OffsetRange) {
                        reference.alignment = ReferenceAlignment.of(reference.alignment.x, it, reference.alignment.scale)
                    }
                    TextButton(onClick = align, enabled = canAlign && !reference.loading) {
                        Text("Align in viewfinder")
                    }
                    TextButton(onClick = { reference.alignment = ReferenceAlignment.Centered }) {
                        Text("Reset alignment")
                    }
                    TextButton(onClick = {
                        reference.remove()
                        dismiss()
                    }) { Text("Remove reference") }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { picker.launch("image/*") }, enabled = !reference.loading) {
                Text(if (reference.image == null) "Choose photo" else "Replace photo")
            }
        }
    )
}

@Composable
private fun ReferenceSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
    Text("$label ${(value * 100).roundToInt()}%")
    Slider(value = value, onValueChange = change, valueRange = range,
        modifier = Modifier.semantics { contentDescription = label })
}

@Composable
internal fun CameraReferenceOverlay(
    image: ImageBitmap,
    alignment: ReferenceAlignment,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    val painter = remember(image) { BitmapPainter(image) }
    Canvas(modifier) {
        val bounds = alignment.bounds(image.width, image.height, size.width, size.height)
        translate(bounds.left, bounds.top) {
            with(painter) { draw(Size(bounds.width, bounds.height), alpha = opacity) }
        }
    }
}
