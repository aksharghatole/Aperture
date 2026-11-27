package com.aperture.photo.camera

import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.os.Handler
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
import androidx.camera.core.ZoomState
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.aperture.photo.imaging.Film
import com.aperture.photo.imaging.PhotoRenderer
import com.aperture.photo.imaging.Recipe
import java.io.File
import java.util.concurrent.Executors

internal class CameraSession(
    private val view: PreviewView,
    private val owner: LifecycleOwner,
    private val onScene: (SceneReading?) -> Unit,
    private val onReady: (ready: Boolean, canFlip: Boolean, hasAnalysis: Boolean) -> Unit,
    private val onZoom: (CameraZoom) -> Unit,
    private val onExposure: (ExposureCompensation?) -> Unit,
    private val onFocus: (FocusFeedback?) -> Unit,
    private val onFlash: (CameraFlashState?) -> Unit,
    private val onError: (String) -> Unit
) {
    private val main = ContextCompat.getMainExecutor(view.context)
    private val displays = view.context.getSystemService(DisplayManager::class.java)
    private var boundRotation: Int? = null
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            val display = view.display ?: return
            if (display.displayId == displayId && display.rotation != boundRotation) bind()
        }
    }
    private val analyzer = Executors.newSingleThreadExecutor()
    private val providerFuture = ProcessCameraProvider.getInstance(view.context)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private var resetExposureOnBind = false
    private var exposureRequestGeneration = 0
    private var flashRequestGeneration = 0
    private var flashMode = CameraFlashMode.OFF
    private val focusFeedback = FocusFeedbackTracker()
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
        displays?.registerDisplayListener(displayListener, Handler(view.context.mainLooper))
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
        val display = view.display ?: return
        val viewport = view.viewPort ?: return
        if (closed) return
        val rotation = display.rotation
        boundRotation = rotation
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
            val previewUseCase = Preview.Builder().setTargetRotation(rotation).build()
            previewUseCase.setSurfaceProvider(view.surfaceProvider)
            val captureUseCase = ImageCapture.Builder().setTargetRotation(rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            val analysisUseCase = ImageAnalysis.Builder().setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            val sceneAnalyzer = FrameAnalyzer(front, { shootingMode }) { mode, scene ->
                main.execute {
                    if (!closed && analysis === analysisUseCase && mode == shootingMode &&
                        view.display?.rotation == rotation) onScene(scene)
                }
            }
            val group = UseCaseGroup.Builder().setViewPort(viewport)
                .addUseCase(previewUseCase).addUseCase(captureUseCase)
            preview = previewUseCase
            capture = captureUseCase
            canFlip = hasBack && hasFront
            try {
                camera = available.bindToLifecycle(owner, selector, group.addUseCase(analysisUseCase).build())
                observeZoom()
                reportExposure(camera)
                reportFlash(camera)
                analysis = analysisUseCase
                analysisUseCase.setAnalyzer(analyzer, sceneAnalyzer)
            } catch (_: IllegalArgumentException) {
                // Some camera devices only support the preview and still-capture combination.
                available.unbind(previewUseCase, captureUseCase, analysisUseCase)
                analysis = null
                val basicGroup = UseCaseGroup.Builder().setViewPort(viewport)
                    .addUseCase(previewUseCase).addUseCase(captureUseCase).build()
                camera = available.bindToLifecycle(owner, selector, basicGroup)
                observeZoom()
                reportExposure(camera)
                reportFlash(camera)
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
        resetExposureOnBind = true
        bind()
    }

    private fun reportExposure(current: Camera?) {
        val state = current?.cameraInfo?.exposureState
        if (state == null) {
            onExposure(null)
            return
        }
        val range = state.exposureCompensationRange
        onExposure(ExposureCompensation(state.exposureCompensationIndex, range.lower, range.upper,
            state.exposureCompensationStep.toFloat()))
        if (resetExposureOnBind) {
            resetExposureOnBind = false
            setExposureCompensationIndex(0) { }
        }
    }

    fun setExposureCompensationIndex(index: Int, completed: (Boolean) -> Unit) {
        val current = camera
        val state = current?.cameraInfo?.exposureState
        if (current == null || state == null) {
            completed(false)
            return
        }
        val range = state.exposureCompensationRange
        val target = index.coerceIn(range.lower, range.upper)
        val generation = ++exposureRequestGeneration
        val future = current.cameraControl.setExposureCompensationIndex(target)
        future.addListener({
            val success = runCatching { future.get() == target }.getOrDefault(false)
            if (generation == exposureRequestGeneration && camera === current) reportExposure(current)
            completed(success)
        }, main)
    }

    private fun observeZoom() {
        val current = camera ?: return
        val observer = Observer<ZoomState> { state ->
            onZoom(CameraZoom(state.zoomRatio, state.minZoomRatio, state.maxZoomRatio))
        }
        zoomObserver = observer
        current.cameraInfo.zoomState.observe(owner, observer)
    }

    fun zoomBy(scale: Float) {
        val current = camera ?: return
        val state = current.cameraInfo.zoomState.value ?: return
        current.cameraControl.setZoomRatio(
            CameraZoom(state.zoomRatio, state.minZoomRatio, state.maxZoomRatio).clamp(state.zoomRatio * scale)
        )
    }

    fun setZoomRatio(ratio: Float) {
        val current = camera ?: return
        val state = current.cameraInfo.zoomState.value ?: return
        current.cameraControl.setZoomRatio(CameraZoom(state.zoomRatio, state.minZoomRatio, state.maxZoomRatio).clamp(ratio))
    }

    fun focus(x: Float, y: Float) {
        if (closed) return
        val current = camera
        if (current == null || view.width <= 0 || view.height <= 0) {
            focusFeedback.clear()
            onFocus(null)
            return
        }
        val targetX = x.coerceIn(0f, 1f)
        val targetY = y.coerceIn(0f, 1f)
        val point = view.meteringPointFactory.createPoint(targetX * view.width, targetY * view.height)
        val afAction = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
        val aeAction = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE).build()
        val autofocusSupported = runCatching {
            current.cameraInfo.isFocusMeteringSupported(afAction)
        }.getOrDefault(false)
        val exposureMeteringSupported = runCatching {
            current.cameraInfo.isFocusMeteringSupported(aeAction)
        }.getOrDefault(false)
        val requested = focusFeedback.request(targetX, targetY, autofocusSupported, exposureMeteringSupported)
        onFocus(requested)
        if (!autofocusSupported && !exposureMeteringSupported) return
        try {
            val action = if (autofocusSupported) afAction else aeAction
            val future = current.cameraControl.startFocusAndMetering(action)
            future.addListener({
                val result = runCatching {
                    val meteringResult = future.get()
                    if (autofocusSupported) meteringResult.isFocusSuccessful else true
                }.getOrDefault(false)
                focusFeedback.complete(requested.requestId, result)?.let(onFocus)
            }, main)
        } catch (_: Exception) {
            focusFeedback.complete(requested.requestId, false)?.let(onFocus)
        }
    }

    private fun reportFlash(current: Camera?) {
        if (current?.cameraInfo?.hasFlashUnit() == true) onFlash(CameraFlashState(flashMode))
        else {
            flashMode = CameraFlashMode.OFF
            onFlash(null)
        }
    }

    fun setFlashMode(target: CameraFlashMode) {
        val current = camera
        val imageCapture = capture
        if (closed || current == null || imageCapture == null || !current.cameraInfo.hasFlashUnit()) {
            onFlash(null)
            return
        }
        val generation = ++flashRequestGeneration
        val future = try {
            current.cameraControl.enableTorch(target == CameraFlashMode.TORCH)
        } catch (_: Exception) {
            onFlash(CameraFlashState(flashMode, error = "Flash control failed. Try another mode."))
            return
        }
        onFlash(CameraFlashState(flashMode, updating = true))
        future.addListener({
            val succeeded = runCatching { future.get(); true }.getOrDefault(false)
            if (generation != flashRequestGeneration || camera !== current) return@addListener
            if (!succeeded) {
                onFlash(CameraFlashState(flashMode, error = "Flash control failed. Try another mode."))
                return@addListener
            }
            imageCapture.flashMode = when (target) {
                CameraFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                CameraFlashMode.ON -> ImageCapture.FLASH_MODE_ON
                CameraFlashMode.OFF, CameraFlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
            }
            flashMode = target
            onFlash(CameraFlashState(target))
        }, main)
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
        flashRequestGeneration++
        camera?.cameraControl?.let { control -> runCatching { control.enableTorch(false) } }
        flashMode = CameraFlashMode.OFF
        onFlash(null)
        focusFeedback.clear()
        onFocus(null)
        exposureRequestGeneration++
        camera?.cameraInfo?.zoomState?.let { state -> zoomObserver?.let(state::removeObserver) }
        zoomObserver = null
        onExposure(null)
        analysis?.clearAnalyzer()
        listOfNotNull(preview, capture, analysis).takeIf { it.isNotEmpty() }?.let {
            provider?.unbind(*it.toTypedArray())
        }
        preview = null
        analysis = null
        camera = null
        capture = null
        onZoom(CameraZoom())
    }

    fun close() {
        closed = true
        displays?.unregisterDisplayListener(displayListener)
        view.removeOnLayoutChangeListener(layoutListener)
        view.previewStreamState.removeObservers(owner)
        unbind()
        analyzer.shutdown()
    }
}
