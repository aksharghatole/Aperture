package com.aperture.photo.imaging

import android.graphics.Bitmap
/** Deterministic, resolution-relative monochrome grain for the non-destructive render pipeline. */
internal object GrainEffect {
    private const val Seed = 0x41504552
    private const val MinimumCells = 6
    private const val MaximumCells = 48
    private const val MaximumAmplitude = 24f

    fun apply(bitmap: Bitmap, intensity: Float, size: Float) {
        if (intensity <= 0f) return
        val shortEdge = minOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val cells = cellsPerShortEdge(size)
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) row[x] = adjustColor(row[x], noiseForPixel(x, y, shortEdge, cells), intensity)
            bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }

    internal fun cellsPerShortEdge(size: Float): Int {
        val normalized = size.coerceIn(0f, 1f)
        return (MaximumCells - normalized * (MaximumCells - MinimumCells)).toInt().coerceAtLeast(MinimumCells)
    }

    internal fun noiseForPixel(x: Int, y: Int, shortEdge: Int, cellsPerShortEdge: Int): Float {
        require(x >= 0 && y >= 0 && shortEdge > 0 && cellsPerShortEdge > 0)
        val cellX = x * cellsPerShortEdge / shortEdge
        val cellY = y * cellsPerShortEdge / shortEdge
        return noise(cellX, cellY)
    }

    internal fun noise(cellX: Int, cellY: Int): Float {
        var value = cellX * 0x1f123bb5 xor cellY * 0x5f356495 xor Seed
        value = (value xor (value ushr 16)) * 0x7feb352d
        value = (value xor (value ushr 15)) * 0x846ca68b.toInt()
        value = value xor (value ushr 16)
        return ((value ushr 8) / 0x7fffff.toFloat()) - 1f
    }

    internal fun adjustColor(color: Int, noise: Float, intensity: Float): Int {
        if (intensity <= 0f) return color
        val offset = noise * MaximumAmplitude * intensity.coerceIn(0f, 1f)
        fun channel(shift: Int) = (((color ushr shift) and 0xff) + offset).toInt().coerceIn(0, 255)
        return (color and -0x1000000) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
