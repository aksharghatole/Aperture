package com.aperture.photo.library

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.os.Bundle
import android.util.AtomicFile
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.EditHistoryCodec
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.PhotoRenderer
import com.aperture.photo.imaging.Recipe
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

data class Photo(val file: File, val film: Film)

internal enum class SavedEditProblem { CORRUPT, UNSUPPORTED, UNREADABLE }

internal class SavedEditException(val problem: SavedEditProblem, cause: IOException? = null) :
    IOException("Saved edits: $problem", cause)

class PhotoLibrary(private val context: Context) {
    private val directory = File(context.filesDir, "photos").apply { mkdirs() }
    private val favoritesFile = AtomicFile(File(context.filesDir, "photo-favorites"))
    internal val presets = PresetStore(File(context.filesDir, "analog-presets"))
    private val resolver = context.contentResolver

    fun favoriteNames(): Set<String> = synchronized(favoritesLock) {
        val stored = runCatching { FavoriteIndexCodec.decode(favoritesFile.readFully()) }.getOrDefault(emptySet())
        val existing = directory.listFiles().orEmpty().asSequence().filter { it.isFile && it.extension == "photo" }
            .map { it.name }.toSet()
        val valid = stored intersect existing
        if (valid != stored) writeFavorites(valid)
        valid
    }

    fun setFavorite(photo: Photo, favorite: Boolean) = synchronized(favoritesLock) {
        check(photo.file.parentFile == directory && photo.file.isFile) { "Photo does not belong to this library" }
        val names = favoriteNames().toMutableSet()
        if (favorite) names.add(photo.file.name) else names.remove(photo.file.name)
        writeFavorites(names)
    }

