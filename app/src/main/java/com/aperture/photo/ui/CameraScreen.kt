package com.aperture.photo.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aperture.photo.camera.CameraSession
import com.aperture.photo.camera.CameraZoom
import com.aperture.photo.camera.CameraFlashMode
import com.aperture.photo.camera.CameraFlashState
import com.aperture.photo.camera.ExposureCompensation
import com.aperture.photo.camera.FocusFeedback
import com.aperture.photo.camera.ReferenceAlignment
import com.aperture.photo.camera.SelfTimer
import com.aperture.photo.camera.SelfTimerCountdown
import com.aperture.photo.camera.SelfTimerStep
import com.aperture.photo.camera.Composition
import com.aperture.photo.camera.CompositionAnchor
import com.aperture.photo.camera.CompositionGuide
import com.aperture.photo.camera.CompositionGuideGeometry
import com.aperture.photo.camera.Framing
import com.aperture.photo.camera.ManualSubject
import com.aperture.photo.camera.Scene
import com.aperture.photo.camera.SceneReading
import kotlinx.coroutines.delay
import com.aperture.photo.camera.ShootingMode
import com.aperture.photo.camera.VolumeShutterPolicy
import com.aperture.photo.imaging.Film
import com.aperture.photo.library.Photo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class PendingShutter(
    val countdown: SelfTimerCountdown,
    val film: Film,
    val session: CameraSession
)

