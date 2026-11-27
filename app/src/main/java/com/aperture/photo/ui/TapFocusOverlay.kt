package com.aperture.photo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aperture.photo.camera.FocusFeedback
import com.aperture.photo.camera.FocusStatus

@Composable
internal fun TapFocusOverlay(feedback: FocusFeedback, modifier: Modifier = Modifier) {
    val color = when (feedback.status) {
        FocusStatus.PENDING -> Color.White
        FocusStatus.SUCCESS -> Color(0xff8bea9a)
        FocusStatus.FAILED -> Color(0xffff7777)
        FocusStatus.UNSUPPORTED -> Color(0xffffcf78)
        FocusStatus.METERING -> Color(0xff8bd8ff)
        FocusStatus.METERED -> Color(0xffffcf78)
    }
    val label = when (feedback.status) {
        FocusStatus.PENDING -> "Focus requested"
        FocusStatus.SUCCESS -> "Autofocus succeeded"
        FocusStatus.FAILED -> if (feedback.autofocus) "Autofocus failed" else "Exposure metering failed"
        FocusStatus.UNSUPPORTED -> "Autofocus and exposure metering unavailable at this point"
        FocusStatus.METERING -> "Exposure metering requested; autofocus unavailable"
        FocusStatus.METERED -> "Exposure metered; autofocus unavailable"
    }
    Canvas(modifier.fillMaxSize().semantics {
        contentDescription = label
        liveRegion = LiveRegionMode.Polite
    }) {
        val center = Offset(size.width * feedback.x, size.height * feedback.y)
        val radius = 22.dp.toPx()
        drawCircle(color.copy(alpha = .22f), radius + 5.dp.toPx(), center)
        drawCircle(color, radius, center, style = Stroke(2.dp.toPx()))
        val arm = 5.dp.toPx()
        val gap = 7.dp.toPx()
        drawLine(color, Offset(center.x - arm, center.y), Offset(center.x - gap, center.y), 2.dp.toPx())
        drawLine(color, Offset(center.x + gap, center.y), Offset(center.x + arm, center.y), 2.dp.toPx())
        drawLine(color, Offset(center.x, center.y - arm), Offset(center.x, center.y - gap), 2.dp.toPx())
        drawLine(color, Offset(center.x, center.y + gap), Offset(center.x, center.y + arm), 2.dp.toPx())
    }
}
