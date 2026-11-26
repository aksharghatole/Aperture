package com.aperture.photo.camera

import kotlin.math.abs
import kotlin.math.roundToInt

enum class Framing(val title: String, val anchorX: Float, val anchorY: Float) {
    THIRDS("Thirds", 1f / 3f, 1f / 3f),
    CENTER("Center", .5f, .5f),
    HORIZON("Horizon", .5f, .5f),
    UPPER_CENTER("Upper center", .5f, 1f / 3f)
}

enum class ShootingMode(
    val title: String,
    val framing: Framing,
    val rollTolerance: Float = 4f,
    val aimTolerance: Float = .14f,
    val lowLightBrightness: Int = 52
) {
    PORTRAIT("Portrait", Framing.UPPER_CENTER, aimTolerance = .12f),
    LANDSCAPE("Landscape", Framing.HORIZON, rollTolerance = 2.5f),
    STREET("Street", Framing.THIRDS),
    ARCHITECTURE("Architecture", Framing.CENTER, rollTolerance = 2f),
    FOOD("Food", Framing.THIRDS, aimTolerance = .11f),
    MACRO("Macro", Framing.CENTER, aimTolerance = .09f),
    NIGHT("Night", Framing.CENTER, lowLightBrightness = 72)
}

data class FocusRegion(val x: Float, val y: Float)

data class Scene(
    val brightness: Int,
    val shadowFraction: Float,
    val highlightFraction: Float,
    val focus: FocusRegion?
)

/** Finds a dominant cluster of detail in the preview. This is not object recognition. */
object Composition {
    fun measure(samples: IntArray, width: Int, height: Int): Scene {
        require(width >= 3 && height >= 3 && samples.size == width * height)
        var sum = 0
        var shadows = 0
        var highlights = 0
        for (value in samples) {
            sum += value
            if (value < 40) shadows++
            if (value > 225) highlights++
        }

        val columns = 5
        val rows = 5
        val detail = IntArray(columns * rows)
        var total = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                val gradient = abs(samples[index - 1] - samples[index + 1]) +
                    abs(samples[index - width] - samples[index + width])
                val weight = (gradient - 28).coerceAtLeast(0)
                detail[(y * rows / height) * columns + x * columns / width] += weight
                total += weight
            }
        }

        val peak = detail.indices.maxBy { detail[it] }
        val focus = if (total > samples.size * 1.5f && detail[peak] >= total * .34f) {
            val cellX = peak % columns
            val cellY = peak / columns
            var momentX = 0f
            var momentY = 0f
            var mass = 0
            for (index in detail.indices) {
                val x = index % columns
                val y = index / columns
                if (abs(x - cellX) > 1 || abs(y - cellY) > 1) continue
                momentX += (x + .5f) * detail[index]
                momentY += (y + .5f) * detail[index]
                mass += detail[index]
            }
            FocusRegion(momentX / mass / columns, momentY / mass / rows)
        } else null
        return Scene(
            (sum.toFloat() / samples.size).roundToInt(),
            shadows.toFloat() / samples.size,
            highlights.toFloat() / samples.size,
            focus
        )
    }

    fun guidance(scene: Scene?, roll: Float?, mode: ShootingMode, analysisAvailable: Boolean = true): String =
        guidance(scene, roll, mode.framing, analysisAvailable, mode.rollTolerance,
            mode.aimTolerance, mode.lowLightBrightness)

    fun guidance(
        scene: Scene?, roll: Float?, framing: Framing, analysisAvailable: Boolean = true,
        rollTolerance: Float = 4f, aimTolerance: Float = .14f, lowLightBrightness: Int = 52
    ): String {
        if (roll != null && abs(roll) >= rollTolerance) {
            val direction = if (roll > 0) "clockwise" else "counterclockwise"
            return "Rotate $direction · ${abs(roll).roundToInt()}°"
        }
        if (scene == null && analysisAvailable) return "Reading the viewfinder…"
        if (framing == Framing.HORIZON && scene == null) {
            return if (roll == null) "Use the horizon guide" else "Camera level"
        }
        if (scene == null) return "Use the grid to frame your subject"
        if (scene.brightness < lowLightBrightness && scene.shadowFraction > .45f) return "Low light · hold steady"
        if (scene.highlightFraction > .28f) return "Bright areas may lose detail"
        if (framing == Framing.HORIZON) {
            return if (roll == null) "Use the grid to judge the horizon" else "Camera level"
        }
        val focus = scene.focus ?: return "Use the grid to frame your subject"
        val x = if (framing == Framing.THIRDS && focus.x > .5f) 1f - framing.anchorX else framing.anchorX
        val dx = focus.x - x
        val dy = focus.y - framing.anchorY
        return when {
            abs(dx) > aimTolerance && abs(dx) >= abs(dy) -> if (dx > 0) "Aim a little right" else "Aim a little left"
            abs(dy) > aimTolerance -> if (dy > 0) "Aim a little lower" else "Aim a little higher"
            else -> "Detail near the framing guide"
        }
    }

    fun sensorRoll(x: Float, y: Float, z: Float): Float? {
        if (kotlin.math.hypot(x, y) < 4f || abs(z) > 8.7f) return null
        val degrees = Math.toDegrees(kotlin.math.atan2(x.toDouble(), y.toDouble())).toFloat()
        return degrees - 90f * (degrees / 90f).roundToInt()
    }
}
