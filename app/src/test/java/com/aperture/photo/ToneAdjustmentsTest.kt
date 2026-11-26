package com.aperture.photo

import com.aperture.photo.imaging.ToneAdjustments
import org.junit.Assert.*
import org.junit.Test

class ToneAdjustmentsTest {
    @Test fun neutralSettingsAreIdentityAndAlphaIsPreserved() {
        val colored = 0x801134ab.toInt()
        assertEquals(colored, ToneAdjustments(0f, 0f).apply(colored))
        assertEquals(128, ToneAdjustments(1f, -1f).apply(colored) ushr 24)
        assertEquals(0x001134ab, ToneAdjustments(1f, -1f).apply(0x001134ab))
    }

    @Test fun shadowsAffectDarkTonesMoreAndHighlightsAffectBrightTonesMore() {
        val lift = ToneAdjustments(1f, 0f)
        val lower = ToneAdjustments(0f, -1f)
        assertTrue(level(lift, 24) > 24)
        assertTrue(level(lift, 24) - 24 > level(lift, 232) - 232)
        assertTrue(level(lower, 232) < 232)
        assertTrue(232 - level(lower, 232) > 24 - level(lower, 24))
        assertTrue(level(ToneAdjustments(-1f, 0f), 24) < 24)
        assertTrue(level(ToneAdjustments(0f, 1f), 232) > 232)
    }

    @Test fun extremeSettingsDoNotReverseGrayRamp() {
        for (shadows in listOf(-1f, 0f, 1f)) {
            for (highlights in listOf(-1f, 0f, 1f)) {
                val tones = ToneAdjustments(shadows, highlights)
                val ramp = (0..255).map { level(tones, it) }
                assertTrue(ramp.zipWithNext().all { (first, second) -> second >= first })
            }
        }
    }

    private fun level(tones: ToneAdjustments, gray: Int): Int =
        tones.apply(-0x1000000 or (gray * 0x010101)) and 255
}
