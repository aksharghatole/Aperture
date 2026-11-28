package com.aperture.photo.library

import com.aperture.photo.imaging.AnalogPreset
import com.aperture.photo.imaging.CropWindow
import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.EditHistoryCodec
import java.nio.ByteBuffer

/** The preset index has its own version; each look uses the validated recipe format. */
internal object PresetCodec {
    const val MaxPresets = 50
    const val MaxNameLength = 40
    const val MaxBytes = 65_536
    private const val Header = "APERTURE-PRESETS-1"

    fun normalizedName(raw: String): String {
        val name = raw.trim()
        require(validName(name)) { "Enter a name of 1–40 characters without control characters." }
        return name
    }

    private fun validName(name: String): Boolean =
        name.length in 1..MaxNameLength && name == name.trim() &&
            name.none { it.isISOControl() || it == '\uFFFD' || it == '\u2028' || it == '\u2029' }

    fun encode(presets: List<AnalogPreset>): ByteArray {
        val encoded = buildString {
            append(Header)
            for (preset in presets) {
                append('\n').append(preset.name).append('\t')
                append(EditHistoryCodec.encode(EditHistory(preset.look)).replace('\n', '\t'))
            }
        }.toByteArray(Charsets.UTF_8)
        require(decode(encoded) == presets) { "Invalid preset data." }
        return encoded
    }

    fun decode(bytes: ByteArray): List<AnalogPreset>? {
        if (bytes.size > MaxBytes) return null
        val text = runCatching { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() }
            .getOrNull() ?: return null
        val lines = text.split('\n')
        if (lines.firstOrNull() != Header || lines.size > MaxPresets + 1) return null
        val result = mutableListOf<AnalogPreset>()
        for (line in lines.drop(1)) {
            val fields = line.split('\t')
            if (fields.size != 3) return null
            val name = fields[0]
            if (!validName(name) || result.any { it.name.equals(name, ignoreCase = true) }) return null
            val history = EditHistoryCodec.decode(fields[1] + "\n" + fields[2]) ?: return null
            val look = history.current
            if (history.snapshots.size != 1 || look.crop != CropWindow.Full || look.quarterTurns != 0) return null
            result.add(AnalogPreset(name, look))
        }
        return result
    }
}
