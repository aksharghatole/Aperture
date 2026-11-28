package com.aperture.photo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aperture.photo.imaging.ExportSettings
import com.aperture.photo.imaging.ImageSize
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.Photo
import com.aperture.photo.library.PhotoLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Each invocation starts with the original full-size/96-quality export defaults. */
@Composable
internal fun ExportOptions(
    library: PhotoLibrary,
    photos: List<Photo>,
    recipe: Recipe? = null,
    onClose: () -> Unit,
    onExport: (ExportSettings) -> Unit
) {
    var sizeOption by rememberSaveable { mutableStateOf("Full") }
    var customEdge by rememberSaveable { mutableStateOf("1600") }
    var quality by rememberSaveable { mutableIntStateOf(96) }
    var dimensions by remember(photos, recipe) { mutableStateOf<List<ImageSize?>?>(null) }
    LaunchedEffect(library, photos, recipe) {
        dimensions = withContext(Dispatchers.IO) {
            photos.map { photo ->
                ensureActive()
                try {
                    library.exportDimensions(photo, recipe)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } catch (_: OutOfMemoryError) {
                    null
                }
            }
        }
    }
    val maxEdge = when (sizeOption) {
        "Full" -> null
        "Custom" -> customEdge.toIntOrNull()?.takeIf { it in 1..32768 }
        else -> sizeOption.toIntOrNull()
    }
    val validSize = sizeOption != "Custom" || maxEdge != null
    val sizes = dimensions
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (photos.size == 1) "Export JPEG" else "Export ${photos.size} JPEGs") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Longest edge after crop and rotation")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (option in listOf("Full", "2048")) {
                            FilterChip(selected = sizeOption == option, onClick = { sizeOption = option },
                                label = { Text(if (option == "Full") "Full resolution" else "$option px") })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (option in listOf("1024", "Custom")) {
                            FilterChip(selected = sizeOption == option, onClick = { sizeOption = option },
                                label = { Text(if (option == "Custom") option else "$option px") })
                        }
                    }
                    if (sizeOption == "Custom") {
                        OutlinedTextField(
                            value = customEdge, onValueChange = { value ->
                                if (value.length <= 5 && value.all(Char::isDigit)) customEdge = value
                            },
                            label = { Text("Longest edge (pixels)") }, singleLine = true,
                            isError = !validSize, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = { if (!validSize) Text("Enter 1–32,768 pixels.") }
                        )
                    }
                    Text("JPEG quality: $quality / 100")
                    Slider(value = quality.toFloat(), onValueChange = { quality = it.roundToInt() },
                        valueRange = 1f..100f, steps = 98)
                    Text("Never enlarges small photos. Original images and saved edits are unchanged.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Final dimensions", style = MaterialTheme.typography.titleSmall)
                    if (sizes == null) Text("Reading photo dimensions…")
                    else if (!validSize) Text("Enter a valid size to see dimensions.")
                }
                if (sizes != null && validSize) itemsIndexed(sizes) { index, original ->
                    val output = original?.within(maxEdge)
                    val label = if (photos.size > 1) "Photo ${index + 1}: " else ""
                    Text(if (output == null) "${label}Dimensions unavailable"
                        else "$label${output.width} × ${output.height} px")
                }
                item {
                    Text("JPEG file size varies with image content and quality; the exact size is known after encoding.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = validSize && sizes != null, onClick = {
                onExport(ExportSettings(maxEdge, quality))
            }) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
