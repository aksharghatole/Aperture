package com.aperture.photo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.aperture.photo.imaging.InspectionTransform

internal class EditorInspectionState {
    var transform by mutableStateOf(InspectionTransform())
        private set
    private var viewport by mutableStateOf(IntSize.Zero)

    fun resize(size: IntSize) {
        if (viewport == size) return
        viewport = size
        reset()
    }

    fun reset() {
        transform = InspectionTransform()
    }

    fun move(aspect: Float, zoomFactor: Float, center: Offset? = null, pan: Offset = Offset.Zero) {
        val width = viewport.width.toFloat()
        val height = viewport.height.toFloat()
        if (width <= 0f || height <= 0f) return
        transform = transform.update(width / height, aspect, zoomFactor,
            center?.let { it.x / width - .5f } ?: 0f, center?.let { it.y / height - .5f } ?: 0f,
            pan.x / width, pan.y / height)
    }

    fun pan(aspect: Float, x: Float, y: Float): Boolean {
        val previous = transform
        move(aspect, 1f, pan = Offset(viewport.width * x, viewport.height * y))
        return transform != previous
    }
}

@Composable
internal fun InspectablePhoto(image: Bitmap, description: String, inspection: EditorInspectionState) {
    val bitmap = remember(image) { image.asImageBitmap() }
    val aspect = bitmap.width.toFloat() / bitmap.height
    val zoomed = inspection.transform.zoom > 1f
    Image(bitmap, description, Modifier.fillMaxSize()
        .semantics {
            if (zoomed) customActions = listOf(
                CustomAccessibilityAction("Pan left") { inspection.pan(aspect, -.2f, 0f) },
                CustomAccessibilityAction("Pan right") { inspection.pan(aspect, .2f, 0f) },
                CustomAccessibilityAction("Pan up") { inspection.pan(aspect, 0f, -.2f) },
                CustomAccessibilityAction("Pan down") { inspection.pan(aspect, 0f, .2f) }
            )
        }
        .pointerInput(inspection, aspect) {
            detectTransformGestures { center, pan, zoom, _ -> inspection.move(aspect, zoom, center, pan) }
        }
        .pointerInput(inspection, aspect) {
            detectTapGestures(onDoubleTap = { tap ->
                if (inspection.transform.zoom > 1f) inspection.reset() else inspection.move(aspect, 2f, tap)
            })
        }
        .graphicsLayer {
            val transform = inspection.transform
            scaleX = transform.zoom
            scaleY = transform.zoom
            translationX = transform.x * size.width
            translationY = transform.y * size.height
        }, contentScale = ContentScale.Fit)
}
