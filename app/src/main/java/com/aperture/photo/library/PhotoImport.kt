package com.aperture.photo.library

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal data class ImportSource(val name: String, val bytes: Long? = null)

internal class PhotoImportException(message: String) : IOException(message)

internal object PhotoImport {
    const val MaxFiles = 50
    private const val MaxBytes = 256L * 1024 * 1024
    private const val StorageReserve = 16L * 1024 * 1024

    fun copy(
        input: InputStream,
        output: OutputStream,
        expectedBytes: Long?,
        maxBytes: Long = MaxBytes,
        availableBytes: () -> Long
    ) {
        if (expectedBytes != null) {
            checkSize(expectedBytes, maxBytes)
            checkStorage(expectedBytes, availableBytes())
        }
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) throw PhotoImportException("Could not read the photo.")
            checkSize(copied + count, maxBytes)
            checkStorage(count.toLong(), availableBytes())
            output.write(buffer, 0, count)
            copied += count
        }
    }

    private fun checkSize(bytes: Long, maxBytes: Long) {
        if (bytes > maxBytes) throw PhotoImportException("Image exceeds the 256 MB import limit.")
    }

    private fun checkStorage(bytes: Long, available: Long) {
        if (available < StorageReserve || bytes > available - StorageReserve) {
            throw PhotoImportException("Not enough free storage to import this photo.")
        }
    }
}
