package com.aperture.photo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aperture.photo.camera.CompositionAnchor

@Composable
internal fun CameraFramingMenu(
    anchor: CompositionAnchor, hasMarker: Boolean, enabled: Boolean, canMark: Boolean,
    selectAnchor: (CompositionAnchor) -> Unit, mark: () -> Unit, clear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(enabled = enabled, onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "Framing target, ${anchor.title}" }) {
            Text("Target · ${anchor.title}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CompositionAnchor.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(if (item == CompositionAnchor.AUTO) "Auto · scene estimate" else item.title) },
                    onClick = {
                        selectAnchor(item)
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (hasMarker) "Move manual marker" else "Mark subject manually") },
                enabled = canMark,
                onClick = {
                    mark()
                    expanded = false
                }
            )
            if (hasMarker) DropdownMenuItem(
                text = { Text("Clear manual marker") },
                onClick = {
                    clear()
                    expanded = false
                }
            )
        }
    }
}
