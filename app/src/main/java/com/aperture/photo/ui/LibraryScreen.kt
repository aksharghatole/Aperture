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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aperture.photo.imaging.ImageInputException
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.EditHistoryCodec
import com.aperture.photo.imaging.PhotoRenderer
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.imaging.RgbCurves
import com.aperture.photo.imaging.comparisonBaseline
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.InspectionTransform
import com.aperture.photo.library.PhotoInformation
import com.aperture.photo.library.readPhotoInformation
import com.aperture.photo.library.Photo
import com.aperture.photo.library.PhotoLibrary
import com.aperture.photo.library.filterFavoritePhotos
import com.aperture.photo.library.PhotoSortMode
import com.aperture.photo.library.CaptureTimeCache
import com.aperture.photo.library.sortPhotos
import com.aperture.photo.library.SavedEditException
import com.aperture.photo.library.SavedEditProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Composable
internal fun LibraryScreen(photos: List<Photo>, operations: PhotoOperations, back: () -> Unit, open: (Photo) -> Unit) {
    val library = operations.library
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptyList<String>()) }
    var batchMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val progress = operations.batchProgress
    val selectMode = selecting || progress != null
    val favoriteNames = remember(operations.libraryRevision) { library.favoriteNames() }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf(PhotoSortMode.IMPORT_NEWEST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var sortedPhotos by remember { mutableStateOf(photos) }
    LaunchedEffect(photos, sortMode) {
        sortedPhotos = withContext(Dispatchers.IO) {
            val captures = if (sortMode.captureDate) photos.associate { photo ->
                photo.file.name to CaptureTimeCache.get(photo.file)
            } else emptyMap()
            sortPhotos(photos, sortMode, captures)
        }
    }
    val visiblePhotos = filterFavoritePhotos(sortedPhotos, favoritesOnly, favoriteNames)
    val pending = if (selectMode) visiblePhotos.filter { it.file.name in selected } else emptyList()
    val stopBatch = progress?.stopping == true

    fun cancelSelection() {
        selected = emptyList()
        selecting = false
    }

    BackHandler(enabled = selectMode) {
        if (operations.batchProgress == null) cancelSelection() else operations.stopBatch()
    }

    LaunchedEffect(operations.batchResult) {
        operations.batchResult?.let {
            selected = it.remaining
            selecting = selected.isNotEmpty()
            batchMessage = it.message
            operations.consumeBatchResult()
        }
    }

    fun exportSelected() {
        if (pending.isEmpty() || progress != null) return
        batchMessage = null
        operations.exportBatch(pending)
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
            } catch (failure: ImageInputException) {
                error = failure.message
            } catch (_: Exception) {
                error = "This image could not be opened. Check its access and try again."
            } catch (_: OutOfMemoryError) {
                error = "This image is too large to import."
            } finally {
                importing = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(if (selectMode) "${progress?.total ?: pending.size} selected" else "Library",
            if (selectMode) "Done" else "Camera", {
                if (!selectMode) back()
                else if (progress == null) cancelSelection()
            }) {
            if (!selectMode) {
                Row {
                    Box {
                        TextButton(enabled = !importing, onClick = { sortMenuExpanded = true }) { Text("Sort") }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            PhotoSortMode.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                    sortMode = option
                                    sortMenuExpanded = false
                                })
                            }
                        }
                    }
                    TextButton(enabled = !importing && photos.isNotEmpty(), onClick = {
                        selecting = true
                        batchMessage = null
                    }) { Text("Select") }
                    TextButton(enabled = !importing, onClick = { picker.launch("image/*") }) { Text("Import") }
                }
            } else if (progress == null) {
                TextButton(enabled = pending.isNotEmpty(), onClick = ::exportSelected) { Text("Export") }
            } else {
                TextButton(enabled = !stopBatch, onClick = operations::stopBatch) { Text("Stop") }
            }
        }
        if (progress != null) {
            LinearProgressIndicator(progress = { progress.completed.toFloat() / progress.total }, Modifier.fillMaxWidth())
            Text(if (stopBatch) "Stopping after the current photo…" else
                "Processed ${progress.completed} of ${progress.total}",
                modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !favoritesOnly, enabled = !selectMode,
                onClick = { favoritesOnly = false }, label = { Text("All") })
            FilterChip(selected = favoritesOnly, enabled = !selectMode,
                onClick = { favoritesOnly = true }, label = { Text("Favorites") })
        }
        batchMessage?.let {
            Text(it, modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (visiblePhotos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (favoritesOnly) "No favorite photos yet." else "Take or import a photo to get started.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(visiblePhotos, key = { it.file.name }) { photo ->
                    val name = photo.file.name
                    val checked = name in selected
                    val tile = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                    val interaction = if (selectMode) {
                        tile.toggleable(value = checked, enabled = progress == null) {
                            selected = if (it) selected + name else selected - name
                        }
                    } else {
                        tile.clickable(onClickLabel = "Edit photo") { open(photo) }
                    }
                    Box(interaction) {
                        PhotoThumbnail(photo, library, Modifier.fillMaxSize(), "${photo.film.title} photo")
                        if (selectMode && checked) {
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

private val SelectionSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

private val thumbnailReads = Semaphore(2)

@Composable
fun PhotoThumbnail(photo: Photo, library: PhotoLibrary, modifier: Modifier, description: String) {
    var bitmap by remember(photo, library) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo, library) {
        bitmap = thumbnailReads.withPermit {
            withContext(Dispatchers.IO) { runCatching { library.thumbnail(photo) }.getOrNull() }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
internal fun EditorScreen(photo: Photo, operations: PhotoOperations, back: () -> Unit, onDeleted: () -> Unit) {
    val library = operations.library
    var isFavorite by remember(photo, library) { mutableStateOf(photo.file.name in library.favoriteNames()) }
    val context = LocalContext.current
    var showPhotoInfo by rememberSaveable(photo.file.name) { mutableStateOf(false) }
    var photoInfo by remember(photo.file) { mutableStateOf<PhotoInformation?>(null) }
    LaunchedEffect(photo.file) {
        photoInfo = withContext(Dispatchers.IO) { runCatching { readPhotoInformation(photo.file) }.getOrNull() }
    }
    val scope = rememberCoroutineScope()
    val original = remember(photo) { Recipe(photo.film) }
    var history by rememberSaveable(photo.file.name, stateSaver = EditHistorySaver) {
        mutableStateOf(EditHistory(original))
    }
    var editsLoaded by rememberSaveable(photo.file.name) { mutableStateOf(false) }
    var lastSaved by rememberSaveable(photo.file.name, stateSaver = EditHistorySaver) {
        mutableStateOf(EditHistory(original))
    }
    var savedByUser by rememberSaveable(photo.file.name) { mutableStateOf(false) }
    var confirmLeave by rememberSaveable(photo.file.name) { mutableStateOf(false) }
    var savedEditProblem by rememberSaveable(photo.file.name) { mutableStateOf<SavedEditProblem?>(null) }
    var confirmReplacement by remember { mutableStateOf(false) }
    var saving by remember(photo) { mutableStateOf(false) }
    var draft by remember(photo) { mutableStateOf<Recipe?>(null) }
    val recipe = draft ?: history.current
    fun commit(edit: Recipe) {
        history = history.commit(edit)
        draft = null
    }
    val loaded by loadedPhoto(photo, 1400)
    val source = loaded?.getOrNull()
    var curveDraft by remember(photo) { mutableStateOf<RgbCurves?>(null) }
    val previewRecipe = if (savedEditProblem != null) Recipe() else curveDraft?.let { recipe.copy(curves = it) } ?: recipe
    val rendered by key(previewRecipe.crop, previewRecipe.quarterTurns) { renderedPhoto(source, previewRecipe) }
    val preview = rendered?.getOrNull()
    var showBefore by rememberSaveable(photo.file.name) { mutableStateOf(false) }
    var compareRequested by remember(photo) { mutableStateOf(false) }
    val baseline = previewRecipe.comparisonBaseline(photo.film)
    val before = when {
        baseline == Recipe() -> loaded
        compareRequested || showBefore -> key(baseline) { renderedPhoto(source, baseline).value }
        else -> null
    }
    val comparing = showBefore && editsLoaded && savedEditProblem == null
    val displayed = if (comparing) before else rendered
    val displayedImage = displayed?.getOrNull()
    val inspection = remember(photo, previewRecipe.crop, previewRecipe.quarterTurns) { EditorInspectionState() }
    var showHistogram by rememberSaveable { mutableStateOf(false) }
    val currentExport = operations.editorExport?.takeIf { it.photo == photo }
    val exportRecipe = if (savedEditProblem == null) recipe else Recipe()
    val shareUri = currentExport?.result?.matching(exportRecipe)
    val smallerExport = currentExport?.smallerEdge?.let { currentExport.snapshot to it }
    val reducedExport = currentExport?.reduced == true
    val exporting = currentExport?.running == true
    var deleting by remember(photo) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(photo.file.name, library, editsLoaded) {
        if (!editsLoaded) {
            try {
                withContext(Dispatchers.IO) { library.loadEdits(photo) }?.let {
                    history = it
                    lastSaved = it
                }
                savedEditProblem = null
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: SavedEditException) {
                savedEditProblem = failure.problem
            } catch (_: Exception) {
                savedEditProblem = SavedEditProblem.UNREADABLE
            }
            editsLoaded = true
        }
    }
    fun requestBack() {
        if (saving || deleting || exporting) return
        if (history.differsFrom(lastSaved, draft)) confirmLeave = true else back()
    }

    fun save(replace: SavedEditProblem? = null, leave: Boolean = false) {
        val snapshot = draft?.let(history::commit) ?: history
        if (leave) {
            history = snapshot
            draft = null
        }
        saving = true
        message = "Saving edits…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) { library.saveEdits(photo, snapshot, replace) }
                savedEditProblem = null
                lastSaved = snapshot
                savedByUser = true
                message = null
                if (leave && !history.differsFrom(snapshot, draft)) back()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: SavedEditException) {
                savedEditProblem = failure.problem
                message = "The existing edit file was preserved."
            } catch (_: Exception) {
                message = "Edits could not be saved. Try again."
            } finally {
                saving = false
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Unsaved edits") },
            text = { Text("Save this photo’s edit history before leaving Darkroom?") },
            confirmButton = {
                TextButton(enabled = savedEditProblem == null, onClick = {
                    confirmLeave = false
                    save(leave = true)
                }) { Text("Save and leave") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmLeave = false }) { Text("Stay") }
                    TextButton(onClick = { confirmLeave = false; back() }) { Text("Discard") }
                }
            }
        )
    }

    if (confirmReplacement) {
        AlertDialog(
            onDismissRequest = { confirmReplacement = false },
            title = { Text("Replace saved edits?") },
            text = { Text("This will replace the unreadable edit history for this photo. The original photo is preserved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplacement = false
                    savedEditProblem?.let { save(it) }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmReplacement = false }) { Text("Cancel") } }
        )
    }

    BackHandler(onBack = ::requestBack)
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
    curveDraft?.let { curves ->
        EditorCurves(curves, preview, { curveDraft = it }, { curveDraft = null }) {
            commit(recipe.copy(curves = curves))
            curveDraft = null
        }
    }
    var cropping by remember { mutableStateOf(false) }
    if (cropping) {
        val fullPreview by renderedPhoto(source, recipe.copy(crop = CropWindow.Full))
        EditorCrop(fullPreview, recipe.crop, { cropping = false }) { crop ->
            commit(recipe.copy(crop = crop))
            cropping = false
        }
    }

    fun startExport(snapshot: Recipe, maxEdge: Int? = null) {
        if (exporting || saving || deleting) return
        message = null
        operations.export(photo, snapshot, maxEdge)
    }

    smallerExport?.let { (snapshot, edge) ->
        AlertDialog(
            onDismissRequest = { operations.dismissExportOffer() },
            title = { Text("Export a smaller JPEG?") },
            text = { Text("The requested size needs more memory. You can export a JPEG up to $edge px on its longest edge.") },
            confirmButton = {
                TextButton(onClick = { startExport(snapshot, edge) }) { Text("Export smaller") }
            },
            dismissButton = {
                TextButton(onClick = { operations.dismissExportOffer() }) { Text("Cancel") }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Darkroom", "Library", ::requestBack) {
            Row {
                TextButton(enabled = !saving && !exporting && !deleting, onClick = {
                    val next = !isFavorite
                    runCatching { library.setFavorite(photo, next) }
                        .onSuccess { isFavorite = next; operations.libraryChanged() }
                        .onFailure { message = "Favorite could not be updated." }
                }, modifier = Modifier.semantics {
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
                }) { Text(if (isFavorite) "★ Saved" else "☆ Favorite") }
                TextButton(enabled = !saving && !exporting && !deleting, onClick = { confirmDelete = true }) {
                    Text("Delete")
                }
                TextButton(onClick = { showPhotoInfo = !showPhotoInfo }) { Text("Info") }
                TextButton(enabled = editsLoaded && source != null && !exporting && !saving && !deleting, onClick = {
                    startExport(exportRecipe)
                }) { Text(if (savedEditProblem == null) "Export" else "Export original") }
            }
        }

        if (showPhotoInfo) {
            if (photoInfo == null) {
                Text("Reading photo information…", modifier = Modifier.padding(vertical = 8.dp))
            } else {
                PhotoInfoPanel(photoInfo!!)
            }
        }

        if (editsLoaded) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (savedEditProblem == null) {
                    FilterChip(selected = showBefore, enabled = showBefore || preview != null, onClick = {
                        compareRequested = true
                        showBefore = !showBefore
                    }, label = {
                        Text(when {
                            !showBefore -> "Before"
                            photo.film == Film.CLEAN -> "Before · original tones"
                            else -> "Before · ${photo.film.title}"
                        })
                    })
                }
                val aspect = displayedImage?.let { it.width.toFloat() / it.height }
                val zoom = inspection.transform.zoom
                TextButton(enabled = aspect != null && zoom > 1f,
                    onClick = { aspect?.let { inspection.move(it, .5f) } },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(48.dp).semantics { contentDescription = "Zoom out" }) { Text("−") }
                TextButton(enabled = aspect != null && zoom < InspectionTransform.MaxZoom,
                    onClick = { aspect?.let { inspection.move(it, 2f) } },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(48.dp).semantics { contentDescription = "Zoom in" }) { Text("+") }
                TextButton(enabled = zoom > 1f, onClick = inspection::reset,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.width(48.dp).semantics { contentDescription = "Reset photo zoom to fit" }) { Text("Fit") }
                Text("${"%.1f".format(zoom)}× preview", style = MaterialTheme.typography.labelMedium)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged(inspection::resize).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            val image = displayedImage
            when {
                image != null -> InspectablePhoto(image, if (comparing) "Before edits" else "Edited photo", inspection)
                loaded?.isFailure == true -> Text("This photo could not be opened.", color = MaterialTheme.colorScheme.error)
                displayed?.isFailure == true -> Text(
                    if (comparing) "Before preview unavailable." else "This edit could not be previewed.",
                    color = MaterialTheme.colorScheme.error
                )
                else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            if (image != null && showHistogram) {
                EditorHistogram(image, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }

        savedEditProblem?.let { problem ->
            val explanation = when (problem) {
                SavedEditProblem.CORRUPT -> "Saved edits are damaged."
                SavedEditProblem.UNSUPPORTED -> "These saved edits use a format this version cannot open."
                SavedEditProblem.UNREADABLE -> "Saved edits could not be read."
            }
            Text("$explanation You can export the original.", modifier = Modifier.padding(vertical = 8.dp))
            if (problem == SavedEditProblem.UNREADABLE) {
                TextButton(onClick = { editsLoaded = false }) { Text("Retry saved edits") }
            } else {
                TextButton(enabled = !saving && !exporting && !deleting, onClick = { confirmReplacement = true }) {
                    Text("Replace saved edits")
                }
            }
        }
        var selectedTool by rememberSaveable { mutableStateOf("Exposure") }
        if (editsLoaded && savedEditProblem == null) Column(Modifier.padding(top = 6.dp)) {
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
                TextButton(enabled = source != null && !saving && !exporting && draft == null,
                    onClick = { save() }) { Text(if (saving) "Saving…" else "Save edits") }
                TextButton(enabled = preview != null, onClick = { showHistogram = !showHistogram }) {
                    Text(if (showHistogram) "Hide histogram" else "Histogram")
                }
                TextButton(enabled = source != null && draft == null, onClick = { cropping = true }) { Text("Crop") }
                TextButton(enabled = source != null && draft == null, onClick = { curveDraft = recipe.curves }) { Text("Curves") }
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
                shareUri?.let { uri ->
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
        }
        val completion = when {
            shareUri != null -> if (reducedExport) "Smaller JPEG saved in Pictures/Aperture" else "Saved in Pictures/Aperture"
            savedByUser && !history.differsFrom(lastSaved, draft) -> "Edits saved"
            else -> null
        }
        if (source?.hasAlpha() == true) {
            Text("JPEG exports fill transparency with white.", style = MaterialTheme.typography.labelSmall)
        }
        (message ?: currentExport?.message ?: completion)?.let {
            Text(it, modifier = Modifier.padding(bottom = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp)
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
private fun PhotoInfoPanel(info: PhotoInformation) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        PhotoInfoRow("Dimensions", info.dimensions)
        PhotoInfoRow("Format", info.format)
        PhotoInfoRow("File size", info.fileSize)
        PhotoInfoRow("File date", info.fileDate)
        PhotoInfoRow("Capture date", info.captureDate ?: "Unavailable")
        PhotoInfoRow("Exposure", info.exposure ?: "Unavailable")
        PhotoInfoRow("ISO", info.iso ?: "Unavailable")
        PhotoInfoRow("Aperture", info.aperture ?: "Unavailable")
        PhotoInfoRow("Focal length", info.focalLength ?: "Unavailable")
        PhotoInfoRow("Orientation", info.orientation ?: "Unavailable")
    }
}

@Composable
private fun PhotoInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value, fontSize = 12.sp)
    }
}

@Composable
private fun ScreenHeader(title: String, backLabel: String, onBack: () -> Unit, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(end = 10.dp)) { Text("‹ $backLabel") }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        action()
    }
}
