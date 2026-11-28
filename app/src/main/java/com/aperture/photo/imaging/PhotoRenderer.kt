package com.aperture.photo.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import java.io.File
import java.io.IOException
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
        decode(source, maxEdge) {}

    internal fun validateStillImport(file: File) {
        try {
            decode(ImageDecoder.createSource(file), 320, ::requireStillImage).recycle()
        } catch (failure: ImageInputException) {
            throw failure
        } catch (failure: IOException) {
            throw ImageInputException("Unsupported or damaged image. Try a still JPEG, PNG, or WebP.", failure)
        }
    }

    private fun requireStillImage(info: ImageDecoder.ImageInfo) {
        if (info.isAnimated) {
            throw ImageInputException("Animated images cannot be imported or exported. Choose a still image or extract a frame first.")
        }
    }

    internal fun flattenForJpeg(bitmap: Bitmap) {
        if (bitmap.hasAlpha()) Canvas(bitmap).drawColor(Color.WHITE, PorterDuff.Mode.DST_OVER)
    }

    internal fun decodeForExport(
        file: File, recipe: Recipe, maxEdge: Int?, memoryBudget: () -> Long = {
            val runtime = Runtime.getRuntime()
            ExportMemory.budget(runtime.maxMemory(), runtime.totalMemory() - runtime.freeMemory())
        }
    ): Bitmap = try {
        decode(ImageDecoder.createSource(file), maxEdge) { info ->
            requireStillImage(info)
            ExportMemory.check(info.size.width, info.size.height, recipe, maxEdge, memoryBudget())
        }
    } catch (failure: ImageDecoder.DecodeException) {
        throw ImageInputException("The original image could not be decoded. Try reimporting a valid still image.", failure)
    }

    private fun decode(
        source: ImageDecoder.Source, maxEdge: Int?, checkHeader: (ImageDecoder.ImageInfo) -> Unit
    ): Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        checkHeader(info)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        // Normalize embedded profiles to the editor and JPEG working space.
        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        if (maxEdge != null) {
            val longest = maxOf(info.size.width, info.size.height)
            decoder.setTargetSampleSize(ExportMemory.sampleSize(longest, maxEdge))
        }
    }

    fun render(source: Bitmap, recipe: Recipe): Bitmap {
        val turns = Math.floorMod(recipe.quarterTurns, 4)
        val sideways = turns % 2 != 0
        val width = if (sideways) source.height else source.width
        val height = if (sideways) source.width else source.height
        val crop = recipe.crop.pixels(width, height)
        val output = Bitmap.createBitmap(crop.width, crop.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.translate(-crop.left.toFloat(), -crop.top.toFloat())
        val transform = Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postRotate(turns * 90f)
            postTranslate(width / 2f, height / 2f)
        }
        canvas.drawBitmap(source, transform, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = colorFilter(recipe)
        })
        val tones = if (recipe.shadows != 0f || recipe.highlights != 0f) {
            ToneAdjustments(recipe.shadows, recipe.highlights)
        } else null
        val curves = if (recipe.curves != RgbCurves()) CurveLookup(recipe.curves) else null
        if (tones != null || curves != null) applyTonalEdits(output, tones, curves)
        if (recipe.vignette > 0f) {
            val alpha = (recipe.vignette.coerceIn(0f, 1f) * 220).toInt()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                shader = RadialGradient(
                    .5f, .5f, .707f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
                    floatArrayOf(0f, .43f, 1f), Shader.TileMode.CLAMP
                )
            }
            canvas.scale(width.toFloat(), height.toFloat())
            canvas.drawRect(0f, 0f, 1f, 1f, paint)
        }
        GrainEffect.apply(output, recipe.grainIntensity, recipe.grainSize)
        return output
    }

    private fun applyTonalEdits(bitmap: Bitmap, tones: ToneAdjustments?, curves: CurveLookup?) {
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                val toned = tones?.apply(row[x]) ?: row[x]
                row[x] = curves?.apply(toned) ?: toned
            }
            bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
    }
}
