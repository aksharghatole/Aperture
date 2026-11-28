package com.aperture.photo

import com.aperture.photo.library.PhotoImport
import com.aperture.photo.library.PhotoImportException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoImportTest {
    private val plenty = 64L * 1024 * 1024

    @Test fun copiesUnknownLengthWithoutChangingBytes() {
        val input = ByteArray(140_000) { it.toByte() }
        val output = ByteArrayOutputStream()
        PhotoImport.copy(ByteArrayInputStream(input), output, expectedBytes = null) { plenty }
        assertArrayEquals(input, output.toByteArray())
    }

    @Test fun preflightsStorageForKnownLength() {
        val output = ByteArrayOutputStream()
        assertThrows(PhotoImportException::class.java) {
            PhotoImport.copy(ByteArrayInputStream(ByteArray(512)), output, expectedBytes = 512) { 16L * 1024 * 1024 }
        }
        assertEquals(0, output.size())
    }

    @Test fun enforcesLimitWhenProviderUnderreportsLength() {
        val output = ByteArrayOutputStream()
        assertThrows(PhotoImportException::class.java) {
            PhotoImport.copy(ByteArrayInputStream(ByteArray(140_000)), output, expectedBytes = 10,
                maxBytes = 100_000) { plenty }
        }
        assertEquals(64 * 1024, output.size())
    }

    @Test fun rechecksFreeSpaceForEachChunkOfUnknownLength() {
        val output = ByteArrayOutputStream()
        assertThrows(PhotoImportException::class.java) {
            PhotoImport.copy(ByteArrayInputStream(ByteArray(140_000)), output, expectedBytes = null) {
                if (output.size() == 0) plenty else 0L
            }
        }
        assertEquals(64 * 1024, output.size())
    }
}
