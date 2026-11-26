package com.aperture.photo.imaging

/** Output levels for input levels 0, 128 and 255. */
data class ToneCurve(val black: Int = 0, val midtone: Int = 128, val white: Int = 255) {
    init {
        require(black in 0..midtone && midtone in black..white && white in midtone..255)
    }

    internal fun map(level: Int): Int = if (level <= 128) {
        black + ((midtone - black) * level + 64) / 128
    } else {
        midtone + ((white - midtone) * (level - 128) + 63) / 127
    }
}

data class RgbCurves(
    val master: ToneCurve = ToneCurve(),
    val red: ToneCurve = ToneCurve(),
    val green: ToneCurve = ToneCurve(),
    val blue: ToneCurve = ToneCurve()
)

internal class CurveLookup(curves: RgbCurves) {
    private val red = IntArray(256) { curves.red.map(curves.master.map(it)) shl 16 }
    private val green = IntArray(256) { curves.green.map(curves.master.map(it)) shl 8 }
    private val blue = IntArray(256) { curves.blue.map(curves.master.map(it)) }

    fun apply(argb: Int): Int {
        if (argb ushr 24 == 0) return argb
        return (argb and -0x1000000) or red[(argb ushr 16) and 255] or green[(argb ushr 8) and 255] or blue[argb and 255]
    }
}
