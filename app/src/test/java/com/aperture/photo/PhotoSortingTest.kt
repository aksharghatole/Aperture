package com.aperture.photo

import com.aperture.photo.imaging.Film
import com.aperture.photo.library.Photo
import com.aperture.photo.library.PhotoSortMode
import com.aperture.photo.library.sortPhotos
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PhotoSortingTest {
    @Test
    fun sortsByImportDateOldestOrNewest() {
        val earlier = photo("earlier", 100L)
        val later = photo("later", 200L)
        try {
            assertEquals(listOf(later, earlier), sortPhotos(listOf(earlier, later), PhotoSortMode.IMPORT_NEWEST))
            assertEquals(listOf(earlier, later), sortPhotos(listOf(earlier, later), PhotoSortMode.IMPORT_OLDEST))
        } finally {
            earlier.file.delete()
            later.file.delete()
        }
    }

    @Test
    fun captureDateSortFallsBackToImportDateWhenCaptureTimeIsMissing() {
        val earlierImport = photo("earlier", 100L)
        val laterImport = photo("later", 200L)
        try {
            val captureDates = mapOf(earlierImport.file.name to 300L, laterImport.file.name to null)
            assertEquals(listOf(earlierImport, laterImport),
                sortPhotos(listOf(earlierImport, laterImport), PhotoSortMode.CAPTURE_NEWEST, captureDates))
        } finally {
            earlierImport.file.delete()
            laterImport.file.delete()
        }
    }

    private fun photo(prefix: String, modified: Long): Photo {
        val file = File.createTempFile(prefix, ".photo")
        file.setLastModified(modified)
        return Photo(file, Film.CLEAN)
    }
}
