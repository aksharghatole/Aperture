package com.aperture.photo.camera

enum class CompositionGuide(val title: String) {
    AUTO("Auto"), THIRDS("Thirds"), CENTER("Center"), DIAGONAL("Diagonal"), GOLDEN("Golden"), OFF("Off")
}

data class NormalizedGuideLine(val startX: Float, val startY: Float, val endX: Float, val endY: Float)

object CompositionGuideGeometry {
    fun lines(guide: CompositionGuide, mode: ShootingMode): List<NormalizedGuideLine> = when (guide) {
        CompositionGuide.AUTO -> when (mode.framing) {
            Framing.THIRDS -> thirds(1f / 3f, 2f / 3f)
            Framing.HORIZON -> listOf(NormalizedGuideLine(0f, .5f, 1f, .5f))
            Framing.CENTER, Framing.UPPER_CENTER -> emptyList()
        }
        CompositionGuide.THIRDS -> thirds(1f / 3f, 2f / 3f)
        CompositionGuide.CENTER -> listOf(
            NormalizedGuideLine(.5f, 0f, .5f, 1f),
            NormalizedGuideLine(0f, .5f, 1f, .5f)
        )
        CompositionGuide.DIAGONAL -> listOf(
            NormalizedGuideLine(0f, 0f, 1f, 1f),
            NormalizedGuideLine(1f, 0f, 0f, 1f)
        )
        CompositionGuide.GOLDEN -> thirds(.382f, .618f)
        CompositionGuide.OFF -> emptyList()
    }

    private fun thirds(first: Float, second: Float) = listOf(
        NormalizedGuideLine(first, 0f, first, 1f),
        NormalizedGuideLine(second, 0f, second, 1f),
        NormalizedGuideLine(0f, first, 1f, first),
        NormalizedGuideLine(0f, second, 1f, second)
    )
}
