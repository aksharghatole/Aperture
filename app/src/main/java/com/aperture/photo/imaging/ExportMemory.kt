package com.aperture.photo.imaging

import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max

internal class ExportMemoryException(val suggestedMaxEdge: Int?) :
    IOException("Estimated image buffers exceed the available export budget")

internal object ExportMemory {
    private const val WorkspaceBytes = 16L * 1024 * 1024

    fun budget(maxHeap: Long, usedHeap: Long): Long =
        minOf(maxHeap / 3L * 2L, (maxHeap - usedHeap).coerceAtLeast(0L))

    fun check(width: Int, height: Int, recipe: Recipe, maxEdge: Int?, budget: Long) {
        require(width > 0 && height > 0 && (maxEdge == null || maxEdge > 0))
        val longest = max(width, height)
        val sample = if (maxEdge == null) 1 else sampleSize(longest, maxEdge)
        if (estimate(width, height, recipe, sample) <= budget) return

        var next = sample
        while (next < longest) {
            next = (next.toLong() * 2).coerceAtMost(longest.toLong()).toInt()
            if (estimate(width, height, recipe, next) <= budget) {
                val edge = longest / next
                throw ExportMemoryException(edge.takeIf { it > 0 })
            }
        }
        throw ExportMemoryException(null)
    }

    fun sampleSize(longest: Int, maxEdge: Int): Int =
        ((longest.toLong() + maxEdge - 1L) / maxEdge).toInt().coerceAtLeast(1)

    fun estimate(width: Int, height: Int, recipe: Recipe, sample: Int = 1): Long {
        val sampledWidth = ceil(width.toDouble() / sample).toInt()
        val sampledHeight = ceil(height.toDouble() / sample).toInt()
        val sideways = Math.floorMod(recipe.quarterTurns, 2) != 0
        val crop = if (sideways) recipe.crop.pixels(sampledHeight, sampledWidth)
            else recipe.crop.pixels(sampledWidth, sampledHeight)

        // Source decoders can return 8-byte wide-gamut pixels. The render target uses ARGB_8888.
        val bytes = sampledWidth.toDouble() * sampledHeight * 8 +
            crop.width.toDouble() * crop.height * 4 + WorkspaceBytes
        return bytes.toLong()
    }
}
