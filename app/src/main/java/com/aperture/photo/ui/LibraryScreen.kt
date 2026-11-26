package com.aperture.photo.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.EditHistoryCodec
import com.aperture.photo.imaging.PhotoRenderer
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.Photo
import com.aperture.photo.library.PhotoLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(photos: List<Photo>, library: PhotoLibrary, back: () -> Unit, open: (Photo) -> Unit) {
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptyList<String>()) }
    var batchProgress by remember { mutableStateOf<BatchProgress?>(null) }
    var stopBatch by remember { mutableStateOf(false) }
    var batchMessage by remember { mutableStateOf<String?>(null) }
    val pending = if (selecting) photos.filter { it.file.name in selected } else emptyList()
    val progress = batchProgress

    fun cancelSelection() {
        selected = emptyList()
        selecting = false
    }

    BackHandler(enabled = selecting) {
        if (batchProgress == null) cancelSelection() else stopBatch = true
    }

    fun exportSelected() {
        if (pending.isEmpty() || batchProgress != null) return
        val batch = pending
        stopBatch = false
        batchMessage = null
        batchProgress = BatchProgress(0, batch.size)
        scope.launch {
            val failures = arrayListOf<String>()
            var exported = 0
            var attempted = 0
            try {
                for (photo in batch) {
                    if (stopBatch) break
                    try {
                        withContext(Dispatchers.IO) { library.exportSavedEdits(photo) }
                        exported++
                        selected = selected - photo.file.name
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        failures.add(photo.file.name)
                    } catch (_: OutOfMemoryError) {
                        failures.add(photo.file.name)
                    }
                    attempted++
                    batchProgress = BatchProgress(attempted, batch.size)
                }
                selected = failures + batch.drop(attempted).map { it.file.name }
                if (selected.isEmpty()) selecting = false
                batchMessage = buildString {
                    append("Exported ").append(exported).append(" of ").append(batch.size)
                    if (failures.isNotEmpty()) append(" · ").append(failures.size).append(" failed")
                    if (attempted < batch.size) append(" · ").append(batch.size - attempted).append(" stopped before export")
                    if (exported > 0) append(" · Pictures/Aperture")
                }
            } finally {
                batchProgress = null
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            error = null
            try {
                val photo = withContext(Dispatchers.IO) { library.import(uri) }
                open(photo)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                error = "This image could not be imported."
            } catch (_: OutOfMemoryError) {
                error = "This image is too large to import."
            } finally {
                importing = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(if (selecting) "${progress?.total ?: pending.size} selected" else "Library",
            if (selecting) "Done" else "Camera", {
                if (!selecting) back()
                else if (batchProgress == null) cancelSelection()
            }) {
            if (!selecting) {
                Row {
                    TextButton(enabled = !importing && photos.isNotEmpty(), onClick = {
                        selecting = true
                        batchMessage = null
                    }) { Text("Select") }
                    TextButton(enabled = !importing, onClick = { picker.launch("image/*") }) { Text("Import") }
                }
            } else if (progress == null) {
                TextButton(enabled = pending.isNotEmpty(), onClick = ::exportSelected) { Text("Export") }
            } else {
                TextButton(enabled = !stopBatch, onClick = { stopBatch = true }) { Text("Stop") }
            }
        }
        if (progress != null) {
            LinearProgressIndicator(progress = { progress.completed.toFloat() / progress.total }, Modifier.fillMaxWidth())
            Text(if (stopBatch) "Stopping after the current photo…" else
                "Processed ${progress.completed} of ${progress.total}",
                modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        batchMessage?.let {
            Text(it, modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Take or import a photo to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(photos, key = { it.file.name }) { photo ->
                    val name = photo.file.name
                    val checked = name in selected
                    val tile = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                    val interaction = if (selecting) {
                        tile.toggleable(value = checked, enabled = progress == null) {
                            selected = if (it) selected + name else selected - name
                        }
                    } else {
                        tile.clickable(onClickLabel = "Edit photo") { open(photo) }
                    }
                    Box(interaction) {
                        PhotoThumbnail(photo, Modifier.fillMaxSize(), "${photo.film.title} photo")
                        if (selecting && checked) {
                            Box(Modifier.matchParentSize().border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)))
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text("✓", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class BatchProgress(val completed: Int, val total: Int)

private val SelectionSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

@Composable
fun PhotoThumbnail(photo: Photo, modifier: Modifier, description: String) {
    val loaded by loadedPhoto(photo, 320)
    val result by renderedPhoto(loaded?.getOrNull(), Recipe(photo.film))
    Box(modifier.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        result?.getOrNull()?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
fun EditorScreen(photo: Photo, library: PhotoLibrary, back: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val original = remember(photo) { Recipe(photo.film) }
    var history by rememberSaveable(photo.file.name, stateSaver = EditHistorySaver) {
        mutableStateOf(EditHistory(original))
    }
    var editsLoaded by rememberSaveable(photo.file.name) { mutableStateOf(false) }
    var saving by remember(photo) { mutableStateOf(false) }
    var draft by remember(photo) { mutableStateOf<Recipe?>(null) }
    val recipe = draft ?: history.current
    fun commit(edit: Recipe) {
        history = history.commit(edit)
        draft = null
    }
    val loaded by loadedPhoto(photo, 1400)
    val source = loaded?.getOrNull()
    val rendered by renderedPhoto(source, recipe)
    val preview = rendered?.getOrNull()
    var showHistogram by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<android.net.Uri?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var deleting by remember(photo) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(photo.file.name, library) {
        if (!editsLoaded) {
            try {
                withContext(Dispatchers.IO) { library.loadEdits(photo) }?.let { history = it }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                message = "Saved edits could not be opened."
            }
            editsLoaded = true
        }
    }
    BackHandler(enabled = saving || deleting) {}
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDelete = false },
            title = { Text("Delete photo?") },
            text = { Text("This permanently removes the original photo and its saved Darkroom edits from Aperture.") },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    confirmDelete = false
                    deleting = true
                    message = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { library.delete(photo) }
                            onDeleted()
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Exception) {
                            message = "The photo could not be deleted. Try again."
                        } finally {
                            deleting = false
                        }
                    }
                }) { Text(if (deleting) "Deleting…" else "Delete") }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
    var cropping by remember { mutableStateOf(false) }
    if (cropping) {
        val fullPreview by renderedPhoto(source, recipe.copy(crop = CropWindow.Full))
        EditorCrop(fullPreview, recipe.crop, { cropping = false }) { crop ->
            commit(recipe.copy(crop = crop))
            cropping = false
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Darkroom", "Library", { if (!saving && !deleting) back() }) {
            Row {
                TextButton(enabled = !saving && !exporting && !deleting, onClick = { confirmDelete = true }) {
                    Text("Delete")
                }
                TextButton(enabled = editsLoaded && source != null && !exporting && !saving && !deleting, onClick = {
                    val exportRecipe = recipe
                    scope.launch {
                        exporting = true
                        result = null
                        message = "Exporting full resolution…"
                        try {
                            result = withContext(Dispatchers.IO) { library.export(photo, exportRecipe) }
                            message = "Saved in Pictures/Aperture"
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Exception) {
                            message = "Export failed. Check available storage and try again."
                        } catch (_: OutOfMemoryError) {
                            message = "Your device ran out of memory during export."
                        } finally {
                            exporting = false
                        }
                    }
                }) { Text("Export") }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            val image = preview
            when {
                image != null -> Image(image.asImageBitmap(), "Edited photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                loaded?.isFailure == true -> Text("This photo could not be opened.", color = MaterialTheme.colorScheme.error)
                rendered?.isFailure == true -> Text("This edit could not be previewed.", color = MaterialTheme.colorScheme.error)
                source == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            if (image != null && showHistogram) {
                EditorHistogram(image, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }

        var selectedTool by rememberSaveable { mutableStateOf("Exposure") }
        if (editsLoaded) Column(Modifier.padding(top = 6.dp)) {
            FilmPicker(recipe.film) { if (recipe.film != it) commit(recipe.copy(film = it, vignette = it.vignette)) }
            val controls = listOf(
                EditControl("Exposure", recipe.exposure, -2f..2f) { draft = (draft ?: history.current).copy(exposure = it) },
                EditControl("Contrast", recipe.contrast, -0.5f..0.5f) { draft = (draft ?: history.current).copy(contrast = it) },
                EditControl("Color", recipe.saturation, -1f..1f) { draft = (draft ?: history.current).copy(saturation = it) },
                EditControl("Warmth", recipe.warmth, -1f..1f) { draft = (draft ?: history.current).copy(warmth = it) },
                EditControl("Vignette", recipe.vignette, 0f..1f) { draft = (draft ?: history.current).copy(vignette = it) }
            )
            val tool = controls.first { it.label == selectedTool }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(controls.size) { index ->
                    val option = controls[index]
                    FilterChip(selected = tool == option, onClick = { selectedTool = option.label },
                        label = { Text(option.label, fontSize = 11.sp) })
                }
            }
            Slider(value = tool.value, onValueChange = tool.change, valueRange = tool.range,
                onValueChangeFinished = { draft?.let(::commit) })
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(enabled = source != null && !saving && !exporting && draft == null, onClick = {
                    val snapshot = history
                    saving = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { library.saveEdits(photo, snapshot) }
                            message = "Edits saved"
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Exception) {
                            message = "Edits could not be saved. Try again."
                        } finally {
                            saving = false
                        }
                    }
                }) { Text(if (saving) "Saving…" else "Save edits") }
                TextButton(enabled = preview != null, onClick = { showHistogram = !showHistogram }) {
                    Text(if (showHistogram) "Hide histogram" else "Histogram")
                }
                TextButton(enabled = source != null && draft == null, onClick = { cropping = true }) { Text("Crop") }
                TextButton(enabled = history.canUndo && draft == null, onClick = { history = history.undo() }) {
                    Text("Undo")
                }
                TextButton(enabled = history.canRedo && draft == null, onClick = { history = history.redo() }) {
                    Text("Redo")
                }
                TextButton(onClick = {
                    commit(recipe.copy(quarterTurns = (recipe.quarterTurns + 1) % 4, crop = recipe.crop.rotateClockwise()))
                }) { Text("Rotate") }
                TextButton(onClick = { commit(original) }) { Text("Reset") }
                result?.let { uri ->
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share photo"))
                    }) { Text("Share") }
                }
            }
            message?.let {
                Text(it, modifier = Modifier.padding(bottom = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp)
            }
        }
    }
}

private data class EditControl(val label: String, val value: Float, val range: ClosedFloatingPointRange<Float>,
    val change: (Float) -> Unit)

private val EditHistorySaver = Saver<EditHistory, Any>(
    save = { EditHistoryCodec.encode(it) },
    restore = { (it as? String)?.let(EditHistoryCodec::decode) }
)

@Composable
private fun loadedPhoto(photo: Photo, maxEdge: Int): State<Result<Bitmap>?> {
    val result = remember(photo, maxEdge) { mutableStateOf<Result<Bitmap>?>(null) }
    LaunchedEffect(photo, maxEdge) {
        result.value = withContext(Dispatchers.IO) {
            runCatching { PhotoRenderer.decode(photo.file, maxEdge) }
        }
    }
    return result
}

@Composable
private fun renderedPhoto(source: Bitmap?, recipe: Recipe): State<Result<Bitmap>?> {
    val result = remember(source) { mutableStateOf<Result<Bitmap>?>(null) }
    LaunchedEffect(source, recipe) {
        if (source != null) {
            result.value = withContext(Dispatchers.Default) {
                runCatching { PhotoRenderer.render(source, recipe) }
            }
        }
    }
    return result
}

@Composable
private fun ScreenHeader(title: String, backLabel: String, onBack: () -> Unit, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(end = 10.dp)) { Text("‹ $backLabel") }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        action()
    }
}
