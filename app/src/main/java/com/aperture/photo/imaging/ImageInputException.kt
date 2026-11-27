package com.aperture.photo.imaging

import java.io.IOException

internal class ImageInputException(message: String, cause: IOException? = null) : IOException(message, cause)
