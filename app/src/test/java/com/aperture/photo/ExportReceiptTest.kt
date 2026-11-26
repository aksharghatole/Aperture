package com.aperture.photo

import com.aperture.photo.imaging.ExportReceipt
import com.aperture.photo.imaging.Recipe
import org.junit.Assert.*
import org.junit.Test

class ExportReceiptTest {
    @Test fun outputMatchesOnlyTheRecipeCapturedWhenExportBegan() {
        val requested = Recipe(exposure = .5f)
        val editedDuringExport = requested.copy(exposure = 1f)
        val receipt = ExportReceipt(requested, "finished export")
        assertNull(receipt.matching(editedDuringExport))
        assertNull(receipt.matching(requested.copy(quarterTurns = 1)))
        assertEquals("finished export", receipt.matching(requested.copy()))
    }
}
