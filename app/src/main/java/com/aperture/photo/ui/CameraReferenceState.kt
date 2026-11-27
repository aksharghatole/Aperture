package com.aperture.photo.ui

import android.app.Application
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aperture.photo.camera.ReferenceAlignment
import com.aperture.photo.imaging.PhotoRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Holds one downsampled reference for the lifetime of the activity's camera session. */
internal class CameraReferenceState(application: Application) : AndroidViewModel(application) {
    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    var opacity by mutableFloatStateOf(.35f)
    var alignment by mutableStateOf(ReferenceAlignment.Centered)
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var decodeJob: Job? = null

    fun load(uri: Uri) {
        if (loading) return
        loading = true
        error = null
        decodeJob = viewModelScope.launch {
            try {
                image = withContext(Dispatchers.IO) {
                    val source = ImageDecoder.createSource(getApplication<Application>().contentResolver, uri)
                    PhotoRenderer.decode(source, maxEdge = 1280).asImageBitmap()
                }
                alignment = ReferenceAlignment.Centered
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Could not open that reference. Choose another image."
            } catch (_: OutOfMemoryError) {
                error = "Not enough memory for that reference. Choose a smaller image."
            } finally {
                loading = false
            }
        }
    }

    fun remove() {
        decodeJob?.cancel()
        image = null
        alignment = ReferenceAlignment.Centered
        opacity = .35f
        error = null
    }
}
