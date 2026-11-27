package com.aperture.photo.library

import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

internal enum class PhotoSortMode(val label: String, val captureDate: Boolean, val ascending: Boolean) {
    IMPORT_NEWEST("Imported · newest", false, false),
    IMPORT_OLDEST("Imported · oldest", false, true),
    CAPTURE_NEWEST("Captured · newest", true, false),
    CAPTURE_OLDEST("Captured · oldest", true, true)
}

internal object CaptureTimeCache {
    private const val MISSING = Long.MIN_VALUE
    private val cache = object : LruCache<String, Long>(64) {}

    @Synchronized
    fun get(file: File): Long? {
        val key = file.name
        cache.get(key)?.let { return it.takeUnless { value -> value == MISSING } }
        val timestamp = readCaptureTime(file) ?: MISSING
        cache.put(key, timestamp)
        return timestamp.takeUnless { it == MISSING }
    }

    private fun readCaptureTime(file: File): Long? {
        val exif = runCatching { ExifInterface(file) }.getOrNull() ?: return null
        val pattern = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT).apply { isLenient = false }
        return sequenceOf(ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_DATETIME)
            .mapNotNull { tag -> exif.getAttribute(tag)?.let { text ->
                val position = ParsePosition(0)
                pattern.parse(text, position)?.time?.takeIf { position.index == text.length }
            } }
            .firstOrNull()
    }
}

internal fun sortPhotos(photos: List<Photo>, mode: PhotoSortMode, captureTimes: Map<String, Long?> = emptyMap()): List<Photo> {
    val key: (Photo) -> Long = { photo ->
        if (mode.captureDate) captureTimes[photo.file.name] ?: photo.file.lastModified()
        else photo.file.lastModified()
    }
    val sorted = photos.sortedWith(compareBy<Photo> { key(it) }.thenBy { it.file.name })
    return if (mode.ascending) sorted else sorted.asReversed()
}
