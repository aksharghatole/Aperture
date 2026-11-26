package com.aperture.photo.imaging

enum class Film(
    val title: String,
    val saturation: Float,
    val contrast: Float,
    val warmth: Float,
    val fade: Float,
    val vignette: Float
) {
    CLEAN("Clean", 1f, 1f, 0f, 0f, 0f),
    AMBER("Amber 200", .88f, 1.11f, .28f, .025f, .18f),
    CHROME("Chrome 64", 1.21f, 1.18f, -.17f, 0f, .12f),
    DUSK("Dusk 800", .74f, .91f, -.26f, .048f, .22f),
    SILVER("Silver 400", 0f, 1.28f, 0f, .02f, .21f)
}

data class Recipe(
    val film: Film = Film.CLEAN,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val vignette: Float = film.vignette,
    val quarterTurns: Int = 0,
    val crop: CropWindow = CropWindow.Full,
    val shadows: Float = 0f,
    val highlights: Float = 0f
)
