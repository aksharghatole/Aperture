package com.aperture.photo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aperture.photo.imaging.AnalogPreset
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.PresetStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PresetStoreTest {
    @Test fun createRenameDeleteSurviveReopeningAndDuplicatesDoNotReplace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File.createTempFile("preset-test", "", context.cacheDir)
        check(folder.delete() && folder.mkdir())
        val file = File(folder, "presets")
        try {
            val store = PresetStore(file)
            val look = Recipe(Film.DUSK, exposure = .5f)
            assertTrue(store.read().isEmpty())
            store.create("My look", look)
            assertEquals(listOf(AnalogPreset.fromRecipe("My look", look)), PresetStore(file).read())
            val before = file.readBytes()
            assertThrows(IllegalArgumentException::class.java) { store.create("MY LOOK", Recipe()) }
            assertArrayEquals(before, file.readBytes())
            store.create("Other", Recipe())
            assertThrows(IllegalArgumentException::class.java) { store.rename("Other", "my look") }
            store.rename("My look", "Evening")
            assertEquals(listOf("Evening", "Other"), PresetStore(file).read().map { it.name })
            assertEquals(look, PresetStore(file).read().first().look)
            store.delete("Evening")
            assertEquals(listOf("Other"), PresetStore(file).read().map { it.name })
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun malformedIndexIsPreservedOnEveryMutation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("preset-corrupt", "", context.cacheDir)
        try {
            val invalid = "APERTURE-PRESETS-99\nexisting bytes".toByteArray()
            file.writeBytes(invalid)
            val store = PresetStore(file)
            assertThrows(IOException::class.java) { store.read() }
            assertThrows(IOException::class.java) { store.create("New", Recipe()) }
            assertThrows(IOException::class.java) { store.rename("Old", "New") }
            assertThrows(IOException::class.java) { store.delete("Old") }
            assertArrayEquals(invalid, file.readBytes())
        } finally {
            file.delete()
        }
    }
}
