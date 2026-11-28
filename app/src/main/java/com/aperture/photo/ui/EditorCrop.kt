package com.aperture.photo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.aperture.photo.imaging.CropEditorGeometry
import com.aperture.photo.imaging.CropHandle
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.ImageSize
import com.aperture.photo.imaging.cropHandleAt
import kotlin.math.roundToInt

private enum class CropRatio(val label: String, val aspect: Float?) {
    FREE("Freeform", null), SQUARE("1:1", 1f), PORTRAIT("4:5", .8f),
    CLASSIC("3:2", 1.5f), WIDE("16:9", 16f / 9f)
}

private val CropSaver = listSaver<CropWindow, Float>(
    save = { listOf(it.left, it.top, it.right, it.bottom) },
    restore = { CropWindow(it[0], it[1], it[2], it[3]) }
)

@Composable
internal fun EditorCrop(
    prepared: Result<Bitmap>?,
    originalSize: Result<ImageSize>?,
    initial: CropWindow,
    onCancel: () -> Unit,
    onApply: (CropWindow) -> Unit
) {
    var crop by rememberSaveable(initial, stateSaver = CropSaver) { mutableStateOf(initial) }
    var selected by rememberSaveable(initial) { mutableStateOf(CropRatio.FREE) }
    val preview = prepared?.getOrNull()
    val dimensions = originalSize?.getOrNull()
    val geometry = remember(dimensions) { dimensions?.let(::CropEditorGeometry) }
    val previewHeight = (LocalConfiguration.current.screenHeightDp * .36f).dp.coerceAtMost(240.dp)
    val previewAspect = preview?.let { it.width.toFloat() / it.height } ?: 1f
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Crop photo") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (preview == null || geometry == null || dimensions == null) {
                    Text(when {
                        prepared?.isFailure == true -> "Could not prepare the crop preview."
                        originalSize?.isFailure == true -> "Could not read original photo dimensions."
                        else -> "Preparing photo…"
                    })
                } else {
                    val ratio = selected.aspect
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val imageWidth = maxWidth.coerceAtMost(previewHeight * previewAspect).coerceAtLeast(1.dp)
                        Box(Modifier.width(imageWidth).aspectRatio(previewAspect)) {
                            Image(
                                preview.asImageBitmap(), "Full photo for cropping",
                                Modifier.matchParentSize(), contentScale = ContentScale.FillBounds
                            )
                            Canvas(Modifier.matchParentSize().pointerInput(preview, geometry, ratio) {
                                var handle: CropHandle? = null
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        handle = cropHandleAt(crop, offset.x, offset.y,
                                            size.width.toFloat(), size.height.toFloat(), 24.dp.toPx())
                                    },
                                    onDragCancel = { handle = null },
                                    onDragEnd = { handle = null }
                                ) { change, delta ->
                                    val active = handle
                                    if (active != null && size.width > 0 && size.height > 0) {
                                        change.consume()
                                        crop = geometry.drag(crop, active, delta.x / size.width, delta.y / size.height, ratio)
                                    }
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
                                val radius = minOf(4.dp.toPx(), size.width / 4f, size.height / 4f)
                                if (radius > 0f) for (point in listOf(
                                    Offset(left, top), Offset((left + right) / 2, top), Offset(right, top),
                                    Offset(left, (top + bottom) / 2), Offset(right, (top + bottom) / 2),
                                    Offset(left, bottom), Offset((left + right) / 2, bottom), Offset(right, bottom)
                                )) {
                                    val center = Offset(point.x.coerceIn(radius, size.width - radius),
                                        point.y.coerceIn(radius, size.height - radius))
                                    drawCircle(Color.White, radius, center)
                                    drawCircle(Color.Black, radius, center, style = Stroke(1.dp.toPx()))
                                }
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CropRatio.entries.forEach { option ->
                            FilterChip(
                                selected = selected == option,
                                onClick = {
                                    selected = option
                                    option.aspect?.let { crop = geometry.preset(it) }
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    Text(if (ratio == null) "Freeform: drag any edge or corner; drag inside to move."
                        else "Locked ${selected.label}: drag an edge or corner to resize without changing the ratio.",
                        style = MaterialTheme.typography.bodySmall)
                    val pixels = crop.pixels(dimensions.width, dimensions.height)
                    Text("${pixels.width} × ${pixels.height} px after crop", style = MaterialTheme.typography.titleSmall)
                    if (ratio == null) {
                        CropSlider("Crop width", crop.width, geometry.minimumWidth, crop.width, enabled = geometry.minimumWidth < 1f) {
                            crop = geometry.resizeCentered(crop, width = it)
                        }
                        CropSlider("Crop height", crop.height, geometry.minimumHeight, crop.height, enabled = geometry.minimumHeight < 1f) {
                            crop = geometry.resizeCentered(crop, height = it)
                        }
                    } else {
                        val scale = geometry.scale(crop, ratio)
                        val minimum = geometry.minimumScale(ratio)
                        CropSlider("Crop size", scale, minimum, scale, enabled = minimum < 1f) {
                            crop = geometry.scaleCentered(crop, ratio, it)
                        }
                    }
                    CropSlider("Horizontal position", geometry.horizontalPosition(crop), 0f,
                        geometry.horizontalPosition(crop), enabled = crop.width < .999999f) {
                        crop = geometry.position(crop, horizontal = it)
                    }
                    CropSlider("Vertical position", geometry.verticalPosition(crop), 0f,
                        geometry.verticalPosition(crop), enabled = crop.height < .999999f) {
                        crop = geometry.position(crop, vertical = it)
                    }
                    TextButton(onClick = { selected = CropRatio.FREE; crop = CropWindow.Full }) { Text("Reset full photo") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(crop) }, enabled = preview != null && geometry != null) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/** Native sliders expose focus, keyboard adjustments and SetProgress to accessibility services. */
@Composable
private fun CropSlider(
    label: String,
    value: Float,
    minimum: Float,
    percentage: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    val display = "${(percentage * 100).roundToInt()}%"
    val rangeStart = if (minimum >= 1f) 0f else minimum
    Column {
        Text("$label · $display", style = MaterialTheme.typography.bodySmall)
        Slider(value = value.coerceIn(rangeStart, 1f), valueRange = rangeStart..1f,
            enabled = enabled, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                stateDescription = display
            })
    }
}
