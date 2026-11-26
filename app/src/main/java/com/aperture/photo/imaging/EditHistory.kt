package com.aperture.photo.imaging

internal class EditHistory private constructor(val snapshots: List<Recipe>, val cursor: Int) {
    constructor(original: Recipe) : this(listOf(original), 0)

    val current: Recipe get() = snapshots[cursor]
    val canUndo: Boolean get() = cursor > 0
    val canRedo: Boolean get() = cursor < snapshots.lastIndex

    fun commit(recipe: Recipe): EditHistory {
        if (recipe == current) return this
        val next = (snapshots.take(cursor + 1) + recipe).takeLast(MaxSnapshots)
        return EditHistory(next, next.lastIndex)
    }

    fun undo(): EditHistory = if (canUndo) EditHistory(snapshots, cursor - 1) else this

    fun redo(): EditHistory = if (canRedo) EditHistory(snapshots, cursor + 1) else this

    companion object {
        private const val MaxSnapshots = 40

        fun restore(snapshots: List<Recipe>, cursor: Int): EditHistory? =
            if (snapshots.size <= MaxSnapshots && cursor in snapshots.indices) EditHistory(snapshots, cursor) else null
    }
}
