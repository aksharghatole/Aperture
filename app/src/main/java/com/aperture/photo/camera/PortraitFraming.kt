package com.aperture.photo.camera

import kotlin.math.abs
import kotlin.math.hypot

/** An approximate region inferred from frontal eye measurements, in viewfinder coordinates. */
@ConsistentCopyVisibility
data class PortraitGroup internal constructor(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val eyeCenter: FocusRegion
) {
    internal fun fitTargetY(target: Float): Float = target.coerceIn(
        (eyeCenter.y - top + PortraitFraming.EdgeMargin).coerceAtMost(eyeCenter.y),
        (eyeCenter.y + (1f - bottom) - PortraitFraming.EdgeMargin).coerceAtLeast(eyeCenter.y)
    )
}

internal data class DetectedEyes(val confidence: Float, val x: Float, val y: Float, val spanX: Float, val spanY: Float)

internal data class PortraitReading(val eyes: FocusRegion? = null, val group: PortraitGroup? = null)

internal object PortraitFraming {
    const val MaxGroupSize = 4
    const val EdgeMargin = .025f

    fun validate(detected: List<DetectedEyes>, mirrored: Boolean): List<DetectedEyes>? {
        if (detected.size !in 1..MaxGroupSize) return null
        val isGroup = detected.size > 1
        val minConfidence = if (isGroup) .75f else .65f
        if (detected.any { eye ->
            eye.confidence !in minConfidence..1f || eye.x !in 0f..1f || eye.y !in 0f..1f ||
                eye.spanX !in 0.035f..0.65f || !eye.spanY.isFinite() || eye.spanY <= 0f ||
                eye.x - eye.spanX / 2f < 0f || eye.x + eye.spanX / 2f > 1f
        }) return null
        val upright = if (mirrored) detected.map { it.copy(x = 1f - it.x) } else detected
        if (isGroup && (groupBounds(upright) == null || hasOverlappingEyes(upright))) return null
        return upright
    }

    fun groupBounds(eyes: List<DetectedEyes>): PortraitGroup? {
        if (eyes.size !in 2..MaxGroupSize) return null
        // Eye spacing gives a rough head margin. CameraX does not provide face rectangles here.
        val left = eyes.minOf { it.x - it.spanX }
        val right = eyes.maxOf { it.x + it.spanX }
        val top = eyes.minOf { it.y - .9f * it.spanY }
        val bottom = eyes.maxOf { it.y + 1.5f * it.spanY }
        if (left < EdgeMargin || top < EdgeMargin || right > 1f - EdgeMargin || bottom > 1f - EdgeMargin) return null
        return PortraitGroup(left, top, right, bottom,
            FocusRegion((left + right) / 2f, (eyes.minOf { it.y } + eyes.maxOf { it.y }) / 2f))
    }

    private fun hasOverlappingEyes(eyes: List<DetectedEyes>): Boolean = eyes.indices.any { index ->
        val a = eyes[index]
        ((index + 1) until eyes.size).any { next ->
            val b = eyes[next]
            abs(a.x - b.x) < (a.spanX + b.spanX) / 2f && abs(a.y - b.y) < (a.spanY + b.spanY) / 3f
        }
    }
}

internal class PortraitTracker {
    private val single = DetailTracker()
    private var previous: List<DetectedEyes>? = null
    private var groupStreak = 0

    fun update(measured: List<DetectedEyes>?): PortraitReading? {
        val last = previous
        previous = measured
        if (measured == null || measured.size != 1) single.update(null)
        if (measured == null || measured.size < 2) {
            groupStreak = 0
            return measured?.singleOrNull()?.let { single.update(FocusRegion(it.x, it.y)) }?.let {
                PortraitReading(eyes = it)
            }
        }
        groupStreak = if (last != null && sameGroup(last, measured)) {
            (groupStreak + 1).coerceAtMost(RequiredFrames)
        } else 1
        return if (groupStreak == RequiredFrames) PortraitFraming.groupBounds(measured)?.let {
            PortraitReading(group = it)
        } else null
    }

    private fun sameGroup(previous: List<DetectedEyes>, current: List<DetectedEyes>): Boolean {
        if (previous.size != current.size) return false
        val used = BooleanArray(current.size)
        for (old in previous) {
            val matches = current.indices.filter { index ->
                val eye = current[index]
                hypot(eye.x - old.x, eye.y - old.y) <= .09f &&
                    eye.spanX / old.spanX in (1f / 1.3f)..1.3f &&
                    eye.spanY / old.spanY in (1f / 1.3f)..1.3f
            }
            val match = matches.singleOrNull() ?: return false
            if (used[match]) return false
            used[match] = true
        }
        return true
    }

    private companion object { const val RequiredFrames = 3 }
}
