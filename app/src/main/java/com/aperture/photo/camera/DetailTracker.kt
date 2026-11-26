package com.aperture.photo.camera

import kotlin.math.hypot

/** Requires consecutive nearby measurements and drops the cue as soon as evidence disappears. */
class DetailTracker {
    private var previous: FocusRegion? = null

    fun update(measured: FocusRegion?): FocusRegion? {
        val last = previous
        previous = measured
        if (last == null || measured == null || hypot(last.x - measured.x, last.y - measured.y) > .16f) {
            return null
        }
        return FocusRegion((last.x + measured.x) / 2f, (last.y + measured.y) / 2f)
    }
}
