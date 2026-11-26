package com.aperture.photo.camera

internal data class SceneReading(val scene: Scene, val sampledAtMillis: Long) {
    fun remainingMillis(nowMillis: Long): Long =
        if (nowMillis < sampledAtMillis) 0L
        else (MaxAgeMillis - (nowMillis - sampledAtMillis)).coerceAtLeast(0L)

    companion object {
        const val MaxAgeMillis = 800L

        fun accept(reading: SceneReading?, nowMillis: Long): SceneReading? =
            reading?.takeIf { it.remainingMillis(nowMillis) > 0L }
    }
}
