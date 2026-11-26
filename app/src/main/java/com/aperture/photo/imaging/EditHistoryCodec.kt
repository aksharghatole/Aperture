package com.aperture.photo.imaging

/** Versioned local format. Each line after the header represents one complete edit. */
internal object EditHistoryCodec {
    fun encode(history: EditHistory): String = buildString {
        append("3,").append(history.cursor)
        for (recipe in history.snapshots) {
            append('\n').append(recipe.film.name)
            append(',').append(recipe.exposure).append(',').append(recipe.contrast)
            append(',').append(recipe.saturation).append(',').append(recipe.warmth)
            append(',').append(recipe.vignette).append(',').append(recipe.quarterTurns)
            append(',').append(recipe.crop.left).append(',').append(recipe.crop.top)
            append(',').append(recipe.crop.right).append(',').append(recipe.crop.bottom)
            append(',').append(recipe.shadows).append(',').append(recipe.highlights)
        }
    }

    fun decode(encoded: String): EditHistory? {
        if (encoded.length > MaxEncodedSize) return null
        val lines = encoded.lines()
        val header = lines.first().split(',')
        if (header.size != 2) return null
        val fieldCount = when (header[0]) {
            "1" -> 7
            "2" -> 11
            "3" -> 13
            else -> return null
        }
        val cursor = header[1].toIntOrNull() ?: return null
        val snapshots = lines.drop(1).map { decodeRecipe(it, fieldCount) ?: return null }
        return EditHistory.restore(snapshots, cursor)
    }

    private fun decodeRecipe(line: String, fieldCount: Int): Recipe? {
        val parts = line.split(',')
        if (parts.size != fieldCount) return null
        val film = Film.entries.firstOrNull { it.name == parts[0] } ?: return null
        val values = parts.subList(1, 6).map { it.toFloatOrNull() ?: return null }
        val (exposure, contrast, saturation, warmth, vignette) = values
        val turns = parts[6].toIntOrNull() ?: return null
        if (exposure !in -2f..2f || contrast !in -0.5f..0.5f || saturation !in -1f..1f ||
            warmth !in -1f..1f || vignette !in 0f..1f || turns !in 0..3) return null
        val crop = if (fieldCount >= 11) {
            val positions = parts.subList(7, 11).map { it.toFloatOrNull() ?: return null }
            try {
                CropWindow(positions[0], positions[1], positions[2], positions[3])
            } catch (_: IllegalArgumentException) {
                return null
            }
        } else CropWindow.Full
        val shadows = if (fieldCount == 13) parts[11].toFloatOrNull() ?: return null else 0f
        val highlights = if (fieldCount == 13) parts[12].toFloatOrNull() ?: return null else 0f
        if (shadows !in -1f..1f || highlights !in -1f..1f) return null
        return Recipe(film, exposure, contrast, saturation, warmth, vignette, turns, crop, shadows, highlights)
    }

    const val MaxEncodedSize = 8_192
}
