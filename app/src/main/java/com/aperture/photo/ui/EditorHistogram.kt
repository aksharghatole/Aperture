package com.aperture.photo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aperture.photo.imaging.LuminanceHistogram
import com.aperture.photo.imaging.RgbHistogram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun EditorHistogram(preview: Bitmap, modifier: Modifier = Modifier) {
    var histogram by remember(preview) { mutableStateOf<LuminanceHistogram?>(null) }
    var rgbHistogram by remember(preview) { mutableStateOf<RgbHistogram?>(null) }
    var showRgb by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(preview) {
        val samples = withContext(Dispatchers.Default) { sampleHistograms(preview) }
        histogram = samples.first
        rgbHistogram = samples.second
    }
    val data = histogram
    Column(
        modifier.width(168.dp).background(Color(0xe6191c19), RoundedCornerShape(12.dp)).padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (showRgb) "RGB · preview estimate" else "Luminance · preview estimate",
                style = MaterialTheme.typography.labelMedium, color = Color.White)
            TextButton(onClick = { showRgb = !showRgb }, contentPadding = PaddingValues(0.dp)) {
                Text(if (showRgb) "Luma" else "RGB", color = Color.White)
            }
        }
        if (data == null) {
            Text("Updating…", color = Color.White, style = MaterialTheme.typography.labelSmall)
        } else if (data.sampleCount == 0) {
            Text("No visible pixels", color = Color.White, style = MaterialTheme.typography.labelSmall)
        } else if (showRgb && rgbHistogram != null) {
            RgbHistogramView(rgbHistogram!!)
        } else {
            val peak = data.bins.max()
            val nearBlack = (100f * data.nearBlackCount / data.sampleCount).roundToInt()
            val nearWhite = (100f * data.nearWhiteCount / data.sampleCount).roundToInt()
            val accent = MaterialTheme.colorScheme.primary
            Canvas(
                Modifier.fillMaxWidth().height(42.dp).padding(vertical = 4.dp).semantics {
                    contentDescription = "Luminance histogram from dark tones on the left to light tones on the right"
                }
            ) {
                val width = size.width / data.bins.size
                for ((index, count) in data.bins.withIndex()) {
                    val height = size.height * count / peak
                    drawRect(accent, Offset(index * width, size.height - height), Size(width, height))
                }
            }
            Text("Near black $nearBlack%  ·  Near white $nearWhite%", color = Color.White,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RgbHistogramView(data: RgbHistogram) {
    if (data.sampleCount == 0) {
        Text("No visible pixels", color = Color.White, style = MaterialTheme.typography.labelSmall)
        return
    }
    val peak = maxOf(data.red.max(), data.green.max(), data.blue.max()).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(42.dp).padding(vertical = 4.dp).semantics {
        contentDescription = "RGB channel histogram. Red, green, and blue lines show separate channel values."
    }) {
        fun drawChannel(bins: IntArray, color: Color) {
            val points = bins.mapIndexed { index, count ->
                Offset(size.width * index / (bins.size - 1), size.height * (1f - count.toFloat() / peak))
            }
            points.zipWithNext().forEach { (start, end) -> drawLine(color, start, end, 1.5.dp.toPx()) }
        }
        drawChannel(data.red, Color(0xffff6868))
        drawChannel(data.green, Color(0xff83e58b))
        drawChannel(data.blue, Color(0xff78a7ff))
    }
    fun percent(count: Int) = (100f * count / data.sampleCount).roundToInt()
    Text("R ${percent(data.redNearBlack)}% dark / ${percent(data.redNearWhite)}% clipped  ·  " +
        "G ${percent(data.greenNearBlack)}% / ${percent(data.greenNearWhite)}%  ·  " +
        "B ${percent(data.blueNearBlack)}% / ${percent(data.blueNearWhite)}%",
        color = Color.White, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.semantics { contentDescription = "Per-channel clipping percentages. Values are ordered dark then clipped: " +
            "red ${percent(data.redNearBlack)} percent dark, ${percent(data.redNearWhite)} percent clipped; " +
            "green ${percent(data.greenNearBlack)} percent dark, ${percent(data.greenNearWhite)} percent clipped; " +
            "blue ${percent(data.blueNearBlack)} percent dark, ${percent(data.blueNearWhite)} percent clipped." })
}

private fun sampleHistograms(bitmap: Bitmap): Pair<LuminanceHistogram, RgbHistogram> {
    val builder = LuminanceHistogram.Builder()
    val rgbBuilder = RgbHistogram.Builder()
    val row = IntArray(bitmap.width)
    val stepX = ((bitmap.width + 127) / 128).coerceAtLeast(1)
    val stepY = ((bitmap.height + 127) / 128).coerceAtLeast(1)
    for (y in stepY / 2 until bitmap.height step stepY) {
        bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        for (x in stepX / 2 until bitmap.width step stepX) {
            builder.add(row[x])
            rgbBuilder.add(row[x])
        }
    }
    return builder.build() to rgbBuilder.build()
}
