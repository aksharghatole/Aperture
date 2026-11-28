package com.aperture.photo.camera

import kotlin.math.floor
import kotlin.math.roundToInt

internal enum class ExposureAssistMode(val title: String) {
    GUIDED("Guided"), AUTO("Auto"), MANUAL("Manual")
}

internal enum class ExposureSuggestion(val text: String) {
    DARK("Dark preview. Try a small exposure boost, or add light."),
    BRIGHT("Bright preview. Lower exposure to protect bright detail."),
    MIXED("Deep shadows and bright highlights. Try changing the lighting or angle."),
    NIGHT("Dark night scene. Hold steady or use a support; preserve the night mood."),
    BALANCED("No strong exposure warning in the sampled preview.")
}

internal object SmartExposure {
    fun classify(scene: Scene, mode: ShootingMode): ExposureSuggestion {
        // Preview luma is an estimate, not RAW clipping or an understanding of the subject.
        if (scene.shadowFraction >= .35f && scene.highlightFraction >= .18f)
            return ExposureSuggestion.MIXED
        if (scene.brightness >= 165 && scene.highlightFraction >= .28f)
            return ExposureSuggestion.BRIGHT
        if (scene.brightness <= 65 && scene.shadowFraction >= .55f)
            return if (mode == ShootingMode.NIGHT) ExposureSuggestion.NIGHT else ExposureSuggestion.DARK
        return ExposureSuggestion.BALANCED
    }

    /** One small adjustment, bounded by lens capabilities and a one-EV safety envelope. */
    fun target(suggestion: ExposureSuggestion, exposure: ExposureCompensation?): Int? {
        val e = exposure?.takeIf { it.supported && it.stepEv.isFinite() } ?: return null
        val sign = when (suggestion) {
            ExposureSuggestion.DARK -> 1
            ExposureSuggestion.BRIGHT -> -1
            else -> return null
        }
        val limit = floor(1f / e.stepEv).toInt()
        if (limit < 1 || e.index !in -limit..limit) return null
        val change = (.5f / e.stepEv).roundToInt().coerceIn(1, limit)
        val target = e.clamp((e.index.toLong() + sign.toLong() * change)
            .coerceIn(-limit.toLong(), limit.toLong()).toInt())
        return target.takeIf { it != e.index && it in -limit..limit }
    }
}

/** Require three distinct, consecutive fresh samples; invalid or stalled readings clear the evidence. */
internal class ExposureStability {
    private var previous: ExposureSuggestion? = null
    private var lastAt: Long? = null
    private var count = 0

    fun update(reading: SceneReading?, now: Long, mode: ShootingMode): ExposureSuggestion? {
        val sample = SceneReading.accept(reading, now)
        if (sample == null) {
            clear()
            return null
        }
        val prior = lastAt
        if (prior != null && sample.sampledAtMillis == prior)
            return previous.takeIf { count >= 3 }
        if (prior != null && (sample.sampledAtMillis < prior ||
                sample.sampledAtMillis - prior >= SceneReading.MaxAgeMillis)) clear()
        val next = SmartExposure.classify(sample.scene, mode)
        count = if (next == previous) (count + 1).coerceAtMost(3) else 1
        previous = next
        lastAt = sample.sampledAtMillis
        return next.takeIf { count >= 3 }
    }

    fun clear() {
        previous = null
        lastAt = null
        count = 0
    }
}
