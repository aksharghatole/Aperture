package com.aperture.photo.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aperture.photo.camera.MotionChannel
import com.aperture.photo.camera.Steadiness
import com.aperture.photo.camera.SteadinessTracker
import kotlinx.coroutines.delay

internal class MotionMonitor {
    val tracker = SteadinessTracker()
    var status by mutableStateOf(Steadiness.UNAVAILABLE)
        private set
    fun refresh() { status = tracker.status(SystemClock.elapsedRealtime()) }
}

@Composable
internal fun rememberMotionMonitor(enabled: Boolean): MotionMonitor {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val monitor = remember { MotionMonitor() }
    DisposableEffect(context, owner, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gyro = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val linear = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val kind = when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> MotionChannel.ROTATION
                    Sensor.TYPE_LINEAR_ACCELERATION -> MotionChannel.TRANSLATION
                    else -> return
                }
                if (event.values.size < 3) return
                monitor.tracker.sample(kind, event.timestamp / 1_000_000L,
                    event.values[0], event.values[1], event.values[2])
            }
        }
        fun stop() {
            manager?.unregisterListener(listener)
            monitor.tracker.clear()
            monitor.refresh()
        }
        fun start() {
            stop()
            if (!enabled || manager == null || gyro == null || linear == null) return
            val registered = runCatching {
                manager.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME) &&
                    manager.registerListener(listener, linear, SensorManager.SENSOR_DELAY_GAME)
            }.getOrDefault(false)
            if (!registered) stop()
            else { monitor.tracker.start(true); monitor.refresh() }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); stop() }
    }
    LaunchedEffect(monitor, enabled) {
        if (enabled) while (true) { monitor.refresh(); delay(100) }
    }
    return monitor
}
