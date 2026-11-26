package com.aperture.photo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.aperture.photo.imaging.RgbCurves
import com.aperture.photo.imaging.ToneCurve
import kotlin.math.roundToInt

private enum class CurveChannel(val label: String, val color: Color) {
    MASTER("RGB", Color.LightGray), RED("Red", Color(0xffff8888)),
    GREEN("Green", Color(0xff8deb9d)), BLUE("Blue", Color(0xff93b8ff))
}

@Composable
internal fun EditorCurves(
    curves: RgbCurves,
    preview: Bitmap?,
    change: (RgbCurves) -> Unit,
    cancel: () -> Unit,
    apply: () -> Unit
) {
    var channel by rememberSaveable { mutableStateOf(CurveChannel.MASTER) }
    val curve = when (channel) {
        CurveChannel.MASTER -> curves.master
        CurveChannel.RED -> curves.red
        CurveChannel.GREEN -> curves.green
        CurveChannel.BLUE -> curves.blue
    }

    fun update(value: ToneCurve) = change(when (channel) {
        CurveChannel.MASTER -> curves.copy(master = value)
        CurveChannel.RED -> curves.copy(red = value)
        CurveChannel.GREEN -> curves.copy(green = value)
        CurveChannel.BLUE -> curves.copy(blue = value)
    })

    AlertDialog(
        onDismissRequest = cancel,
        title = { Text("Curves") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CurveChannel.entries.forEach { option ->
                        FilterChip(selected = channel == option, onClick = { channel = option }, label = { Text(option.label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.weight(1f).height(100.dp)) {
                        fun point(input: Int, level: Int) = Offset(size.width * input / 255f, size.height * (255 - level) / 255f)
                        drawLine(Color.Gray, point(0, 0), point(255, 255))
                        val black = point(0, curve.black)
                        val middle = point(128, curve.midtone)
                        val white = point(255, curve.white)
                        drawLine(channel.color, black, middle, 2.dp.toPx())
                        drawLine(channel.color, middle, white, 2.dp.toPx())
                        drawCircle(channel.color, 3.dp.toPx(), middle)
                    }
                    preview?.let {
                        Image(it.asImageBitmap(), "Curves preview", Modifier.weight(1f).height(100.dp),
                            contentScale = ContentScale.Fit)
                    }
                }
                CurveSlider("Black", curve.black) { update(curve.copy(black = it.coerceAtMost(curve.midtone))) }
                CurveSlider("Midtone", curve.midtone) { update(curve.copy(midtone = it.coerceIn(curve.black, curve.white))) }
                CurveSlider("White", curve.white) { update(curve.copy(white = it.coerceAtLeast(curve.midtone))) }
                TextButton(onClick = { update(ToneCurve()) }) { Text("Reset channel") }
            }
        },
        confirmButton = { TextButton(onClick = apply) { Text("Apply") } },
        dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } }
    )
}

@Composable
private fun CurveSlider(label: String, value: Int, change: (Int) -> Unit) {
    Text("$label $value", style = MaterialTheme.typography.labelMedium)
    Slider(value = value.toFloat(), onValueChange = { change(it.roundToInt()) }, valueRange = 0f..255f)
}
