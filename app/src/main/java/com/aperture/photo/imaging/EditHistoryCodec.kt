package com.aperture.photo.imaging

/** Versioned local format. Each line after the header represents one complete edit. */
internal object EditHistoryCodec {
    fun encode(history: EditHistory): String = buildString {
        append("1,").append(history.cursor)
        for (recipe in history.snapshots) {
            append('\n').append(recipe.film.name)
            append(',').append(recipe.exposure).append(',').append(recipe.contrast)
            append(',').append(recipe.saturation).append(',').append(recipe.warmth)
            append(',').append(recipe.vignette).append(',').append(recipe.quarterTurns)
        }
    }

    fun decode(encoded: String): EditHistory? {
        if (encoded.length > MaxEncodedSize) return null
        val lines = encoded.lines()
        val header = lines.first().split(',')
        if (header.size != 2 || header[0] != "1") return null
        val cursor = header[1].toIntOrNull() ?: return null
        val snapshots = lines.drop(1).map { decodeRecipe(it) ?: return null }
        return EditHistory.restore(snapshots, cursor)
    }

    private fun decodeRecipe(line: String): Recipe? {
        val parts = line.split(',')
        if (parts.size != 7) return null
        val film = Film.entries.firstOrNull { it.name == parts[0] } ?: return null
        val values = parts.subList(1, 6).map { it.toFloatOrNull() ?: return null }
        val (exposure, contrast, saturation, warmth, vignette) = values
        val turns = parts[6].toIntOrNull() ?: return null
        if (exposure !in -2f..2f || contrast !in -0.5f..0.5f || saturation !in -1f..1f ||
            warmth !in -1f..1f || vignette !in 0f..1f || turns !in 0..3) return null
        return Recipe(film, exposure, contrast, saturation, warmth, vignette, turns)
    }

    const val MaxEncodedSize = 8_192
}
