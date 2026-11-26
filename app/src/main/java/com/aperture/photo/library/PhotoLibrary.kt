package com.aperture.photo.library

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
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

class PhotoLibrary(private val context: Context) {
    private val directory = File(context.filesDir, "photos").apply { mkdirs() }
    private val resolver = context.contentResolver

    fun all(): List<Photo> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "photo" }
        .sortedByDescending { it.name }
        .map { file ->
            val film = Film.entries.firstOrNull { it.name == file.nameWithoutExtension.substringAfterLast('_') }
            Photo(file, film ?: Film.CLEAN)
        }

    fun beginCapture(): File = File.createTempFile("capture-", ".jpg", context.cacheDir)

    fun commitCapture(pending: File, film: Film): Photo = try {
        val file = destination(film)
        if (!pending.renameTo(file)) throw IOException("Could not save the photo")
        Photo(file, film)
    } finally {
        pending.delete()
    }

    fun import(uri: Uri): Photo {
        val pending = beginCapture()
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Could not open the photo")
            input.use { source -> pending.outputStream().use { source.copyTo(it) } }
            PhotoRenderer.decode(pending, 320).recycle()
            return commitCapture(pending, Film.CLEAN)
        } finally {
            pending.delete()
        }
    }

    private fun destination(film: Film): File =
        File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}_${film.name}.photo")

    internal fun loadEdits(photo: Photo): EditHistory? = synchronized(editFileLock) {
        val file = editFile(photo)
        val input = try {
            file.openRead()
        } catch (failure: FileNotFoundException) {
            if (!file.baseFile.exists()) return@synchronized null
            throw failure
        }
        val bytes = input.use { stream ->
            val buffer = ByteArray(EditHistoryCodec.MaxEncodedSize + 1)
            var size = 0
            while (size < buffer.size) {
                val count = stream.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            buffer.copyOf(size)
        }
        if (bytes.size > EditHistoryCodec.MaxEncodedSize) throw IOException("Edit history is too large")
        EditHistoryCodec.decode(bytes.toString(Charsets.UTF_8)) ?: throw IOException("Invalid edit history")
    }

    internal fun saveEdits(photo: Photo, history: EditHistory) = synchronized(editFileLock) {
        val file = editFile(photo)
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
    }

    private fun editFile(photo: Photo): AtomicFile {
        check(photo.file.parentFile == directory && photo.file.isFile)
        return AtomicFile(File(directory, "${photo.file.name}.edits"))
    }

    private companion object {
        val editFileLock = Any()
    }

    fun exportSavedEdits(photo: Photo): Uri =
        export(photo, loadEdits(photo)?.current ?: Recipe(photo.film))

    fun export(photo: Photo, recipe: Recipe): Uri {
        val source = PhotoRenderer.decode(photo.file)
        val rendered = try {
            PhotoRenderer.render(source, recipe)
        } finally {
            source.recycle()
        }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Aperture-${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Aperture")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create the export")
            try {
                val stream = resolver.openOutputStream(uri) ?: throw IOException("Could not write the export")
                stream.use {
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
            rendered.recycle()
        }
    }
}
