package com.aperture.photo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aperture.photo.imaging.LuminanceHistogram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun EditorHistogram(preview: Bitmap, modifier: Modifier = Modifier) {
    var histogram by remember(preview) { mutableStateOf<LuminanceHistogram?>(null) }
    LaunchedEffect(preview) {
        histogram = withContext(Dispatchers.Default) { sampleHistogram(preview) }
    }
    val data = histogram
    Column(
        modifier.width(168.dp).background(Color(0xe6191c19), RoundedCornerShape(12.dp)).padding(10.dp)
    ) {
        Text("Luminance", style = MaterialTheme.typography.labelMedium, color = Color.White)
        if (data == null) {
            Text("Updating…", color = Color.White, style = MaterialTheme.typography.labelSmall)
        } else if (data.sampleCount == 0) {
            Text("No visible pixels", color = Color.White, style = MaterialTheme.typography.labelSmall)
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

private fun sampleHistogram(bitmap: Bitmap): LuminanceHistogram {
    val builder = LuminanceHistogram.Builder()
    val row = IntArray(bitmap.width)
    val stepX = ((bitmap.width + 127) / 128).coerceAtLeast(1)
    val stepY = ((bitmap.height + 127) / 128).coerceAtLeast(1)
    for (y in stepY / 2 until bitmap.height step stepY) {
        bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        for (x in stepX / 2 until bitmap.width step stepX) builder.add(row[x])
    }
    return builder.build()
}
