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
    val focus: FocusRegion?,
    val portraitEyes: FocusRegion? = null,
    val exposureDiagnostics: ExposureDiagnostics? = null,
    val portraitGroup: PortraitGroup? = null
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
            focus,
            exposureDiagnostics = ExposureDiagnostics.from(samples)
        )
    }

    fun guidance(
        scene: Scene?, roll: Float?, mode: ShootingMode, analysisAvailable: Boolean = true,
        anchor: CompositionAnchor = CompositionAnchor.AUTO, manualSubject: Boolean = false
    ): String = advice(scene, roll, mode, analysisAvailable, anchor, manualSubject).text

    internal fun advice(
        scene: Scene?, roll: Float?, mode: ShootingMode, analysisAvailable: Boolean,
        anchor: CompositionAnchor, manualSubject: Boolean
    ): CompositionAdvice =
        advice(scene, roll, mode.framing, analysisAvailable, mode.rollTolerance,
            mode.aimTolerance, mode.lowLightBrightness, mode == ShootingMode.PORTRAIT, anchor, manualSubject)

    fun framingPoint(
        scene: Scene?, mode: ShootingMode, anchor: CompositionAnchor = CompositionAnchor.AUTO,
        manualSubject: Boolean = false
    ): FocusRegion? {
        if (manualSubject) return null
        return when {
            mode == ShootingMode.PORTRAIT -> scene?.portraitEyes
            mode == ShootingMode.LANDSCAPE && anchor == CompositionAnchor.AUTO -> null
            else -> scene?.focus
        }
    }

    fun guidance(
        scene: Scene?, roll: Float?, framing: Framing, analysisAvailable: Boolean = true,
        rollTolerance: Float = 4f, aimTolerance: Float = .14f, lowLightBrightness: Int = 52,
        portrait: Boolean = false, anchor: CompositionAnchor = CompositionAnchor.AUTO, manualSubject: Boolean = false
    ): String = advice(scene, roll, framing, analysisAvailable, rollTolerance, aimTolerance,
        lowLightBrightness, portrait, anchor, manualSubject).text

    private fun advice(
        scene: Scene?, roll: Float?, framing: Framing, analysisAvailable: Boolean,
        rollTolerance: Float, aimTolerance: Float, lowLightBrightness: Int,
        portrait: Boolean, anchor: CompositionAnchor, manualSubject: Boolean
    ): CompositionAdvice {
        if (roll != null && abs(roll) >= rollTolerance) {
            val cue = if (roll > 0) CompositionCue.CLOCKWISE else CompositionCue.COUNTERCLOCKWISE
            return CompositionAdvice("${cue.words} · ${abs(roll).roundToInt()}°", cue)
        }
        if (manualSubject) return CompositionAdvice("Manual marker · move the camera to frame your subject")
        val horizonOnly = framing == Framing.HORIZON && anchor == CompositionAnchor.AUTO
        if (scene == null && analysisAvailable && anchor == CompositionAnchor.AUTO) {
            return CompositionAdvice("Reading the viewfinder…")
        }
        if (horizonOnly && scene == null) return CompositionAdvice(if (roll == null) "Use the horizon guide" else "Camera level")
        val manualGuide = CompositionAdvice(when {
            anchor != CompositionAnchor.AUTO -> "Frame your subject at the target"
            portrait -> "Place the eyes near the upper guide"
            else -> "Use the grid to frame your subject"
        })
        if (scene == null) return manualGuide
        if (scene.brightness < lowLightBrightness && scene.shadowFraction > .45f) return CompositionCue.LOW_LIGHT.advice()
        if (scene.highlightFraction > .28f) return CompositionCue.HIGHLIGHTS.advice()
        if (horizonOnly) {
            return CompositionAdvice(if (roll == null) "Use the grid to judge the horizon" else "Camera level")
        }
        val group = if (portrait) scene.portraitGroup else null
        val focus = group?.eyeCenter ?: (if (portrait) scene.portraitEyes else scene.focus) ?: return manualGuide
        val target = anchor.target(framing, focus)
        val dx = focus.x - target.x
        val targetY = group?.fitTargetY(target.y) ?: target.y
        val dy = focus.y - targetY
        return when {
            abs(dx) > aimTolerance && abs(dx) >= abs(dy) ->
                (if (dx > 0) CompositionCue.AIM_RIGHT else CompositionCue.AIM_LEFT).advice()
            abs(dy) > aimTolerance -> (if (dy > 0) CompositionCue.AIM_LOWER else CompositionCue.AIM_HIGHER).advice()
            group != null -> CompositionAdvice("Group detected · check framing")
            else -> CompositionAdvice(if (portrait) "Eyes near the framing guide" else "Detail near the framing guide")
        }
    }

    private fun CompositionCue.advice() = CompositionAdvice(words, this)

    /** Screen-relative roll using the display's clockwise quarter-turn index (Surface.ROTATION_*). */
    fun sensorRoll(x: Float, y: Float, z: Float, displayQuarterTurns: Int): Float? {
        if (kotlin.math.hypot(x, y) < 4f || abs(z) > 8.7f) return null
        val (screenX, screenY) = when (displayQuarterTurns) {
            0 -> x to y
            1 -> -y to x
            2 -> -x to -y
            3 -> y to -x
            else -> return null
        }
        return Math.toDegrees(kotlin.math.atan2(screenX.toDouble(), screenY.toDouble())).toFloat()
    }
}
