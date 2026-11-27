package com.aperture.photo.library

internal object RecoveryPolicy {
    const val MaxAgeMillis = 24L * 60L * 60L * 1000L
    private const val Uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    private val capture = Regex("capture-[0-9]+\\.jpg")
    private val sidecar = Regex("([0-9]+-$Uuid" + "_[A-Z]+\\.photo)\\.edits(?:\\.new|\\.bak)?")
    private val export = Regex("Aperture-[0-9]+(?:-$Uuid)?\\.jpg")

    fun isCapture(name: String, modifiedMillis: Long, nowMillis: Long): Boolean =
        capture.matches(name) && isOld(modifiedMillis, nowMillis)

    fun orphanCandidate(name: String, modifiedMillis: Long, nowMillis: Long): String? =
        if (isOld(modifiedMillis, nowMillis)) sidecar.matchEntire(name)?.groupValues?.get(1) else null

    fun isPendingExport(
        name: String, path: String?, owner: String?, packageName: String,
        addedSeconds: Long, pending: Boolean, nowMillis: Long
    ): Boolean = pending && owner == packageName && path?.trimEnd('/') == "Pictures/Aperture" &&
        export.matches(name) && addedSeconds in 1..Long.MAX_VALUE / 1000 &&
        isOld(addedSeconds * 1000, nowMillis)

    private fun isOld(timestamp: Long, nowMillis: Long): Boolean =
        timestamp > 0 && nowMillis >= timestamp && nowMillis - timestamp > MaxAgeMillis
}
