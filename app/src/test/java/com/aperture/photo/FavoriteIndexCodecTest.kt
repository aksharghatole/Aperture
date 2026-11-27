package com.aperture.photo

import com.aperture.photo.imaging.Film
import com.aperture.photo.library.FavoriteIndexCodec
import com.aperture.photo.library.Photo
import com.aperture.photo.library.filterFavoritePhotos
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FavoriteIndexCodecTest {
    @Test
    fun roundTripsOnlySafePhotoNamesAndRecoversFromCorruptData() {
        val encoded = FavoriteIndexCodec.encode(setOf("one.photo", "../escape.photo", "two.photo"))
        assertEquals(setOf("one.photo", "two.photo"), FavoriteIndexCodec.decode(encoded))
        assertEquals(emptySet<String>(), FavoriteIndexCodec.decode(byteArrayOf(0, 1, 2)))
    }

    @Test
    fun favoriteFilterPreservesVisibleSelectionSetAndAllPhotosMode() {
        val first = Photo(File("one.photo"), Film.CLEAN)
        val second = Photo(File("two.photo"), Film.CLEAN)
        val photos = listOf(first, second)
        assertEquals(listOf(second), filterFavoritePhotos(photos, true, setOf("two.photo")))
        assertEquals(photos, filterFavoritePhotos(photos, false, setOf("two.photo")))
    }
}
