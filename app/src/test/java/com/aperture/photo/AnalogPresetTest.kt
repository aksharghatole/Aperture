package com.aperture.photo

import com.aperture.photo.imaging.AnalogPreset
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.imaging.RgbCurves
import com.aperture.photo.imaging.ToneCurve
import com.aperture.photo.library.PresetCodec
import org.junit.Assert.*
import org.junit.Test

class AnalogPresetTest {
    private val styled = Recipe(
        film = Film.AMBER, exposure = .8f, contrast = -.2f, saturation = .4f,
        warmth = -.1f, vignette = .5f, shadows = .3f, highlights = -.6f,
        curves = RgbCurves(red = ToneCurve(5, 115, 220)), grainIntensity = .25f, grainSize = .6f,
        quarterTurns = 3, crop = CropWindow(.1f, .1f, .9f, .7f)
    )

    @Test fun presetAppliesOneUndoableLookWithoutChangingTargetGeometry() {
        val preset = AnalogPreset.fromRecipe("Warm", styled)
        assertEquals(CropWindow.Full, preset.look.crop)
        assertEquals(0, preset.look.quarterTurns)
        val target = Recipe(Film.SILVER, quarterTurns = 1, crop = CropWindow(.2f, .3f, .7f, .8f))
        val expected = styled.copy(quarterTurns = target.quarterTurns, crop = target.crop)
        val edited = EditHistory(target).commit(preset.applyTo(target))
        assertEquals(expected, edited.current)
        assertEquals(2, edited.snapshots.size)
        assertEquals(target, edited.undo().current)
        assertEquals(expected, edited.undo().redo().current)
    }

    @Test fun indexRoundTripPreservesUnicodeNamesAndEveryLookAdjustment() {
        val presets = listOf(AnalogPreset.fromRecipe("Café, dusk 🌆", styled), AnalogPreset.fromRecipe("Neutral", Recipe()))
        assertEquals(presets, PresetCodec.decode(PresetCodec.encode(presets)))
        assertEquals(emptyList<AnalogPreset>(), PresetCodec.decode(PresetCodec.encode(emptyList())))
        assertEquals("Café", PresetCodec.normalizedName("  Café  "))
    }

    @Test fun rejectsMalformedOrUnsupportedFilesAndDuplicateNames() {
        val preset = AnalogPreset.fromRecipe("Warm", styled)
        val data = PresetCodec.encode(listOf(preset)).toString(Charsets.UTF_8)
        assertNull(PresetCodec.decode(data.replace("PRESETS-1", "PRESETS-2").toByteArray()))
        assertNull(PresetCodec.decode(data.substringBeforeLast(',').toByteArray()))
        assertNull(PresetCodec.decode(data.replace("AMBER", "UNKNOWN").toByteArray()))
        assertNull(PresetCodec.decode((data + "\n" + data.substringAfter('\n').replace("Warm", "warm")).toByteArray()))
        assertNull(PresetCodec.decode(data.replace("0.8", "NaN").toByteArray()))
        assertNull(PresetCodec.decode(byteArrayOf(0xC3.toByte(), 0x28)))
        assertNull(PresetCodec.decode(ByteArray(PresetCodec.MaxBytes + 1)))
        assertThrows(IllegalArgumentException::class.java) {
            PresetCodec.encode(listOf(preset.copy(look = styled)))
        }
        for (bad in listOf("", "   ", "a\tb", "a\nb", "a".repeat(41), "a\u0000b")) {
            assertThrows(IllegalArgumentException::class.java) { PresetCodec.normalizedName(bad) }
        }
    }

    @Test fun boundsNumberOfPresets() {
        val presets = (1..PresetCodec.MaxPresets).map { AnalogPreset.fromRecipe("Look $it", styled) }
        assertEquals(presets, PresetCodec.decode(PresetCodec.encode(presets)))
        assertThrows(IllegalArgumentException::class.java) {
            PresetCodec.encode(presets + AnalogPreset.fromRecipe("Extra", styled))
        }
    }
}
