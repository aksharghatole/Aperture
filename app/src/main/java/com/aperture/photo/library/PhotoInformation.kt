package com.aperture.photo.library

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.DateFormat
import java.util.Date

data class PhotoInformation(
    val dimensions: String,
    val fileSize: String,
    val format: String,
    val fileDate: String,
    val captureDate: String?,
    val exposure: String?,
    val iso: String?,
    val aperture: String?,
    val focalLength: String?,
    val orientation: String?
)

fun readPhotoInformation(file: File): PhotoInformation {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val exif = runCatching { ExifInterface(file) }.getOrNull()
    fun tag(name: String) = exif?.getAttribute(name)?.takeIf(String::isNotBlank)
    val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        ?.let { value ->
            when (value) {
                ExifInterface.ORIENTATION_NORMAL -> "Normal"
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Mirrored horizontally"
                ExifInterface.ORIENTATION_ROTATE_180 -> "Rotated 180°"
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Mirrored vertically"
                ExifInterface.ORIENTATION_TRANSPOSE -> "Transposed"
                ExifInterface.ORIENTATION_ROTATE_90 -> "Rotated 90°"
                ExifInterface.ORIENTATION_TRANSVERSE -> "Transverse"
                ExifInterface.ORIENTATION_ROTATE_270 -> "Rotated 270°"
                else -> null
            }
        }
    return PhotoInformation(
        dimensions = if (bounds.outWidth > 0 && bounds.outHeight > 0) "${bounds.outWidth} × ${bounds.outHeight}" else "Unavailable",
        fileSize = "${file.length()} bytes",
        format = bounds.outMimeType ?: "Unknown",
        fileDate = DateFormat.getDateTimeInstance().format(Date(file.lastModified())),
        captureDate = tag(ExifInterface.TAG_DATETIME_ORIGINAL),
        exposure = tag(ExifInterface.TAG_EXPOSURE_TIME)?.let { "$it s" },
        iso = tag("PhotographicSensitivity") ?: tag("ISOSpeedRatings"),
        aperture = tag(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" },
        focalLength = tag(ExifInterface.TAG_FOCAL_LENGTH)?.let { "$it mm" },
        orientation = orientation
    )
}