@Composable
internal fun CameraScreen(
    operations: PhotoOperations,
    reference: CameraReferenceState,
    latest: Photo?,
    onVolumeShutterChanged: (((() -> Boolean)?) -> Unit),
    openLibrary: () -> Unit
) {
    val library = operations.library
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    var granted by remember { mutableStateOf(cameraPermission(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var film by rememberSaveable { mutableStateOf(Film.CLEAN) }
    val cameraPreferences = remember(context) { context.getSharedPreferences("camera", Context.MODE_PRIVATE) }
    var guide by remember {
        mutableStateOf(runCatching {
            CompositionGuide.valueOf(cameraPreferences.getString("composition_guide", CompositionGuide.AUTO.name)!!)
        }.getOrDefault(CompositionGuide.AUTO))
    }
    var volumeShutterEnabled by remember {
        mutableStateOf(cameraPreferences.getBoolean("volume_shutter_enabled", false))
    }
    var timerSeconds by remember {
        mutableIntStateOf(cameraPreferences.getInt("shutter_timer_seconds", 0).takeIf { it == 3 || it == 10 } ?: 0)
    }
    var timerHaptics by remember { mutableStateOf(cameraPreferences.getBoolean("timer_haptics_enabled", false)) }
    var voiceCues by remember { mutableStateOf(cameraPreferences.getBoolean("composition_voice", false)) }
    var hapticCues by remember { mutableStateOf(cameraPreferences.getBoolean("composition_haptics", false)) }
    var cueSettingsOpen by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ShootingMode.STREET) }
    var anchor by rememberSaveable { mutableStateOf(CompositionAnchor.AUTO) }
    var markingSubject by remember { mutableStateOf(false) }
    var manualSubject by remember { mutableStateOf<ManualSubject?>(null) }
    var referenceDialogOpen by rememberSaveable { mutableStateOf(false) }
    var aligningReference by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<SceneReading?>(null) }
    val scene = reading?.scene
    var ready by remember { mutableStateOf(false) }
    var canFlip by remember { mutableStateOf(false) }
    val busy = operations.captureBusy
    var error by remember { mutableStateOf<String?>(null) }
    val captureMessage = operations.captureMessage
    var retry by remember { mutableIntStateOf(0) }
    var session by remember { mutableStateOf<CameraSession?>(null) }
    var zoom by remember { mutableStateOf(CameraZoom()) }
    var exposure by remember { mutableStateOf<ExposureCompensation?>(null) }
    var exposureError by remember { mutableStateOf(false) }
    var exposureRequestId by remember { mutableIntStateOf(0) }
    var focusFeedback by remember { mutableStateOf<FocusFeedback?>(null) }
    var flashState by remember { mutableStateOf<CameraFlashState?>(null) }
    var pendingShutter by remember { mutableStateOf<PendingShutter?>(null) }
    var timerRequestId by remember { mutableIntStateOf(0) }
    var analysisAvailable by remember { mutableStateOf(false) }
    var diagnosticsEnabled by remember {
        mutableStateOf(cameraPreferences.getBoolean("exposure_diagnostics_enabled", false))
    }
    val roll = if (granted) deviceRoll() else null
    val advice = Composition.advice(scene, roll, mode, analysisAvailable, anchor, manualSubject != null)
    val cueAvailability = CameraCueFeedback(
        voiceOn = voiceCues,
        hapticsOn = hapticCues,
        allowed = granted && ready && analysisAvailable && !busy && pendingShutter == null &&
            captureMessage == null && !markingSubject && manualSubject == null && !aligningReference &&
            !referenceDialogOpen && !cueSettingsOpen,
        mode = mode,
        anchor = anchor,
        reading = reading,
        cue = advice.cue
    )

    fun requestShutter() {
        if (pendingShutter != null) {
            pendingShutter = null
            return
        }
        if (!ready || !granted || busy) return
        val active = session ?: return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (timerSeconds == 0) operations.capture(film, active::takePhoto)
        else pendingShutter = PendingShutter(SelfTimer.start(++timerRequestId, timerSeconds), film, active)
    }

    BackHandler(enabled = busy || pendingShutter != null || aligningReference || markingSubject) {
        if (pendingShutter != null) pendingShutter = null
        else if (aligningReference) aligningReference = false
        else if (markingSubject) markingSubject = false
    }

    val dispatchVolumeShutter by rememberUpdatedState {
        if (!VolumeShutterPolicy.shouldCapture(volumeShutterEnabled, ready, granted, busy, session != null,
                if (pendingShutter == null) 0 else 1)) false
        else {
            requestShutter()
            true
        }
    }
    DisposableEffect(onVolumeShutterChanged) {
        onVolumeShutterChanged { dispatchVolumeShutter() }
        onDispose { onVolumeShutterChanged(null) }
    }

    LaunchedEffect(reading) {
        val accepted = reading ?: return@LaunchedEffect
        delay(accepted.remainingMillis(SystemClock.elapsedRealtime()))
        if (reading === accepted) reading = null
    }

    LaunchedEffect(focusFeedback) {
        val accepted = focusFeedback ?: return@LaunchedEffect
        delay(1_800)
        if (focusFeedback == accepted) focusFeedback = null
    }

    LaunchedEffect(pendingShutter?.countdown?.id) {
        val request = pendingShutter ?: return@LaunchedEffect
        var countdown = request.countdown
        while (true) {
            delay(1_000)
            val active = pendingShutter?.takeIf { it.countdown.id == request.countdown.id }
                ?: return@LaunchedEffect
            when (val step = SelfTimer.advance(countdown)) {
                is SelfTimerStep.Continue -> {
                    countdown = step.countdown
                    pendingShutter = active.copy(countdown = countdown)
                    if (timerHaptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                SelfTimerStep.Capture -> {
                    pendingShutter = null
                    if (ready && granted && session === active.session && !operations.captureBusy &&
                        owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        operations.capture(active.film, active.session::takePhoto)
                    }
                    return@LaunchedEffect
                }
            }
        }
    }

    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = cameraPermission(context)
            else if (event == Lifecycle.Event.ON_PAUSE) {
                pendingShutter = null
                markingSubject = false
                manualSubject = null
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val viewfinder: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xff080b09))) {
            if (granted) {
                key(retry) {
                    val preview = remember(context) {
                        PreviewView(context).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    }
                    AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
                    DisposableEffect(preview, owner) {
                        val connection = CameraSession(preview, owner, { next ->
                            reading = if (ready) SceneReading.accept(next, SystemClock.elapsedRealtime()) else null
                        }, { active, flip, hasAnalysis ->
                                ready = active
                                canFlip = flip
                                analysisAvailable = hasAnalysis
                                if (!active) {
                                    reading = null
                                    pendingShutter = null
                                    markingSubject = false
                                    manualSubject = null
                                }
                        }, {
                            if (zoom.ratio != it.ratio) manualSubject = null
                            zoom = it
                        }, { exposure = it }, { focusFeedback = it }, { flashState = it }, { error = it })
                        session = connection
                        onDispose {
                            connection.close()
                            reading = null
                            session = null
                        }
                    }
                    SideEffect {
                        session?.let { connection ->
                            connection.setFilm(film)
                            connection.shootingMode = mode
                        }
                    }
                }

                if (ready) {
                    if (film.vignette > 0f) {
                        Canvas(Modifier.matchParentSize()) {
                            scaleToVignette(film.vignette)
                        }
                    }
                    reference.image?.let { image ->
                        CameraReferenceOverlay(image, reference.alignment, reference.opacity, Modifier.matchParentSize())
                    }
                    val viewfinderGestures = if (aligningReference) {
                        Modifier.pointerInput(reference) {
                            detectTransformGestures { center, pan, zoomChange, _ ->
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                if (width > 0 && height > 0) {
                                    reference.alignment = reference.alignment.transform(
                                        center.x / width - .5f, center.y / height - .5f,
                                        pan.x / width, pan.y / height, zoomChange
                                    )
                                }
                            }
                        }
                    } else if (markingSubject) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { tap ->
                                ManualSubject.fromTap(tap.x, tap.y, size.width, size.height)?.let {
                                    manualSubject = it
                                    markingSubject = false
                                }
                            }
                        }
                    } else {
                        Modifier.pointerInput(session) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                if (zoomChange != 1f) session?.zoomBy(zoomChange)
                            }
                        }.pointerInput(session) {
                            detectTapGestures { tap -> session?.focus(tap.x / size.width, tap.y / size.height) }
                        }
                    }
                    GuideOverlay(scene, roll, mode, guide, anchor, manualSubject, markingSubject,
                        Modifier.matchParentSize().then(viewfinderGestures))
                    focusFeedback?.let { TapFocusOverlay(it, Modifier.matchParentSize()) }
                    if (diagnosticsEnabled && analysisAvailable) {
                        ExposureDiagnosticsOverlay(scene?.exposureDiagnostics,
                            Modifier.align(Alignment.TopEnd).padding(10.dp))
                    }
                    pendingShutter?.let { pending ->
                        Column(Modifier.align(Alignment.Center)
                            .clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = .76f))
                            .padding(horizontal = 28.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${pending.countdown.secondsRemaining}", color = Color.White,
                                fontSize = 40.sp, fontWeight = FontWeight.Light)
                            Text("Tap shutter or Back to cancel", color = Color.White, fontSize = 11.sp)
                        }
                    }
                    if (aligningReference) {
                        Row(Modifier.align(Alignment.BottomCenter).padding(8.dp)
                            .background(Color(0xf21b201d), RoundedCornerShape(16.dp)).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Drag or pinch", fontSize = 12.sp)
                            TextButton(onClick = { reference.alignment = ReferenceAlignment.Centered }) { Text("Reset") }
                            TextButton(onClick = { aligningReference = false }) { Text("Done") }
                        }
                    } else Column(Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        val hint = if (markingSubject) "Tap the subject to mark it · Back to cancel"
                        else captureMessage ?: advice.text
                        Text(hint, color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .75f))
                                .padding(horizontal = 14.dp, vertical = 8.dp).then(
                                    if (captureMessage != null && !markingSubject) Modifier.semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    } else Modifier
                                ))
                    }
                } else if (error == null) {
                    if (busy) Text("Finishing capture…", Modifier.align(Alignment.Center))
                    else CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp)
                }
                error?.let { message ->
                    CameraPrompt(message, "Retry", Modifier.align(Alignment.Center)) {
                        error = null
                        retry++
                    }
                }
            } else {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CameraPrompt("Allow camera access to use the viewfinder.", "Enable camera") {
                        permission.launch(Manifest.permission.CAMERA)
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)))
                    }) { Text("App settings") }
                }
            }
        }
    }

    val controls: @Composable () -> Unit = {
        if (granted) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShootingMode.entries.forEach { item ->
                    FilterChip(selected = item == mode, onClick = {
                        if (mode != item) reading = null
                        mode = item
                        session?.shootingMode = item
                        operations.clearCaptureMessage()
                    }, label = { Text(item.title) })
                }
            }
            FilmPicker(film) { film = it; operations.clearCaptureMessage() }
            flashState?.let { state ->
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.mode == CameraFlashMode.TORCH) "Torch" else "Still flash",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    CameraFlashMode.choices(supported = true).forEach { item ->
                        FilterChip(selected = state.mode == item,
                            enabled = ready && !busy && pendingShutter == null && !state.updating,
                            onClick = { session?.setFlashMode(item) }, label = { Text(item.title) })
                    }
                    if (state.updating) Text("Changing…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.sp) }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Timer", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                listOf(0, 3, 10).forEach { seconds ->
                    FilterChip(selected = timerSeconds == seconds, enabled = pendingShutter == null && !busy,
                        onClick = {
                            timerSeconds = seconds
                            cameraPreferences.edit().putInt("shutter_timer_seconds", seconds).apply()
                        }, label = { Text(if (seconds == 0) "Off" else "${seconds}s") })
                }
                Text("Haptics", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Switch(checked = timerHaptics, enabled = pendingShutter == null && !busy, onCheckedChange = {
                    timerHaptics = it
                    cameraPreferences.edit().putBoolean("timer_haptics_enabled", it).apply()
                }, modifier = Modifier.semantics { contentDescription = "Haptic countdown ticks" })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exposure estimate", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Sampled preview luma · not RAW clipping", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp)
                }
                Switch(checked = diagnosticsEnabled, enabled = analysisAvailable && ready && !busy, onCheckedChange = {
                    diagnosticsEnabled = it
                    cameraPreferences.edit().putBoolean("exposure_diagnostics_enabled", it).apply()
                }, modifier = Modifier.semantics { contentDescription = "Show exposure estimate histogram" })
            }
            if (!analysisAvailable && ready) {
                Text("Preview analysis unavailable on this camera", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CameraFramingMenu(anchor, manualSubject != null, !busy && pendingShutter == null,
                    canMark = ready, selectAnchor = { anchor = it }, mark = {
                        aligningReference = false
                        markingSubject = true
                    }, clear = {
                        markingSubject = false
                        manualSubject = null
                    })
                TextButton(enabled = !busy && pendingShutter == null, onClick = { cueSettingsOpen = true },
                    modifier = Modifier.semantics { contentDescription = "Composition cues settings" }) {
                    Text("Cues")
                }
                Text("Guide", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                CompositionGuide.entries.forEach { item ->
                    FilterChip(selected = guide == item, onClick = {
                        guide = item
                        cameraPreferences.edit().putString("composition_guide", item.name).apply()
                    }, label = { Text(item.title) })
                }
            }
            if (guide != CompositionGuide.AUTO && guide != CompositionGuide.OFF) {
                val targetLabel = if (anchor == CompositionAnchor.AUTO) mode.title else anchor.title
                Text("Grid only · target uses $targetLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            } else if (guide == CompositionGuide.OFF) {
                Text("Grid off · assistant framing cues remain", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Zoom ${"%.1f".format(zoom.ratio)}×", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp)
                zoom.presets().forEach { preset ->
                    TextButton(enabled = ready && !busy, onClick = { session?.setZoomRatio(preset) },
                        modifier = Modifier.semantics { contentDescription = "Zoom to ${preset.toInt()} times" }) {
                        Text("${preset.toInt()}×")
                    }
                }
            }
            exposure?.takeIf { it.supported }?.let { current ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Camera exposure ${if (current.ev >= 0f) "+" else ""}${"%.1f".format(current.ev)} EV",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = current.index != 0 && !busy, onClick = {
                        exposureError = false
                        val requestId = ++exposureRequestId
                        session?.setExposureCompensationIndex(0) { success ->
                            if (requestId == exposureRequestId) exposureError = !success
                        }
                    }) { Text("Reset") }
                }
                Slider(
                    value = current.index.toFloat(),
                    onValueChange = { value ->
                        val target = current.clamp(value.toInt())
                        exposure = current.copy(index = target)
                        exposureError = false
                        val requestId = ++exposureRequestId
                        session?.setExposureCompensationIndex(target) { success ->
                            if (requestId == exposureRequestId) exposureError = !success
                        }
                    },
                    valueRange = current.minIndex.toFloat()..current.maxIndex.toFloat(),
                    steps = (current.maxIndex - current.minIndex - 1).coerceAtLeast(0),
                    enabled = !busy,
                    modifier = Modifier.width(120.dp).semantics {
                        contentDescription = "Camera exposure compensation, ${"%.1f".format(current.ev)} EV"
                    }
                )
            }
            if (exposureError) {
                Text("Camera exposure update failed.", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
            TextButton(enabled = ready && !busy && pendingShutter == null && !aligningReference && !markingSubject,
                onClick = { session?.focus(.5f, .5f) }) { Text("Focus center") }
            TextButton(modifier = Modifier.semantics {
                stateDescription = if (volumeShutterEnabled) "On" else "Off"
            }, onClick = {
                volumeShutterEnabled = !volumeShutterEnabled
                cameraPreferences.edit().putBoolean("volume_shutter_enabled", volumeShutterEnabled).apply()
            }) {
                Text(if (volumeShutterEnabled) "Volume shutter on" else "Volume shutter off", fontSize = 12.sp)
            }
        }

    }

    val shutterControls: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                if (latest != null) {
                    Box(Modifier.size(49.dp).clip(RoundedCornerShape(10.dp)).clickable(
                        enabled = !busy, role = Role.Button, onClickLabel = "Open library", onClick = openLibrary
                    )) {
                        PhotoThumbnail(latest, library, Modifier.fillMaxSize(), "Last photo")
                    }
                } else {
                    TextButton(enabled = !busy, onClick = openLibrary) { Text("Photos", fontSize = 11.sp) }
                }
            }
            val shutterColor = MaterialTheme.colorScheme.primary.copy(alpha = if (ready) 1f else .36f)
            Box(
                Modifier.size(66.dp).border(1.dp, shutterColor, CircleShape).clip(CircleShape)
                    .semantics { contentDescription = if (pendingShutter == null) "Shutter" else "Cancel timer" }
                    .clickable(enabled = ready && !busy, role = Role.Button,
                        onClickLabel = if (pendingShutter == null) "Take photo" else "Cancel timer") {
                        requestShutter()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(29.dp), strokeWidth = 2.dp)
                else if (pendingShutter != null) Text("${pendingShutter!!.countdown.secondsRemaining}",
                    color = Color.White, fontWeight = FontWeight.SemiBold)
                else Box(Modifier.size(53.dp).background(shutterColor, CircleShape))
            }
            Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                if (canFlip) TextButton(enabled = !busy && pendingShutter == null, onClick = {
                    operations.clearCaptureMessage()
                    session?.flip()
                }) { Text("Flip", fontSize = 11.sp) }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val wideLayout = maxWidth > maxHeight * 1.24f
        val controlsWidth = (maxWidth * .43f).coerceAtMost(280.dp)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Aperture", fontSize = 27.sp, fontWeight = FontWeight.Medium,
                    letterSpacing = (-1).sp, modifier = Modifier.weight(1f))
                Row(Modifier.weight(1.5f, fill = false).horizontalScroll(rememberScrollState())) {
                    if (granted) TextButton(enabled = !busy, onClick = {
                        aligningReference = false
                        markingSubject = false
                        referenceDialogOpen = true
                    }) {
                        Text("Reference")
                    }
                    TextButton(enabled = !busy, onClick = openLibrary) { Text("Library") }
                }
            }
            if (wideLayout) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    viewfinder(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(controlsWidth).fillMaxHeight()) {
                        if (granted) Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controls() }
                        else Spacer(Modifier.weight(1f))
                        shutterControls()
                    }
                }
            } else {
                viewfinder(Modifier.weight(1f).fillMaxWidth())
                if (granted) Column(Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(rememberScrollState())) { controls() }
                shutterControls()
            }
        }
    }

    if (granted && referenceDialogOpen) {
        CameraReferenceDialog(reference, ready, dismiss = { referenceDialogOpen = false }) {
            referenceDialogOpen = false
            aligningReference = true
        }
    }

    if (granted && cueSettingsOpen) CameraCueSettings(
        voiceOn = voiceCues,
        setVoice = {
            voiceCues = it
            cameraPreferences.edit().putBoolean("composition_voice", it).apply()
        },
        hapticsOn = hapticCues,
        setHaptics = {
            hapticCues = it
            cameraPreferences.edit().putBoolean("composition_haptics", it).apply()
        },
        availability = cueAvailability,
        dismiss = { cueSettingsOpen = false }
    )

    LaunchedEffect(captureMessage) {
        if (!busy && captureMessage != null) {
            kotlinx.coroutines.delay(2900)
            operations.clearCaptureMessage()
        }
    }
}

