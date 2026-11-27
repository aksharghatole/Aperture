package com.aperture.photo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aperture.photo.camera.ExposureDiagnostics
import kotlin.math.roundToInt

@Composable
internal fun ExposureDiagnosticsOverlay(diagnostics: ExposureDiagnostics?, modifier: Modifier = Modifier) {
    Column(Modifier.width(156.dp).background(Color(0xe6191c19), RoundedCornerShape(12.dp))
        .padding(10.dp).then(modifier)) {
        Text("Preview estimate", color = Color.White, fontSize = 11.sp)
        if (diagnostics == null) {
            Text("Waiting for frame…", color = Color.White, fontSize = 10.sp)
            return@Column
        }
        val peak = diagnostics.histogram.maxOrNull()?.coerceAtLeast(1) ?: 1
        val accent = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxWidth().height(38.dp).semantics {
            contentDescription = "Preview luma histogram, dark values on the left and bright values on the right"
        }) {
            val columnWidth = size.width / diagnostics.histogram.size
            diagnostics.histogram.forEachIndexed { index, count ->
                val barHeight = size.height * count / peak
                drawRect(accent, Offset(index * columnWidth, size.height - barHeight),
                    Size(columnWidth.coerceAtLeast(1f), barHeight))
            }
        }
        val dark = (diagnostics.darkFraction * 100).roundToInt()
        val bright = (diagnostics.brightFraction * 100).roundToInt()
        if (diagnostics.darkWarning) {
            Text("Deep shadows ~${dark}%", color = Color(0xffffcf78), fontSize = 10.sp)
        }
        if (diagnostics.brightWarning) {
            Text("Bright pixels ~${bright}%", color = Color(0xffffcf78), fontSize = 10.sp)
        }
        if (!diagnostics.darkWarning && !diagnostics.brightWarning) {
            Text("No strong preview warnings", color = Color.White, fontSize = 10.sp)
        }
        Text("Not RAW clipping data", color = Color.White.copy(alpha = .72f), fontSize = 9.sp)
    }
}
