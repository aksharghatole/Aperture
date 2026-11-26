package com.aperture.photo.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import java.io.File
import kotlin.math.ceil
import kotlin.math.pow

object PhotoRenderer {
    fun colorFilter(recipe: Recipe): ColorMatrixColorFilter {
        val film = recipe.film
        val saturation = ColorMatrix().apply {
            setSaturation((film.saturation + recipe.saturation).coerceIn(0f, 2f))
        }
        val contrast = (film.contrast + recipe.contrast).coerceIn(.3f, 2f)
        val exposure = 2f.pow(recipe.exposure)
        val gain = contrast * exposure
        val offset = 128f * (1f - contrast) + film.fade * 255f
        val warmth = film.warmth + recipe.warmth
        saturation.postConcat(ColorMatrix(floatArrayOf(
            gain, 0f, 0f, 0f, offset + warmth * 23f,
            0f, gain, 0f, 0f, offset + warmth * 3f,
            0f, 0f, gain, 0f, offset - warmth * 25f,
            0f, 0f, 0f, 1f, 0f
        )))
        return ColorMatrixColorFilter(saturation)
    }

    fun decode(file: File, maxEdge: Int? = null): Bitmap =
        decode(ImageDecoder.createSource(file), maxEdge)

    fun decode(source: ImageDecoder.Source, maxEdge: Int? = null): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            if (maxEdge != null) {
                val longest = maxOf(info.size.width, info.size.height)
                decoder.setTargetSampleSize(ceil(longest.toDouble() / maxEdge).toInt().coerceAtLeast(1))
            }
        }

    fun render(source: Bitmap, recipe: Recipe): Bitmap {
        val turns = Math.floorMod(recipe.quarterTurns, 4)
        val sideways = turns % 2 != 0
        val width = if (sideways) source.height else source.width
        val height = if (sideways) source.width else source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val transform = Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postRotate(turns * 90f)
            postTranslate(width / 2f, height / 2f)
        }
        canvas.drawBitmap(source, transform, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = colorFilter(recipe)
        })
        if (recipe.vignette > 0f) {
            val alpha = (recipe.vignette.coerceIn(0f, 1f) * 220).toInt()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    .5f, .5f, .707f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
                    floatArrayOf(0f, .43f, 1f), Shader.TileMode.CLAMP
                )
            }
            canvas.scale(width.toFloat(), height.toFloat())
            canvas.drawRect(0f, 0f, 1f, 1f, paint)
        }
        return output
    }
}
