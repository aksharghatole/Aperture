package com.aperture.photo.library

internal object FavoriteIndexCodec {
    fun encode(names: Set<String>): ByteArray = names.asSequence()
        .filter(::isSafeName).sorted().joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Set<String> = runCatching {
        bytes.toString(Charsets.UTF_8).lineSequence().filter(::isSafeName).toSet()
    }.getOrDefault(emptySet())

    private fun isSafeName(name: String) = name.isNotBlank() && name == name.substringAfterLast('/') &&
        name == name.substringAfterLast('\\') && name.endsWith(".photo")
}

internal fun filterFavoritePhotos(photos: List<Photo>, favoritesOnly: Boolean, favoriteNames: Set<String>): List<Photo> =
    if (favoritesOnly) photos.filter { it.file.name in favoriteNames } else photos
