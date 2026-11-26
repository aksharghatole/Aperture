package com.aperture.photo.camera

import android.graphics.Paint
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.PhotoRenderer
import com.aperture.photo.imaging.Recipe
import java.io.File
import java.util.concurrent.Executors

class CameraSession(
    private val view: PreviewView,
    private val owner: LifecycleOwner,
    private val onScene: (Scene) -> Unit,
    private val onReady: (ready: Boolean, canFlip: Boolean, hasAnalysis: Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val main = ContextCompat.getMainExecutor(view.context)
    private val analyzer = Executors.newSingleThreadExecutor()
    private val providerFuture = ProcessCameraProvider.getInstance(view.context)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var capture: ImageCapture? = null
    private var analysis: ImageAnalysis? = null
    private var closed = false
    private var canFlip = false
    private var front = false
    private var film = Film.CLEAN
    @Volatile var shootingMode = ShootingMode.STREET

    private val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) bind()
    }

    init {
        view.addOnLayoutChangeListener(layoutListener)
        view.previewStreamState.observe(owner) { state ->
            val streaming = state == PreviewView.StreamState.STREAMING && camera != null
            if (streaming) applyFilm()
            onReady(streaming, canFlip, analysis != null)
        }
        providerFuture.addListener({
            if (!closed) {
                try {
                    provider = providerFuture.get()
                    bind()
                } catch (_: Exception) {
                    onError("Camera services are unavailable.")
                }
            }
        }, main)
    }

    private fun bind() {
        val available = provider ?: return
        val viewport = view.viewPort ?: return
        if (closed) return
        onReady(false, false, false)
        unbind()
        try {
            val hasBack = available.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            val hasFront = available.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            if (!hasFront && !hasBack) {
                onError("This device has no usable camera.")
                return
            }
            front = hasFront && (front || !hasBack)
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val rotation = view.display.rotation
            val previewUseCase = Preview.Builder().setTargetRotation(rotation).build()
            previewUseCase.setSurfaceProvider(view.surfaceProvider)
            val captureUseCase = ImageCapture.Builder().setTargetRotation(rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            val analysisUseCase = ImageAnalysis.Builder().setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            val sceneAnalyzer = FrameAnalyzer(front, { shootingMode }) { mode, scene ->
                main.execute {
                    if (!closed && analysis === analysisUseCase && mode == shootingMode) onScene(scene)
                }
            }
            val group = UseCaseGroup.Builder().setViewPort(viewport)
                .addUseCase(previewUseCase).addUseCase(captureUseCase)
            preview = previewUseCase
            capture = captureUseCase
            canFlip = hasBack && hasFront
            try {
                camera = available.bindToLifecycle(owner, selector, group.addUseCase(analysisUseCase).build())
                analysis = analysisUseCase
                analysisUseCase.setAnalyzer(analyzer, sceneAnalyzer)
            } catch (_: IllegalArgumentException) {
                // Some camera devices only support the preview and still-capture combination.
                available.unbind(previewUseCase, captureUseCase, analysisUseCase)
                analysis = null
                val basicGroup = UseCaseGroup.Builder().setViewPort(viewport)
                    .addUseCase(previewUseCase).addUseCase(captureUseCase).build()
                camera = available.bindToLifecycle(owner, selector, basicGroup)
            }
            if (view.previewStreamState.value == PreviewView.StreamState.STREAMING) {
                applyFilm()
                onReady(true, canFlip, analysis != null)
            }
        } catch (_: Exception) {
            unbind()
            onError("The camera could not start. Retry or open the library.")
        }
    }

    fun flip() {
        front = !front
        bind()
    }

    fun focus(x: Float, y: Float) {
        val point = view.meteringPointFactory.createPoint(x, y)
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
    }

    fun setFilm(value: Film) {
        if (film == value) return
        film = value
        applyFilm()
    }

    private fun applyFilm() {
        val texture = findTexture(view) ?: return
        texture.setLayerPaint(Paint().apply {
            if (film != Film.CLEAN) colorFilter = PhotoRenderer.colorFilter(Recipe(film))
        })
    }

    private fun findTexture(view: View): TextureView? {
        if (view is TextureView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTexture(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    fun takePhoto(file: File, completed: (Boolean) -> Unit) {
        val imageCapture = capture
        if (imageCapture == null) {
            completed(false)
            return
        }
        try {
            // The main executor remains available if the screen closes during a capture.
            val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = front }
            val output = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
            imageCapture.takePicture(output, main, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = completed(true)
                override fun onError(exception: ImageCaptureException) = completed(false)
            })
        } catch (_: Exception) {
            completed(false)
        }
    }

    private fun unbind() {
        analysis?.clearAnalyzer()
        listOfNotNull(preview, capture, analysis).takeIf { it.isNotEmpty() }?.let {
            provider?.unbind(*it.toTypedArray())
        }
        preview = null
        analysis = null
        camera = null
        capture = null
    }

    fun close() {
        closed = true
        view.removeOnLayoutChangeListener(layoutListener)
        view.previewStreamState.removeObservers(owner)
        unbind()
        analyzer.shutdown()
    }
}
