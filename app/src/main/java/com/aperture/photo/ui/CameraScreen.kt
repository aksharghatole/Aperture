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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
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
import com.aperture.photo.camera.Composition
import com.aperture.photo.camera.Framing
import com.aperture.photo.camera.Scene
import com.aperture.photo.camera.SceneReading
import kotlinx.coroutines.delay
import com.aperture.photo.camera.ShootingMode
import com.aperture.photo.imaging.Film
import com.aperture.photo.library.Photo
import com.aperture.photo.library.PhotoLibrary
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CameraScreen(library: PhotoLibrary, latest: Photo?, openLibrary: () -> Unit, onCaptured: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    var granted by remember { mutableStateOf(cameraPermission(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var film by rememberSaveable { mutableStateOf(Film.CLEAN) }
    var mode by rememberSaveable { mutableStateOf(ShootingMode.STREET) }
    val reference = rememberCameraReference()
    var reading by remember { mutableStateOf<SceneReading?>(null) }
    val scene = reading?.scene
    var ready by remember { mutableStateOf(false) }
    var canFlip by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var captureMessage by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var session by remember { mutableStateOf<CameraSession?>(null) }
    val roll = if (granted) deviceRoll() else null

    LaunchedEffect(reading) {
        val accepted = reading ?: return@LaunchedEffect
        delay(accepted.remainingMillis(SystemClock.elapsedRealtime()))
        if (reading === accepted) reading = null
    }

    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = cameraPermission(context)
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
                        }, { active, flip, _ ->
                                ready = active
                                canFlip = flip
                                if (!active) reading = null
                            }, { error = it })
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
                        Image(image, null, Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop, alpha = reference.opacity)
                    }
                    GuideOverlay(scene, roll, mode, Modifier.matchParentSize().pointerInput(session) {
                        detectTapGestures { session?.focus(it.x, it.y) }
                    })
                    Column(Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        val hint = captureMessage ?: Composition.guidance(scene, roll, mode, analysisAvailable = false)
                        Text(hint, color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .75f))
                                .padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                } else if (error == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp)
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
                        captureMessage = null
                    }, label = { Text(item.title) })
                }
            }
            FilmPicker(film) { film = it; captureMessage = null }
        }

        Row(Modifier.fillMaxWidth().height(90.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                if (latest != null) {
                    Box(Modifier.size(49.dp).clip(RoundedCornerShape(10.dp)).clickable(
                        role = Role.Button, onClickLabel = "Open library", onClick = openLibrary
                    )) {
                        PhotoThumbnail(latest, Modifier.fillMaxSize(), "Last photo")
                    }
                } else {
                    TextButton(onClick = openLibrary) { Text("Photos", fontSize = 11.sp) }
                }
            }
            val shutterColor = MaterialTheme.colorScheme.primary.copy(alpha = if (ready) 1f else .36f)
            Box(
                Modifier.size(66.dp).border(1.dp, shutterColor, CircleShape).clip(CircleShape)
                    .semantics { contentDescription = "Shutter" }
                    .clickable(enabled = ready && !busy, role = Role.Button, onClickLabel = "Take photo") {
                        val active = session ?: return@clickable
                        val pending = try {
                            library.beginCapture()
                        } catch (_: Exception) {
                            captureMessage = "Could not create a photo file"
                            return@clickable
                        }
                        val selectedFilm = film
                        busy = true
                        captureMessage = "Saving photo…"
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        active.takePhoto(pending) { saved ->
                            busy = false
                            if (!saved) {
                                pending.delete()
                                captureMessage = "Capture failed · try again"
                            } else {
                                val result = runCatching { library.commitCapture(pending, selectedFilm) }
                                captureMessage = if (result.isSuccess) "Photo saved to library" else "Could not save the photo"
                                if (result.isSuccess) onCaptured()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(29.dp), strokeWidth = 2.dp)
                else Box(Modifier.size(53.dp).background(shutterColor, CircleShape))
            }
            Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                if (canFlip) TextButton(enabled = !busy, onClick = {
                    captureMessage = null
                    session?.flip()
                }) { Text("Flip", fontSize = 11.sp) }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val wideLayout = maxWidth > maxHeight * 1.24f
        val controlsWidth = (maxWidth * .43f).coerceAtMost(280.dp)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(if (wideLayout) 48.dp else 60.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Aperture", fontSize = 27.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
                Row {
                    if (granted) TextButton(onClick = reference.open) { Text("Reference") }
                    TextButton(onClick = openLibrary) { Text("Library") }
                }
            }
            if (wideLayout) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    viewfinder(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.width(controlsWidth)) { controls() }
                }
            } else {
                viewfinder(Modifier.weight(1f).fillMaxWidth())
                controls()
            }
        }
    }

    LaunchedEffect(captureMessage) {
        if (!busy && captureMessage != null) {
            kotlinx.coroutines.delay(2900)
            captureMessage = null
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
private fun GuideOverlay(scene: Scene?, roll: Float?, mode: ShootingMode, modifier: Modifier) {
    val framing = mode.framing
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val white = Color.White.copy(alpha = .37f)
        val center = Offset(size.width / 2, size.height / 2)
        if (framing == Framing.THIRDS) {
            for (step in 1..2) {
                val x = size.width * step / 3
                val y = size.height * step / 3
                drawLine(white, Offset(x, 0f), Offset(x, size.height))
                drawLine(white, Offset(0f, y), Offset(size.width, y))
            }
        } else if (framing != Framing.HORIZON) {
            val anchor = Offset(size.width * framing.anchorX, size.height * framing.anchorY)
            drawCircle(white, 34.dp.toPx(), anchor, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        Composition.framingPoint(scene, mode)?.let {
            val point = Offset(it.x * size.width, it.y * size.height)
            drawCircle(Color.Black.copy(alpha = .65f), 8.dp.toPx(), point)
            drawCircle(accent, 5.dp.toPx(), point, style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()))
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
    val owner = LocalLifecycleOwner.current
    var roll by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context, owner) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var filtered: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val values = filtered ?: event.values.copyOf().also { filtered = it }
                for (axis in 0..2) values[axis] += (event.values[axis] - values[axis]) * .18f
                roll = Composition.sensorRoll(values[0], values[1], values[2])
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