    fun all(): List<Photo> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "photo" }
        .sortedByDescending { it.name }
        .map { file ->
            val film = Film.entries.firstOrNull { it.name == file.nameWithoutExtension.substringAfterLast('_') }
            Photo(file, film ?: Film.CLEAN)
        }

    fun beginCapture(): File = synchronized(recoveryLock) {
        File.createTempFile("capture-", ".jpg", context.cacheDir).also { activeCaptures.add(it.name) }
    }

    fun discardCapture(pending: File) = synchronized(recoveryLock) {
        pending.delete()
        activeCaptures.remove(pending.name)
        Unit
    }

    fun commitCapture(pending: File, film: Film): Photo = try {
        val file = destination(film)
        if (!pending.renameTo(file)) throw IOException("Could not save the photo")
        Photo(file, film)
    } finally {
        discardCapture(pending)
    }

    fun import(uri: Uri): Photo {
        val pending = beginCapture()
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Could not open the photo")
            input.use { source -> pending.outputStream().use { source.copyTo(it) } }
            PhotoRenderer.validateStillImport(pending)
            return commitCapture(pending, Film.CLEAN)
        } finally {
            discardCapture(pending)
        }
    }

    internal fun recoverInterruptedWork(nowMillis: Long = System.currentTimeMillis()) {
        synchronized(recoveryLock) {
            context.cacheDir.listFiles().orEmpty().forEach { file ->
                if (file.isFile && file.name !in activeCaptures &&
                    RecoveryPolicy.isCapture(file.name, file.lastModified(), nowMillis)) file.delete()
            }
        }
        synchronized(editFileLock) {
            directory.listFiles().orEmpty().forEach { file ->
                val originalName = RecoveryPolicy.orphanCandidate(file.name, file.lastModified(), nowMillis)
                if (file.isFile && originalName != null && !File(directory, originalName).exists()) file.delete()
            }
        }
        // Some providers do not expose owner metadata or support pending queries. Leave those rows alone.
        runCatching { recoverPendingExports(nowMillis) }
    }

    @Suppress("DEPRECATION")
    private fun recoverPendingExports(nowMillis: Long) {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.IS_PENDING
        )
        val selection = "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND " +
            "${MediaStore.MediaColumns.IS_PENDING} = 1 AND ${MediaStore.MediaColumns.DATE_ADDED} < ?"
        val args = arrayOf(context.packageName, ((nowMillis - RecoveryPolicy.MaxAgeMillis) / 1000L).toString())
        val cursor = if (Build.VERSION.SDK_INT >= 30) {
            val query = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            }
            resolver.query(collection, projection, query, null)
        } else {
            resolver.query(MediaStore.setIncludePending(collection), projection, selection, args, null)
        }
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val name = it.getString(1) ?: continue
                if (id <= 0 || !RecoveryPolicy.isPendingExport(
                        name, it.getString(2), it.getString(3), context.packageName,
                        it.getLong(4), it.getInt(5) == 1, nowMillis
                    )) continue
                synchronized(recoveryLock) {
                    if (name !in activeExports) {
                        val row = ContentUris.withAppendedId(collection, id)
                        runCatching {
                            resolver.delete(row, "$selection AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                                args + name)
                        }
                    }
                }
            }
        }
    }

    private fun destination(film: Film): File =
        File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}_${film.name}.photo")

    internal fun loadEdits(photo: Photo): EditHistory? = synchronized(editFileLock) {
        val file = editFile(photo)
        val input = try {
            file.openRead()
        } catch (failure: FileNotFoundException) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized null
            throw SavedEditException(SavedEditProblem.UNREADABLE, failure)
        } catch (failure: IOException) {
            throw SavedEditException(SavedEditProblem.UNREADABLE, failure)
        }
        val bytes = try {
            input.use { stream ->
                val buffer = ByteArray(EditHistoryCodec.MaxEncodedSize + 1)
                var size = 0
                while (size < buffer.size) {
                    val count = stream.read(buffer, size, buffer.size - size)
                    if (count < 0) break
                    size += count
                }
                buffer.copyOf(size)
            }
        } catch (failure: IOException) {
            throw SavedEditException(SavedEditProblem.UNREADABLE, failure)
        }
        val encoded = bytes.toString(Charsets.UTF_8)
        if (EditHistoryCodec.isUnsupportedVersion(encoded)) throw SavedEditException(SavedEditProblem.UNSUPPORTED)
        if (bytes.size > EditHistoryCodec.MaxEncodedSize) throw SavedEditException(SavedEditProblem.CORRUPT)
        EditHistoryCodec.decode(encoded) ?: throw SavedEditException(SavedEditProblem.CORRUPT)
    }

    internal fun saveEdits(
        photo: Photo, history: EditHistory, replaceProblem: SavedEditProblem? = null
    ) = synchronized(editFileLock) {
        val file = editFile(photo)
        try {
            loadEdits(photo)
        } catch (failure: SavedEditException) {
            if (failure.problem != replaceProblem || failure.problem == SavedEditProblem.UNREADABLE) throw failure
        }
        val output = file.startWrite()
        try {
            output.write(EditHistoryCodec.encode(history).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
    }

    fun delete(photo: Photo) = synchronized(editFileLock) {
        check(photo.file.parentFile == directory) { "Photo does not belong to this library" }
        if (!photo.file.isFile) throw FileNotFoundException("Photo no longer exists")
        if (!photo.file.delete()) throw IOException("Could not remove the photo")
        AtomicFile(File(directory, "${photo.file.name}.edits")).delete()
        synchronized(favoritesLock) { writeFavorites(favoriteNames() - photo.file.name) }
    }

    private fun editFile(photo: Photo): AtomicFile {
        check(photo.file.parentFile == directory && photo.file.isFile)
        return AtomicFile(File(directory, "${photo.file.name}.edits"))
    }

    private fun writeFavorites(names: Set<String>) {
        val output = favoritesFile.startWrite()
        try {
            output.write(FavoriteIndexCodec.encode(names))
            favoritesFile.finishWrite(output)
        } catch (failure: Throwable) {
            favoritesFile.failWrite(output)
            throw failure
        }
    }

    private companion object {
        val recoveryLock = Any()
        val activeCaptures = mutableSetOf<String>()
        val activeExports = mutableSetOf<String>()
        val editFileLock = Any()
        val favoritesLock = Any()
    }

    internal fun thumbnail(photo: Photo): Bitmap {
        val recipe = try {
            loadEdits(photo)?.current
        } catch (_: IOException) {
            null
        } ?: Recipe(photo.film)
        val source = PhotoRenderer.decode(photo.file, 320)
        return try {
            PhotoRenderer.render(source, recipe)
        } finally {
            source.recycle()
        }
    }

    fun exportSavedEdits(photo: Photo): Uri =
        export(photo, loadEdits(photo)?.current ?: Recipe(photo.film))

    fun export(photo: Photo, recipe: Recipe, maxEdge: Int? = null): Uri {
        val source = PhotoRenderer.decodeForExport(photo.file, recipe, maxEdge)
        val rendered = try {
            PhotoRenderer.render(source, recipe)
        } finally {
            source.recycle()
        }
        val name = "Aperture-${System.currentTimeMillis()}-${UUID.randomUUID()}.jpg"
        synchronized(recoveryLock) { activeExports.add(name) }
        try {
            PhotoRenderer.flattenForJpeg(rendered)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Aperture")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create the export")
            try {
                val stream = resolver.openOutputStream(uri) ?: throw IOException("Could not write the export")
                stream.use {
                    // Encode the rendered pixels without transferring source EXIF.
                    if (!rendered.compress(android.graphics.Bitmap.CompressFormat.JPEG, 96, it)) {
                        throw IOException("Could not encode the export")
                    }
                }
                val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                if (resolver.update(uri, published, null, null) != 1) throw IOException("Could not publish the export")
                return uri
            } catch (failure: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw failure
            }
        } finally {
            synchronized(recoveryLock) { activeExports.remove(name) }
            rendered.recycle()
        }
    }
}
