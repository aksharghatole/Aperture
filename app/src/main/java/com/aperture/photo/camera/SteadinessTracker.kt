package com.aperture.photo.camera

import kotlin.math.sqrt

internal enum class MotionChannel(val limit: Double) { ROTATION(.065), TRANSLATION(.30) }
internal enum class Steadiness(val label: String) {
    UNAVAILABLE("Motion sensors unavailable"), WAITING("Checking motion…"),
    MOVING("Hold the phone still"), STEADY("Phone appears steady")
}

/** Both sensors must report continuously low motion; subject blur is not measured. */
internal class SteadinessTracker {
    private class Channel { var last: Long? = null; var quietSince: Long? = null }
    private val channels = MotionChannel.entries.associateWith { Channel() }
    var available = false
        private set
    fun start(supported: Boolean) { clear(); available = supported }
    fun clear() {
        available = false
        channels.values.forEach { it.last = null; it.quietSince = null }
    }
    fun sample(kind: MotionChannel, at: Long, x: Float, y: Float, z: Float) {
        if (!available) return
        val state = channels.getValue(kind)
        val previous = state.last
        if (at < 0 || (previous != null && at < previous) ||
            !x.isFinite() || !y.isFinite() || !z.isFinite()) {
            state.last = null; state.quietSince = null; return
        }
        if (previous == at) return
        if (previous == null || at - previous > MaxGapMillis) state.quietSince = null
        state.last = at
        val magnitude = sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)
        if (magnitude > kind.limit) state.quietSince = null
        else if (state.quietSince == null) state.quietSince = at
    }
    fun status(now: Long): Steadiness {
        if (!available) return Steadiness.UNAVAILABLE
        if (channels.values.any { val at = it.last; at == null || now < at || now - at > MaxGapMillis })
            return Steadiness.WAITING
        val quiet = channels.values.map { it.quietSince ?: return Steadiness.MOVING }
        val end = channels.values.minOf { it.last!! }
        return if (end - quiet.max() >= HoldMillis) Steadiness.STEADY else Steadiness.MOVING
    }
    fun canCapture(armedAt: Long, now: Long): Boolean =
        now >= armedAt && now - armedAt in HoldMillis until ShotTimeoutMillis &&
            status(now) == Steadiness.STEADY &&
            channels.values.minOf { it.last!! } - armedAt >= HoldMillis
    companion object {
        const val HoldMillis = 900L
        const val MaxGapMillis = 250L
        const val ShotTimeoutMillis = 10_000L
    }
}
