package com.aperture.photo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aperture.photo.imaging.AnalogPreset
import com.aperture.photo.imaging.Recipe
import com.aperture.photo.library.PresetCodec
import com.aperture.photo.library.PresetStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AnalogPresets(
    store: PresetStore,
    current: Recipe,
    onClose: () -> Unit,
    onApply: (Recipe) -> Unit
) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var presets by remember { mutableStateOf<List<AnalogPreset>?>(null) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf<String?>(null) }

    fun load() {
        busy = true
        error = null
        scope.launch {
            try {
                presets = withContext(Dispatchers.IO) { store.read() }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                error = "Saved presets could not be opened. The existing file was preserved."
            } finally {
                busy = false
            }
        }
    }
    fun mutate(action: () -> List<AnalogPreset>, clearName: Boolean = false) {
        busy = true
        error = null
        scope.launch {
            try {
                presets = withContext(Dispatchers.IO) { action() }
                if (clearName) {
                    name = ""
                    renaming = null
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: IllegalArgumentException) {
                error = failure.message ?: "Invalid preset."
            } catch (_: Exception) {
                error = "Presets could not be changed. Try again."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(store) { load() }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("AnalogLab presets") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Save a look to reuse on other photos. Crop and rotation stay with each photo.")
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val available = presets
                if (available == null) {
                    if (!busy) TextButton(onClick = ::load) { Text("Retry") }
                } else {
                    OutlinedTextField(
                        value = name, onValueChange = { if (it.length <= PresetCodec.MaxNameLength) name = it },
                        label = { Text(if (renaming == null) "New preset name" else "Rename preset") },
                        singleLine = true, enabled = !busy
                    )
                    Row {
                        TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                            val oldName = renaming
                            val requestedName = name
                            mutate({
                                if (oldName == null) store.create(requestedName, current)
                                else store.rename(oldName, requestedName)
                            }, clearName = true)
                        }) { Text(if (renaming == null) "Save current look" else "Save name") }
                        if (renaming != null) TextButton(enabled = !busy, onClick = {
                            renaming = null
                            name = ""
                        }) { Text("Cancel") }
                    }
                    if (available.isEmpty()) Text("No saved presets yet.")
                    else {
                        for (preset in available) {
                            Column {
                                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    TextButton(enabled = !busy, onClick = { onApply(preset.applyTo(current)) }) {
                                        Text("Apply")
                                    }
                                    TextButton(enabled = !busy, onClick = {
                                        renaming = preset.name
                                        name = preset.name
                                        scope.launch { scroll.animateScrollTo(0) }
                                    }) {
                                        Text("Rename")
                                    }
                                    TextButton(enabled = !busy, onClick = { confirmingDelete = preset.name }) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Done") } }
    )
    confirmingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("Delete preset?") },
            text = { Text("Remove “$target”? Photos and saved edits are unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = null
                    mutate({ store.delete(target) })
                    if (renaming == target) {
                        renaming = null
                        name = ""
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = null }) { Text("Cancel") } }
        )
    }
}
