package com.aperture.photo.imaging

import java.io.IOException
import kotlin.math.ceil

internal class ExportMemoryException(val suggestedMaxEdge: Int?) :
    IOException("Estimated image buffers exceed the available export budget")

internal object ExportMemory {
    private const val BaseWorkspaceBytes = 2L * 1024 * 1024
    private const val MaxWorkspaceBytes = 16L * 1024 * 1024

    fun budget(maxHeap: Long, usedHeap: Long): Long =
        minOf(maxHeap / 3L * 2L, (maxHeap - usedHeap).coerceAtLeast(0L))

    fun check(width: Int, height: Int, recipe: Recipe, maxEdge: Int?, budget: Long): ExportPlan {
        require(maxEdge == null || maxEdge > 0)
        val original = ImageSize(width, height)
        val requested = ExportPlan.create(original, recipe, maxEdge)
        if (estimate(requested) <= budget) return requested

        var edge = requested.output.longest
        while (edge > 1) {
            edge = (edge / 2).coerceAtLeast(1)
            if (estimate(ExportPlan.create(original, recipe, edge)) <= budget) throw ExportMemoryException(edge)
        }
        throw ExportMemoryException(null)
    }

    fun sampleSize(longest: Int, maxEdge: Int): Int =
        ((longest.toLong() + maxEdge - 1L) / maxEdge).toInt().coerceAtLeast(1)

    fun estimate(width: Int, height: Int, recipe: Recipe, sample: Int = 1): Long {
        val sampled = ImageSize(ceil(width.toDouble() / sample).toInt(), ceil(height.toDouble() / sample).toInt())
        return estimate(ExportPlan.create(sampled, recipe, null))
    }

    fun estimate(plan: ExportPlan): Long {
        // Decoders can return 8-byte wide-gamut pixels. The render target uses ARGB_8888.
        val sampledPixels = plan.decode.width.toDouble() * plan.decode.height
        val workspace = (BaseWorkspaceBytes + sampledPixels * 4).coerceAtMost(MaxWorkspaceBytes.toDouble())
        return (sampledPixels * 8 + plan.output.width.toDouble() * plan.output.height * 4 + workspace).toLong()
    }
}
