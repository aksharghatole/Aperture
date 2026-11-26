package com.aperture.photo

import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.EditHistoryCodec
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.Recipe
import org.junit.Assert.*
import org.junit.Test

class EditHistoryCodecTest {
    @Test fun roundTripRestoresCurrentRecipeAndRedoBranch() {
        val initial = Recipe(Film.AMBER)
        val adjusted = initial.copy(exposure = 1f, warmth = -.2f)
        val rotated = adjusted.copy(quarterTurns = 1)
        val original = EditHistory(initial).commit(adjusted).commit(rotated).undo()
        val restored = EditHistoryCodec.decode(EditHistoryCodec.encode(original))!!
        assertEquals(original.snapshots, restored.snapshots)
        assertEquals(adjusted, restored.current)
        assertEquals(initial, restored.undo().current)
        assertEquals(rotated, restored.redo().current)
    }

    @Test fun rejectsInvalidAndUnknownEditFiles() {
        val valid = EditHistoryCodec.encode(EditHistory(Recipe()))
        assertNull(EditHistoryCodec.decode(valid.replaceFirst("1,0", "2,0")))
        assertNull(EditHistoryCodec.decode(valid.replaceFirst("1,0", "1,3")))
        assertNull(EditHistoryCodec.decode(valid.replaceFirst("0.0", "NaN")))
        assertNull(EditHistoryCodec.decode(valid.replace("CLEAN", "UNKNOWN")))
        assertNull(EditHistoryCodec.decode(valid.dropLast(2)))
        assertNull(EditHistoryCodec.decode("x".repeat(8193)))
    }
}
