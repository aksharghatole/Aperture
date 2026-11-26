package com.aperture.photo.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.Film
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
        ScreenHeader("Library", "Camera", back) {
            TextButton(enabled = !importing, onClick = { picker.launch("image/*") }) { Text("Import") }
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
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Edit photo") { open(photo) }) {
                        PhotoThumbnail(photo, Modifier.fillMaxSize(), "${photo.film.title} photo")
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoThumbnail(photo: Photo, modifier: Modifier, description: String) {
    val loaded by loadedPhoto(photo, 320)
    val result by renderedPhoto(loaded?.getOrNull(), Recipe(photo.film))
    Box(modifier.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        result?.getOrNull()?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
fun EditorScreen(photo: Photo, library: PhotoLibrary, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val original = remember(photo) { Recipe(photo.film) }
    var history by rememberSaveable(photo.file.name, stateSaver = EditHistorySaver) {
        mutableStateOf(EditHistory(original))
    }
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
    var result by remember { mutableStateOf<android.net.Uri?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Darkroom", "Library", back) {
            TextButton(enabled = source != null && !exporting, onClick = {
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
        }

        var selectedTool by rememberSaveable { mutableStateOf("Exposure") }
        Column(Modifier.padding(top = 6.dp)) {
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
                TextButton(enabled = history.canUndo && draft == null, onClick = { history = history.undo() }) {
                    Text("Undo")
                }
                TextButton(enabled = history.canRedo && draft == null, onClick = { history = history.redo() }) {
                    Text("Redo")
                }
                TextButton(onClick = { commit(recipe.copy(quarterTurns = (recipe.quarterTurns + 1) % 4)) }) { Text("Rotate") }
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

private val EditHistorySaver = listSaver<EditHistory, Any>(
    save = { history ->
        listOf(history.cursor) + history.snapshots.flatMap {
            listOf(it.film.name, it.exposure, it.contrast, it.saturation, it.warmth, it.vignette, it.quarterTurns)
        }
    },
    restore = { values ->
        val cursor = values.firstOrNull() as? Int ?: return@listSaver null
        val steps = values.drop(1)
        if (steps.size % 7 != 0) return@listSaver null
        val recipes = steps.chunked(7).map {
            Recipe(Film.valueOf(it[0] as String), it[1] as Float, it[2] as Float, it[3] as Float,
                it[4] as Float, it[5] as Float, it[6] as Int)
        }
        EditHistory.restore(recipes, cursor)
    }
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
