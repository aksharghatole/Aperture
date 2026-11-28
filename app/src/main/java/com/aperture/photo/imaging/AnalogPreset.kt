package com.aperture.photo.imaging

/** A reusable look, never a photo's framing or edit history. */
internal data class AnalogPreset(val name: String, val look: Recipe) {
    fun applyTo(photoRecipe: Recipe): Recipe =
        look.copy(crop = photoRecipe.crop, quarterTurns = photoRecipe.quarterTurns)

    companion object {
        fun fromRecipe(name: String, recipe: Recipe): AnalogPreset =
            AnalogPreset(name, recipe.copy(crop = CropWindow.Full, quarterTurns = 0))
    }
}
