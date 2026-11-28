package com.aperture.photo.camera

enum class CompositionAnchor(val title: String, val point: FocusRegion? = null) {
    AUTO("Auto"),
    CENTER("Center", FocusRegion(.5f, .5f)),
    UPPER_LEFT("Upper left", FocusRegion(1f / 3f, 1f / 3f)),
    UPPER_RIGHT("Upper right", FocusRegion(2f / 3f, 1f / 3f)),
    LOWER_LEFT("Lower left", FocusRegion(1f / 3f, 2f / 3f)),
    LOWER_RIGHT("Lower right", FocusRegion(2f / 3f, 2f / 3f));

    internal fun target(framing: Framing, evidence: FocusRegion? = null): FocusRegion {
        val x = if (framing == Framing.THIRDS && evidence != null && evidence.x > .5f) {
            1f - framing.anchorX
        } else framing.anchorX
        return point ?: FocusRegion(x, framing.anchorY)
    }
}

/** A fixed screen region in the current viewfinder geometry. */
internal data class ManualSubject(val center: FocusRegion) {
    companion object {
        const val HalfExtent = .06f

        fun fromTap(x: Float, y: Float, width: Int, height: Int): ManualSubject? {
            if (width <= 0 || height <= 0 || !x.isFinite() || !y.isFinite()) return null
            val bounds = HalfExtent..(1f - HalfExtent)
            return ManualSubject(FocusRegion((x / width).coerceIn(bounds), (y / height).coerceIn(bounds)))
        }
    }
}
