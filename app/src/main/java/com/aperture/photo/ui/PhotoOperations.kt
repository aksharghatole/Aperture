package com.aperture.photo.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aperture.photo.imaging.ExportMemoryException
import com.aperture.photo.imaging.ExportReceipt
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.ImageInputException
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.Photo
import com.aperture.photo.library.PhotoImport
import com.aperture.photo.library.PhotoImportException
import com.aperture.photo.library.PhotoLibrary
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class BatchProgress(val completed: Int, val total: Int, val stopping: Boolean = false)
internal data class BatchResult(val remaining: List<String>, val message: String)
internal data class ImportOutcome(val name: String, val photo: Photo? = null, val error: String? = null)
internal data class ImportResult(val outcomes: List<ImportOutcome>, val skipped: Int = 0, val error: String? = null) {
    val imported: Int get() = outcomes.count { it.photo != null }
    val failed: Int get() = outcomes.size - imported
    val openSingle: Photo? get() = outcomes.singleOrNull()?.photo?.takeIf { skipped == 0 }
}
internal data class EditorExport(
    val photo: Photo,
    val snapshot: Recipe,
    val running: Boolean = true,
    val result: ExportReceipt<Uri>? = null,
    val reduced: Boolean = false,
    val smallerEdge: Int? = null,
    val message: String? = null
)

/** Retains in-process photo operations across Activity recreation. */
internal class PhotoOperations(application: Application) : AndroidViewModel(application) {
    val library = PhotoLibrary(application)
    var libraryRevision by mutableIntStateOf(0)
        private set
    var captureBusy by mutableStateOf(false)
        private set
    var captureMessage by mutableStateOf<String?>(null)
        private set
    var editorExport by mutableStateOf<EditorExport?>(null)
        private set
    var batchProgress by mutableStateOf<BatchProgress?>(null)
        private set
    var batchResult by mutableStateOf<BatchResult?>(null)
        private set
    var importProgress by mutableStateOf<BatchProgress?>(null)
        private set
    var importResult by mutableStateOf<ImportResult?>(null)
        private set

    private val busy: Boolean get() = captureBusy || editorExport?.running == true ||
        batchProgress != null || importProgress != null

    fun libraryChanged() { libraryRevision++ }

    fun clearCaptureMessage() { if (!captureBusy) captureMessage = null }

    fun stopImport() {
        importProgress = importProgress?.copy(stopping = true)
    }

    fun consumeImportResult() {
        importResult = null
    }

    fun importPhotos(selection: List<Uri>) {
        if (busy || selection.isEmpty()) return
        val selected = selection.asSequence().distinct().take(PhotoImport.MaxFiles + 1).toList()
        if (selected.size > PhotoImport.MaxFiles) {
            importResult = ImportResult(emptyList(), error = "Select up to ${PhotoImport.MaxFiles} photos per import.")
            return
        }
        importResult = null
        importProgress = BatchProgress(0, selected.size)
        viewModelScope.launch {
            val outcomes = ArrayList<ImportOutcome>(selected.size)
            try {
                for ((index, uri) in selected.withIndex()) {
                    if (importProgress?.stopping == true) break
                    outcomes.add(withContext(Dispatchers.IO) { importOne(uri, index + 1) })
                    importProgress = importProgress?.copy(completed = outcomes.size)
                }
                importResult = ImportResult(outcomes.toList(), selected.size - outcomes.size)
            } finally {
                if (outcomes.any { it.photo != null }) libraryChanged()
                importProgress = null
            }
        }
    }

    private fun importOne(uri: Uri, number: Int): ImportOutcome {
        val source = library.importSource(uri, "Photo $number")
        return try {
            ImportOutcome(source.name, photo = library.import(uri, source))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: PhotoImportException) {
            ImportOutcome(source.name, error = failure.message ?: "Could not import this photo.")
        } catch (failure: ImageInputException) {
            ImportOutcome(source.name, error = failure.message ?: "Could not open this image.")
        } catch (_: Exception) {
            ImportOutcome(source.name, error = "Could not read or save this photo.")
        } catch (_: OutOfMemoryError) {
            ImportOutcome(source.name, error = "This image is too large to decode.")
        }
    }