private fun cameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CameraPrompt(message: String, action: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = Color.White)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onClick) { Text(action) }
    }
}

@Composable
fun FilmPicker(film: Film, onChange: (Film) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Film.entries.forEach { option ->
            FilterChip(selected = film == option, onClick = { onChange(option) }, label = {
                Text(option.title, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            })
        }
    }
}

@Composable
private fun GuideOverlay(
    scene: Scene?, roll: Float?, mode: ShootingMode, guide: CompositionGuide,
    anchor: CompositionAnchor, manualSubject: ManualSubject?, markingSubject: Boolean, modifier: Modifier
) {
    val framing = mode.framing
    val accent = MaterialTheme.colorScheme.primary
    val manual = markingSubject || manualSubject != null
    Canvas(modifier) {
        val white = Color.White.copy(alpha = .37f)
        val center = Offset(size.width / 2, size.height / 2)
        CompositionGuideGeometry.lines(guide, mode).forEach { line ->
            drawLine(white, Offset(line.startX * size.width, line.startY * size.height),
                Offset(line.endX * size.width, line.endY * size.height))
        }
        if (anchor != CompositionAnchor.AUTO || manual) {
            val target = anchor.target(framing)
            val point = Offset(target.x * size.width, target.y * size.height)
            drawCircle(Color.Black.copy(alpha = .65f), 13.dp.toPx(), point,
                style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            drawCircle(accent, 13.dp.toPx(), point, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        } else if (guide != CompositionGuide.OFF && framing != Framing.THIRDS && framing != Framing.HORIZON) {
            val anchor = Offset(size.width * framing.anchorX, size.height * framing.anchorY)
            drawCircle(white, 34.dp.toPx(), anchor, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        Composition.framingPoint(scene, mode, anchor, manualSubject = manual)?.let {
            val point = Offset(it.x * size.width, it.y * size.height)
            drawCircle(Color.Black.copy(alpha = .65f), 8.dp.toPx(), point)
            drawCircle(accent, 5.dp.toPx(), point, style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()))
        }
        if (!manual && mode == ShootingMode.PORTRAIT) scene?.portraitGroup?.let { group ->
            val topLeft = Offset(group.left * size.width, group.top * size.height)
            val region = Size((group.right - group.left) * size.width, (group.bottom - group.top) * size.height)
            drawRect(Color.Black.copy(alpha = .65f), topLeft, region,
                style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            drawRect(accent, topLeft, region, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        manualSubject?.let {
            val half = ManualSubject.HalfExtent
            val corner = Offset((it.center.x - half) * size.width, (it.center.y - half) * size.height)
            val extent = Size(2 * half * size.width, 2 * half * size.height)
            drawRect(Color.Black.copy(alpha = .65f), corner, extent,
                style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
            drawRect(Color.White, corner, extent, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
        }
        if (framing == Framing.HORIZON) drawLine(white, Offset(0f, center.y), Offset(size.width, center.y))
        if (roll != null) {
            val angle = Math.toRadians(roll.toDouble())
            val radius = 28.dp.toPx()
            val delta = Offset(radius * cos(angle).toFloat(), radius * sin(angle).toFloat())
            val origin = if (framing == Framing.HORIZON) center else center.copy(y = size.height * .08f)
            drawLine(Color.Black.copy(alpha = .6f), origin - delta, origin + delta, 4.dp.toPx())
            drawLine(if (abs(roll) < mode.rollTolerance) accent else Color.White, origin - delta, origin + delta, 2.dp.toPx())
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.scaleToVignette(strength: Float) {
    withTransform({ scale(size.width, size.height, pivot = Offset.Zero) }) {
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, .43f to Color.Transparent,
                1f to Color.Black.copy(alpha = strength * .86f),
                center = Offset(.5f, .5f), radius = .707f
            ),
            size = Size(1f, 1f)
        )
    }
}

@Composable
private fun deviceRoll(): Float? {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    var roll by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context, owner, view) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var filtered: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val values = filtered ?: event.values.copyOf().also { filtered = it }
                for (axis in 0..2) values[axis] += (event.values[axis] - values[axis]) * .18f
                roll = view.display?.rotation?.let { rotation ->
                    Composition.sensorRoll(values[0], values[1], values[2], rotation)
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (sensor != null) {
                    filtered = null
                    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                }
                Lifecycle.Event.ON_PAUSE -> { manager.unregisterListener(listener); roll = null }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            manager.unregisterListener(listener)
        }
    }
    return roll
}
