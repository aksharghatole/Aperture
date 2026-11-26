package com.aperture.photo.ui

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aperture.photo.imaging.PhotoRenderer
import kotlin.math.roundToInt

internal data class CameraReference(val image: ImageBitmap?, val opacity: Float, val open: () -> Unit)

@Composable
internal fun rememberCameraReference(): CameraReference {
    val resolver = LocalContext.current.contentResolver
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var opacity by remember { mutableFloatStateOf(.35f) }
    var request by remember { mutableStateOf<Uri?>(null) }
    var open by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            error = false
            request = uri
        }
    }

    LaunchedEffect(request) {
        val uri = request ?: return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                PhotoRenderer.decode(ImageDecoder.createSource(resolver, uri), 1280).asImageBitmap()
            }.getOrNull()
        }
        if (decoded != null) image = decoded
        error = decoded == null
        request = null
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Reference photo") },
            text = {
                Column {
                    when {
                        request != null -> Text("Loading reference…")
                        error -> Text("Could not open that image.")
                        image == null -> Text("Choose a photo to overlay on the viewfinder.")
                    }
                    if (image != null) {
                        Text("Opacity ${(opacity * 100).roundToInt()}%")
                        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..0.8f)
                        TextButton(onClick = { image = null; request = null; error = false; open = false }) {
                            Text("Remove reference")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = { picker.launch("image/*") }, enabled = request == null) {
                    Text(if (image == null) "Choose photo" else "Replace photo")
                }
            }
        )
    }
    return CameraReference(image, opacity) { open = true }
}
