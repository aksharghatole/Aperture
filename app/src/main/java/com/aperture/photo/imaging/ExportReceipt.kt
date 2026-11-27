package com.aperture.photo.imaging

internal data class ExportReceipt<T>(private val recipe: Recipe, private val output: T) {
    fun matching(current: Recipe): T? = output.takeIf { current == recipe }
}
