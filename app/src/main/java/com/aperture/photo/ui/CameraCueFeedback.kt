package com.aperture.photo.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aperture.photo.camera.CompositionAnchor
import com.aperture.photo.camera.CompositionCue
import com.aperture.photo.camera.CompositionCueGate
import com.aperture.photo.camera.CueKind
import com.aperture.photo.camera.SceneReading
import com.aperture.photo.camera.ShootingMode
import java.util.Locale
import kotlinx.coroutines.delay

internal enum class VoiceCueStatus { OFF, CONNECTING, READY, UNAVAILABLE }

internal data class CueAvailability(val voice: VoiceCueStatus, val haptics: Boolean)

@Composable
internal fun CameraCueFeedback(
    voiceOn: Boolean,
    hapticsOn: Boolean,
    allowed: Boolean,
    mode: ShootingMode,
    anchor: CompositionAnchor,
    reading: SceneReading?,
    cue: CompositionCue?
): CueAvailability {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val gate = remember { CompositionCueGate() }
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState == Lifecycle.State.RESUMED) }
    var speaker by remember { mutableStateOf<OfflineCueSpeech?>(null) }
    @Suppress("DEPRECATION")
    val hasVibrator = remember(context) { context.getSystemService(Vibrator::class.java)?.hasVibrator() == true }

    DisposableEffect(context, voiceOn) {
        val active = if (voiceOn) runCatching { OfflineCueSpeech(context) }.getOrNull() else null
        speaker = active
        onDispose { active?.close() }
    }
    val activeSpeaker = speaker
    DisposableEffect(lifecycle, mode, anchor, activeSpeaker) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    activeSpeaker?.stop()
                    gate.clearEvidence()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            activeSpeaker?.stop()
            gate.clearEvidence()
        }
    }

    val voiceStatus = if (!voiceOn) VoiceCueStatus.OFF else activeSpeaker?.status ?: VoiceCueStatus.UNAVAILABLE
    val useVoice = voiceStatus == VoiceCueStatus.READY
    val useHaptics = hapticsOn && hasVibrator
    LaunchedEffect(allowed, resumed, reading, cue, mode, anchor, useVoice, useHaptics, activeSpeaker) {
        val now = SystemClock.elapsedRealtime()
        val fresh = SceneReading.accept(reading, now)
        if (!allowed || !resumed || lifecycle.currentState != Lifecycle.State.RESUMED ||
            fresh == null || cue == null || (!useVoice && !useHaptics)) {
            activeSpeaker?.stop()
            gate.clearEvidence()
            return@LaunchedEffect
        }
        activeSpeaker?.keepOnly(cue)
        val emitted = gate.observe(cue, fresh.sampledAtMillis, now) ?: return@LaunchedEffect
        if (useVoice) activeSpeaker?.speak(emitted)
        if (useHaptics) {
            val effect = if (emitted.kind == CueKind.LIGHT) HapticFeedbackConstants.CONTEXT_CLICK
            else HapticFeedbackConstants.CLOCK_TICK
            val played = view.performHapticFeedback(effect)
            if (played && emitted.kind == CueKind.LEVEL) {
                delay(90)
                if (lifecycle.currentState == Lifecycle.State.RESUMED &&
                    SceneReading.accept(fresh, SystemClock.elapsedRealtime()) != null) {
                    view.performHapticFeedback(effect)
                }
            }
        }
    }
    return CueAvailability(voiceStatus, hasVibrator)
}

private class OfflineCueSpeech(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext) { result ->
        main.post { configure(result) }
    }
    private var speaking: CompositionCue? = null
    var status by mutableStateOf(VoiceCueStatus.CONNECTING)
        private set

    private fun configure(result: Int) {
        val current = engine ?: return
        val supported = result == TextToSpeech.SUCCESS && runCatching {
            val region = Locale.getDefault().country
            val voice = current.voices.orEmpty().filter {
                it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
            }.maxWithOrNull(compareBy({ it.locale.country == region }, { it.quality }))
            voice != null && current.setVoice(voice) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        status = if (supported) VoiceCueStatus.READY else VoiceCueStatus.UNAVAILABLE
    }

    fun speak(cue: CompositionCue) {
        val current = engine ?: return
        val result = runCatching {
            current.speak(cue.words.replace(" · ", ". "), TextToSpeech.QUEUE_FLUSH, null, "composition-${cue.name}")
        }.getOrDefault(TextToSpeech.ERROR)
        if (result == TextToSpeech.SUCCESS) speaking = cue
        else {
            stop()
            status = VoiceCueStatus.UNAVAILABLE
        }
    }

    fun keepOnly(cue: CompositionCue) {
        if (speaking != cue) stop()
    }

    fun stop() {
        if (speaking != null) runCatching { engine?.stop() }
        speaking = null
    }

    fun close() {
        stop()
        runCatching { engine?.shutdown() }
        engine = null
    }
}

@Composable
internal fun CameraCueSettings(
    voiceOn: Boolean, setVoice: (Boolean) -> Unit, hapticsOn: Boolean, setHaptics: (Boolean) -> Unit,
    availability: CueAvailability, dismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Composition cues") },
        text = {
            Column {
                CueToggle("Spoken cues", voiceOn, setVoice)
                val voiceMessage = when (availability.voice) {
                    VoiceCueStatus.CONNECTING -> "Checking installed voices…"
                    VoiceCueStatus.UNAVAILABLE -> "An installed offline English voice is required."
                    else -> "Uses installed offline English voices."
                }
                Text(voiceMessage, style = MaterialTheme.typography.bodySmall)
                CueToggle("Haptic cues", hapticsOn, setHaptics, enabled = availability.haptics)
                if (!availability.haptics) Text("This device has no vibration motor.", style = MaterialTheme.typography.bodySmall)
                else Text("One tick for aim, two for level, and a click for lighting.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } }
    )
}

@Composable
private fun CueToggle(label: String, checked: Boolean, change: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = change)
        .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
