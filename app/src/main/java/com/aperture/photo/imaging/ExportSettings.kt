package com.aperture.photo.imaging

import kotlin.math.ceil
import kotlin.math.roundToInt

/** The long edge limits the finished crop, not the original sensor dimensions. */
data class ExportSettings(val longEdge: Int? = null, val quality: Int = 96) {
    init {
        require(longEdge == null || longEdge > 0)
        require(quality in 1..100)
    }
}

internal data class ImageSize(val width: Int, val height: Int) {
    init { require(width > 0 && height > 0) }
    val longest: Int get() = maxOf(width, height)
    fun within(maxEdge: Int?): ImageSize {
        if (maxEdge == null || longest <= maxEdge) return this
        val ratio = maxEdge.toDouble() / longest
        return ImageSize((width * ratio).roundToInt().coerceAtLeast(1),
            (height * ratio).roundToInt().coerceAtLeast(1))
    }
}

/** Full-resolution crop geometry plus an exact output size and bounded decoder target. */
internal data class ExportPlan(
    val original: ImageSize,
    val rotated: ImageSize,
    val crop: CropPixels,
    val output: ImageSize,
    val decode: ImageSize
) {
    companion object {
        fun create(original: ImageSize, recipe: Recipe, maxEdge: Int?): ExportPlan {
            val rotated = if (Math.floorMod(recipe.quarterTurns, 2) == 0) original
                else ImageSize(original.height, original.width)
            val crop = recipe.crop.pixels(rotated.width, rotated.height)
            val full = ImageSize(crop.width, crop.height)
            val output = full.within(maxEdge)
            val ratio = output.longest.toDouble() / full.longest
            // Round decoder dimensions up: the output must never lose resolution because of
            // integer sampling steps on the uncropped source.
            val decode = ImageSize(
                ceil(original.width * ratio).toInt().coerceIn(1, original.width),
                ceil(original.height * ratio).toInt().coerceIn(1, original.height)
            )
            return ExportPlan(original, rotated, crop, output, decode)
        }
    }
}
