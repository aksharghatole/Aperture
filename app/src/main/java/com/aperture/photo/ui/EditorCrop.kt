package com.aperture.photo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.aperture.photo.imaging.CropWindow

private enum class CropRatio(val label: String, val aspect: Float?) {
    FULL("Full", null), SQUARE("1:1", 1f), PORTRAIT("4:5", .8f),
    CLASSIC("3:2", 1.5f), WIDE("16:9", 16f / 9f)
}

@Composable
internal fun EditorCrop(
    prepared: Result<Bitmap>?,
    initial: CropWindow,
    onCancel: () -> Unit,
    onApply: (CropWindow) -> Unit
) {
    var crop by remember(initial) { mutableStateOf(initial) }
    var selected by remember(initial) { mutableStateOf(if (initial == CropWindow.Full) CropRatio.FULL else null) }
    val preview = prepared?.getOrNull()
    val previewHeight = (LocalConfiguration.current.screenHeightDp * .36f).dp.coerceAtMost(240.dp)
    val aspect = preview?.let { it.width.toFloat() / it.height } ?: 1f
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Crop photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (preview == null) {
                    Text(if (prepared?.isFailure == true) "Could not prepare the crop preview." else "Preparing photo…")
                } else {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val imageWidth = maxWidth.coerceAtMost(previewHeight * aspect)
                        Box(Modifier.width(imageWidth).aspectRatio(aspect)) {
                            Image(
                                preview.asImageBitmap(), "Full photo for cropping",
                                Modifier.matchParentSize(), contentScale = ContentScale.FillBounds
                            )
                            Canvas(Modifier.matchParentSize().pointerInput(preview) {
                                detectDragGestures { change, delta ->
                                    change.consume()
                                    crop = crop.move(delta.x / size.width, delta.y / size.height)
                                }
                            }) {
                                val left = crop.left * size.width
                                val top = crop.top * size.height
                                val right = crop.right * size.width
                                val bottom = crop.bottom * size.height
                                val shade = Color.Black.copy(alpha = .62f)
                                drawRect(shade, size = Size(size.width, top))
                                drawRect(shade, Offset(0f, bottom), Size(size.width, size.height - bottom))
                                drawRect(shade, Offset(0f, top), Size(left, bottom - top))
                                drawRect(shade, Offset(right, top), Size(size.width - right, bottom - top))
                                drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top),
                                    style = Stroke(1.dp.toPx()))
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CropRatio.entries.forEach { ratio ->
                            FilterChip(
                                selected = selected == ratio,
                                onClick = {
                                    selected = ratio
                                    crop = ratio.aspect?.let { CropWindow.centered(aspect, it) } ?: CropWindow.Full
                                },
                                label = { Text(ratio.label) }
                            )
                        }
                    }
                    Text("Drag to reposition the crop.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(crop) }, enabled = preview != null) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