    fun capture(film: Film, request: (File, (Boolean) -> Unit) -> Unit) {
        if (busy) return
        val pending = try {
            library.beginCapture()
        } catch (_: Exception) {
            captureMessage = "Could not create a photo file"
            return
        }
        captureBusy = true
        captureMessage = "Saving photo…"
        var completed = false
        fun complete(saved: Boolean) {
            if (completed) return
            completed = true
            if (saved) {
                val committed = runCatching { library.commitCapture(pending, film) }.isSuccess
                captureMessage = if (committed) "Photo saved to library" else "Could not save the photo"
                if (committed) libraryChanged()
            } else {
                library.discardCapture(pending)
                captureMessage = "Capture interrupted or failed · try again"
            }
            captureBusy = false
        }
        try {
            request(pending, ::complete)
        } catch (_: Exception) {
            complete(false)
        }
    }

    fun export(photo: Photo, snapshot: Recipe, maxEdge: Int?) {
        if (busy) return
        val started = EditorExport(photo, snapshot, message =
            if (maxEdge == null) "Exporting full resolution…" else "Exporting a smaller JPEG…")
        editorExport = started
        viewModelScope.launch {
            editorExport = try {
                val uri = withContext(Dispatchers.IO) { library.export(photo, snapshot, maxEdge) }
                started.copy(running = false, result = ExportReceipt(snapshot, uri),
                    reduced = maxEdge != null, message = null)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ImageInputException) {
                started.copy(running = false, message = failure.message)
            } catch (failure: ExportMemoryException) {
                started.copy(running = false, smallerEdge = failure.suggestedMaxEdge,
                    message = if (failure.suggestedMaxEdge == null) "There is not enough memory to export this photo." else null)
            } catch (_: OutOfMemoryError) {
                started.copy(running = false, smallerEdge = if (maxEdge == null) 2048 else null,
                    message = if (maxEdge == null) null else "There is not enough memory at the selected export size.")
            } catch (_: Exception) {
                started.copy(running = false, message = "Export failed. Check available storage and try again.")
            }
        }
    }

    fun dismissExportOffer() {
        editorExport = editorExport?.copy(smallerEdge = null)
    }

    fun stopBatch() {
        batchProgress = batchProgress?.copy(stopping = true)
    }

    fun consumeBatchResult() {
        batchResult = null
    }

    fun exportBatch(photos: List<Photo>) {
        if (busy || photos.isEmpty()) return
        batchResult = null
        batchProgress = BatchProgress(0, photos.size)
        viewModelScope.launch {
            val failures = arrayListOf<String>()
            var exported = 0
            var attempted = 0
            var memoryFailures = 0
            try {
                for (photo in photos) {
                    if (batchProgress?.stopping == true) break
                    try {
                        withContext(Dispatchers.IO) { library.exportSavedEdits(photo) }
                        exported++
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Exception) {
                        if (failure is ExportMemoryException) memoryFailures++
                        failures.add(photo.file.name)
                    } catch (_: OutOfMemoryError) {
                        memoryFailures++
                        failures.add(photo.file.name)
                    }
                    attempted++
                    batchProgress = batchProgress?.copy(completed = attempted)
                }
                val remaining = failures + photos.drop(attempted).map { it.file.name }
                val summary = buildString {
                    append("Exported ").append(exported).append(" of ").append(photos.size)
                    if (failures.isNotEmpty()) append(" · ").append(failures.size).append(" failed")
                    if (memoryFailures > 0) append(" · Use Darkroom for smaller exports")
                    if (attempted < photos.size) append(" · ").append(photos.size - attempted).append(" stopped before export")
                    if (exported > 0) append(" · Pictures/Aperture")
                }
                batchResult = BatchResult(remaining, summary)
            } finally {
                batchProgress = null
            }
        }
    }
}
