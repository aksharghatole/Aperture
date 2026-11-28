package com.aperture.photo.camera

internal data class ShootingLesson(val framing: String, val light: String, val technique: String)
internal data class FocusSuggestion(val point: FocusRegion, val label: String)
internal data class ShootingAdvice(val lesson: ShootingLesson, val liveTip: String?, val focus: FocusSuggestion?,
    val recommendTimer: Boolean)

internal object ShootingCoach {
    fun advise(mode: ShootingMode, reading: SceneReading?, now: Long, marker: ManualSubject?): ShootingAdvice {
        val scene = SceneReading.accept(reading, now)?.scene
        val lesson = when (mode) {
            ShootingMode.PORTRAIT -> ShootingLesson(
                "Place eyes near the upper third and leave comfortable headroom.",
                "Try soft window light from the side; avoid harsh overhead light.",
                "Tap the nearer eye. Step back rather than using a very wide lens close to a face.")
            ShootingMode.LANDSCAPE -> ShootingLesson(
                "Keep the horizon level; give either sky or foreground more space.",
                "Look for interesting side light and protect bright clouds.",
                "Include foreground detail and use a stable support in dim light.")
            ShootingMode.STREET -> ShootingLesson(
                "Leave space in the direction of movement and watch the frame edges.",
                "Use light and shadow to separate the subject from the background.",
                "Anticipate movement; in dim light moving subjects may blur.")
            ShootingMode.ARCHITECTURE -> ShootingLesson(
                "Keep the phone upright for straighter verticals; try symmetry.",
                "Side light reveals texture and shape.",
                "Step back to fit the building instead of tilting the phone sharply upward.")
            ShootingMode.FOOD -> ShootingLesson(
                "Try overhead for flat dishes or a diagonal angle for taller food.",
                "Use soft side light and move distracting items away.",
                "Tap the main ingredient; avoid digital zoom if you can move closer.")
            ShootingMode.MACRO -> ShootingLesson(
                "Keep the subject clear and leave space around fine details.",
                "Add diffuse light without casting your phone's shadow.",
                "Move slowly until focus is possible; brace the phone for still subjects.")
            ShootingMode.NIGHT -> ShootingLesson(
                "Use bright details as anchors and preserve intentional dark areas.",
                "Avoid pointing directly at strong lights unless that is your intention.",
                "Brace the phone or use a tripod; a timer helps for still scenes.")
        }
        val tip = scene?.let {
            when (SmartExposure.classify(it, mode)) {
                ExposureSuggestion.MIXED -> "Strong contrast in the preview. Try a different angle or softer light."
                ExposureSuggestion.DARK -> "The preview is dark. Add light or steady the phone for still subjects."
                ExposureSuggestion.BRIGHT -> "The preview is bright. Check bright areas before capturing."
                ExposureSuggestion.NIGHT -> "Dark preview is expected at night. Keep the phone steady."
                ExposureSuggestion.BALANCED -> null
            }
        }
        // A user marker is fixed in viewfinder space; tracked eyes are fresh evidence, not recognition.
        val focus = marker?.center?.let { FocusSuggestion(it, "Focus / meter at marker") }
            ?: if (mode == ShootingMode.PORTRAIT && scene?.portraitGroup == null)
                scene?.portraitEyes?.let { FocusSuggestion(it, "Focus / meter near eyes") }
            else null
        val validFocus = focus?.takeIf { it.point.x in 0f..1f && it.point.y in 0f..1f }
        val timer = mode == ShootingMode.NIGHT || (mode == ShootingMode.MACRO &&
            scene != null && scene.brightness <= 85 && scene.shadowFraction >= .4f)
        return ShootingAdvice(lesson, tip, validFocus, timer)
    }
}
