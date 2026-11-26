package com.aperture.photo

import com.aperture.photo.imaging.EditHistory
import com.aperture.photo.imaging.Recipe
import org.junit.Assert.*
import org.junit.Test

class EditHistoryTest {
    private val original = Recipe()

    @Test fun replaysEditsAndCanUndoReset() {
        val exposure = original.copy(exposure = .5f)
        val rotated = exposure.copy(quarterTurns = 1)
        val history = EditHistory(original).commit(exposure).commit(rotated).commit(original)
        assertEquals(original, history.current)
        assertEquals(rotated, history.undo().current)
        assertEquals(exposure, history.undo().undo().current)
        assertEquals(original, history.undo().redo().current)
    }

    @Test fun newEditAfterUndoDiscardsRedoButSameValuePreservesIt() {
        val exposure = original.copy(exposure = .5f)
        val undone = EditHistory(original).commit(exposure).undo()
        assertTrue(undone.commit(original).canRedo)
        val revised = undone.commit(original.copy(warmth = .5f))
        assertFalse(revised.canRedo)
        assertEquals(original, revised.undo().current)
    }

    @Test fun capKeepsLatestEditsAndCanRestoreCursor() {
        var history = EditHistory(original)
        repeat(80) { index ->
            history = history.commit(original.copy(exposure = (index + 1) / 50f))
        }
        assertEquals(40, history.snapshots.size)
        val undone = history.undo()
        val restored = EditHistory.restore(undone.snapshots, undone.cursor)!!
        assertEquals(undone.current, restored.current)
        assertEquals(history.current, restored.redo().current)
        assertNull(EditHistory.restore(undone.snapshots, 40))
    }
}
