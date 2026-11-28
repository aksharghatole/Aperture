package com.aperture.photo.library

import android.util.AtomicFile
import com.aperture.photo.imaging.AnalogPreset
import com.aperture.photo.imaging.Recipe
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Separate from photos and edit sidecars; a malformed index is never overwritten. */
internal class PresetStore(path: File) {
    private val file = AtomicFile(path)

    fun read(): List<AnalogPreset> = synchronized(lock) {
        val input = try {
            file.openRead()
        } catch (failure: FileNotFoundException) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
                return@synchronized emptyList()
            }
            throw failure
        }
        val bytes = input.use { stream ->
            val buffer = ByteArray(PresetCodec.MaxBytes + 1)
            var size = 0
            while (size < buffer.size) {
                val count = stream.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            buffer.copyOf(size)
        }
        PresetCodec.decode(bytes) ?: throw IOException("Saved presets cannot be read.")
    }

    fun create(rawName: String, recipe: Recipe): List<AnalogPreset> = change { presets ->
        val name = PresetCodec.normalizedName(rawName)
        require(presets.size < PresetCodec.MaxPresets) { "Delete a preset before adding another (limit 50)." }
        require(presets.none { it.name.equals(name, ignoreCase = true) }) { "That preset name already exists." }
        presets + AnalogPreset.fromRecipe(name, recipe)
    }

    fun rename(oldName: String, rawName: String): List<AnalogPreset> = change { presets ->
        val name = PresetCodec.normalizedName(rawName)
        require(presets.any { it.name == oldName }) { "That preset no longer exists." }
        require(presets.none { it.name != oldName && it.name.equals(name, ignoreCase = true) }) {
            "That preset name already exists."
        }
        presets.map { if (it.name == oldName) it.copy(name = name) else it }
    }

    fun delete(name: String): List<AnalogPreset> = change { presets ->
        require(presets.any { it.name == name }) { "That preset no longer exists." }
        presets.filterNot { it.name == name }
    }

    private fun change(transform: (List<AnalogPreset>) -> List<AnalogPreset>): List<AnalogPreset> = synchronized(lock) {
        val updated = transform(read())
        val bytes = PresetCodec.encode(updated)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
        updated
    }

    private companion object {
        val lock = Any()
    }
}
