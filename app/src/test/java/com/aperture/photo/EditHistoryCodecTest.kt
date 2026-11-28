package com.aperture.photo

import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.EditHistoryCodec
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.imaging.RgbCurves
import com.aperture.photo.imaging.ToneCurve
import org.junit.Assert.*
import org.junit.Test

class EditHistoryCodecTest {
    @Test fun roundTripRestoresCurrentRecipeAndRedoBranch() {
        val initial = Recipe(Film.AMBER)
        val adjusted = initial.copy(exposure = 1f, warmth = -.2f, shadows = .6f, highlights = -.5f, curves = RgbCurves(red = ToneCurve(7, 113, 225)))
        val rotated = adjusted.copy(quarterTurns = 1, crop = CropWindow(.1f, .2f, .8f, .9f))
        val original = EditHistory(initial).commit(adjusted).commit(rotated).undo()
        val restored = EditHistoryCodec.decode(EditHistoryCodec.encode(original))!!
        assertEquals(original.snapshots, restored.snapshots)
        assertEquals(adjusted, restored.current)
        assertEquals(initial, restored.undo().current)
        assertEquals(rotated, restored.redo().current)
    }

    @Test fun opensHistoriesSavedBeforeCropWasIntroduced() {
        val versionOne = "1,0\nCLEAN,0.5,0.0,0.0,0.0,0.0,1"
        val recipe = EditHistoryCodec.decode(versionOne)!!.current
        assertEquals(Recipe(exposure = .5f, quarterTurns = 1), recipe)
        assertEquals(CropWindow.Full, recipe.crop)
    }

    @Test fun opensHistoriesSavedWithCropAndNoToneAdjustments() {
        val versionTwo = "2,0\nCLEAN,0.5,0.0,0.0,0.0,0.0,1,0.25,0.0,0.75,1.0"
        assertEquals(Recipe(exposure = .5f, quarterTurns = 1, crop = CropWindow(.25f, 0f, .75f, 1f)),
            EditHistoryCodec.decode(versionTwo)?.current)
    }

    @Test fun opensVersionFourCurvesWithNeutralGrain() {
        val versionFour = EditHistoryCodec.encode(EditHistory(Recipe(curves = RgbCurves(red = ToneCurve(8, 120, 230)))))
            .replaceFirst("5,0", "4,0").lineSequence().mapIndexed { index, line ->
                if (index == 0) line else line.substringBeforeLast(',').substringBeforeLast(',')
            }.joinToString("\n")
        val recipe = EditHistoryCodec.decode(versionFour)!!.current
        assertEquals(RgbCurves(red = ToneCurve(8, 120, 230)), recipe.curves)
        assertEquals(0f, recipe.grainIntensity)
        assertEquals(0f, recipe.grainSize)
    }

    @Test fun roundTripsGrainInSavedHistory() {
        val history = EditHistory(Recipe()).commit(Recipe(grainIntensity = .65f, grainSize = .8f))
        assertEquals(history, EditHistoryCodec.decode(EditHistoryCodec.encode(history)))
    }

    @Test fun loadsVersionThreeWithNeutralCurves() {
        val saved = "3,0\nCLEAN,0.0,0.0,0.0,0.0,0.0,0,0.0,0.0,1.0,1.0,0.25,-0.5"
        assertEquals(Recipe(shadows = .25f, highlights = -.5f), EditHistoryCodec.decode(saved)?.current)
    }

    @Test fun distinguishesFutureVersionsFromDamagedHeaders() {
        assertTrue(EditHistoryCodec.isUnsupportedVersion("99,0\nnew-format"))
        assertFalse(EditHistoryCodec.isUnsupportedVersion("99,"))
        assertFalse(EditHistoryCodec.isUnsupportedVersion("4,0\ntruncated"))
        assertFalse(EditHistoryCodec.isUnsupportedVersion(""))
    }

    @Test fun rejectsInvalidAndUnknownEditFiles() {
        val valid = EditHistoryCodec.encode(EditHistory(Recipe()))
        assertNull(EditHistoryCodec.decode(valid.replaceFirst("5,0", "6,0")))
        assertNull(EditHistoryCodec.decode(valid.replaceFirst("5,0", "5,3")))
        assertNull(EditHistoryCodec.decode(valid.replaceFirst("0.0", "NaN")))
        assertNull(EditHistoryCodec.decode(valid.replace("CLEAN", "UNKNOWN")))
        assertNull(EditHistoryCodec.decode(valid.replaceAfterLast(",", "1.1")))
        assertNull(EditHistoryCodec.decode(valid.substringBeforeLast(',')))
        assertNull(EditHistoryCodec.decode("x".repeat(EditHistoryCodec.MaxEncodedSize + 1)))
    }
}
