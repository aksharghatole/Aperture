package com.aperture.photo.camera

internal enum class CueKind { AIM, LEVEL, LIGHT }

internal enum class CompositionCue(val words: String, val kind: CueKind) {
    AIM_LEFT("Aim a little left", CueKind.AIM),
    AIM_RIGHT("Aim a little right", CueKind.AIM),
    AIM_HIGHER("Aim a little higher", CueKind.AIM),
    AIM_LOWER("Aim a little lower", CueKind.AIM),
    CLOCKWISE("Rotate clockwise", CueKind.LEVEL),
    COUNTERCLOCKWISE("Rotate counterclockwise", CueKind.LEVEL),
    LOW_LIGHT("Low light · hold steady", CueKind.LIGHT),
    HIGHLIGHTS("Bright areas may lose detail", CueKind.LIGHT)
}

internal data class CompositionAdvice(val text: String, val cue: CompositionCue? = null)

/** Emits only after a cue persists across distinct fresh preview samples. */
internal class CompositionCueGate {
    private var candidate: CompositionCue? = null
    private var firstSample = 0L
    private var latestSample: Long? = null
    private var lastEmission: Long? = null
    private var lastEmitted: CompositionCue? = null

    fun clearEvidence() {
        candidate = null
        latestSample = null
    }

    fun observe(cue: CompositionCue?, sampledAt: Long?, now: Long): CompositionCue? {
        if (cue == null || sampledAt == null || now - sampledAt !in 0L until SceneReading.MaxAgeMillis) {
            clearEvidence()
            return null
        }
        val previous = latestSample
        if (previous != null && sampledAt < previous) return null
        if (previous == null || candidate != cue || sampledAt - previous >= SceneReading.MaxAgeMillis) {
            candidate = cue
            firstSample = sampledAt
            latestSample = sampledAt
            return null
        }
        if (sampledAt == previous) return null
        latestSample = sampledAt
        if (sampledAt - firstSample < StabilityMillis) return null
        val last = lastEmission
        val cooldown = if (lastEmitted == cue) RepeatMillis else ChangeMillis
        if (last != null && now - last < cooldown) return null
        lastEmission = now
        lastEmitted = cue
        return cue
    }

    private companion object {
        const val StabilityMillis = 180L
        const val ChangeMillis = 2_500L
        const val RepeatMillis = 8_000L
    }
}
