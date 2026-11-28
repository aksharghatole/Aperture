package com.aperture.photo

import com.aperture.photo.camera.Composition
import com.aperture.photo.camera.DetectedEyes
import com.aperture.photo.camera.FocusRegion
import com.aperture.photo.camera.PortraitFraming
import com.aperture.photo.camera.PortraitTracker
import com.aperture.photo.camera.Scene
import com.aperture.photo.camera.ShootingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitFramingTest {
    private val left = DetectedEyes(.96f, .20f, .31f, .06f, .085f)
    private val right = DetectedEyes(.9f, .43f, .35f, .07f, .10f)

    @Test fun retainsSingleFaceThresholdAndTwoFrameStability() {
        val tracker = PortraitTracker()
        val single = requireNotNull(PortraitFraming.validate(listOf(left.copy(confidence = .7f)), mirrored = true))
        assertNull(tracker.update(single))
        assertEquals(FocusRegion(.8f, .31f), tracker.update(single)?.eyes)
        assertNull(PortraitFraming.validate(listOf(left.copy(confidence = .5f)), mirrored = false))
        assertNull(PortraitFraming.validate(listOf(left.copy(x = .02f)), mirrored = false))
        assertNull(PortraitFraming.validate(listOf(left.copy(spanX = .01f)), mirrored = false))
    }

    @Test fun rejectsAmbiguousClippedLowConfidenceAndSaturatedDetections() {
        assertNull(PortraitFraming.validate(listOf(left, right.copy(confidence = .74f)), mirrored = false))
        assertNull(PortraitFraming.validate(listOf(left.copy(x = .075f), right), mirrored = false))
        assertNull(PortraitFraming.validate(listOf(left, right.copy(y = .9f)), mirrored = false))
        assertNull(PortraitFraming.validate(listOf(left, left.copy(x = .22f)), mirrored = false))
        val four = (1..4).map { left.copy(x = it / 5f) }
        assertNotNull(PortraitFraming.validate(four, mirrored = false))
        assertNull(PortraitFraming.validate(four + right, mirrored = false))
    }

    @Test fun derivesBoundsAndDirectionInTheVisiblePreview() {
        fun group(mirrored: Boolean) = requireNotNull(PortraitFraming.groupBounds(
            requireNotNull(PortraitFraming.validate(listOf(left, right), mirrored))
        ))
        val back = group(mirrored = false)
        val front = group(mirrored = true)
        assertEquals(.14f, back.left, .0001f)
        assertEquals(.50f, back.right, .0001f)
        assertEquals(.2335f, back.top, .0001f)
        assertEquals(.50f, back.bottom, .0001f)
        assertEquals(1f - back.right, front.left, .0001f)
        assertEquals(1f - back.eyeCenter.x, front.eyeCenter.x, .0001f)
        val scene = Scene(120, 0f, 0f, null)
        assertEquals("Aim a little left", Composition.guidance(scene.copy(portraitGroup = back), 0f, ShootingMode.PORTRAIT))
        assertEquals("Aim a little right", Composition.guidance(scene.copy(portraitGroup = front), 0f, ShootingMode.PORTRAIT))
        assertEquals("Place the eyes near the upper guide", Composition.guidance(scene, 0f, ShootingMode.PORTRAIT))
    }

    @Test fun waitsForThreeReadingsAcrossReorderingAndDropsCueOnCountChanges() {
        val tracker = PortraitTracker()
        val eyes = listOf(left, right)
        assertNull(tracker.update(eyes))
        assertNull(tracker.update(eyes.reversed()))
        assertNotNull(tracker.update(listOf(left.copy(x = .22f), right))?.group)
        assertNull(tracker.update(listOf(left)))
        assertNull(tracker.update(eyes))
        assertNull(tracker.update(eyes))
        assertNotNull(tracker.update(eyes)?.group)
        assertNull(tracker.update(null))
        assertNull(tracker.update(eyes))
    }

    @Test fun suppressesAmbiguousMatchesAndLargeSizeChangesWithTheSameCount() {
        val pair = listOf(left, left.copy(x = .36f))
        val tracker = PortraitTracker()
        repeat(3) { tracker.update(pair) }
        assertNull(tracker.update(listOf(left.copy(x = .28f), left.copy(x = .44f))))
        assertNull(tracker.update(pair))
        repeat(2) { tracker.update(pair) }
        assertNull(tracker.update(listOf(left.copy(spanX = .09f), pair[1])))
    }

    @Test fun tallGroupTargetKeepsEstimatedFrameMargins() {
        val eyes = listOf(left.copy(x = .35f, y = .29f, spanY = .2f),
            right.copy(x = .65f, y = .68f, spanY = .15f))
        val group = requireNotNull(PortraitFraming.groupBounds(eyes))
        val target = group.fitTargetY(1f / 3f)
        val newTop = group.top + target - group.eyeCenter.y
        assertTrue(newTop >= PortraitFraming.EdgeMargin - .0001f)
        assertEquals("Group detected · check framing",
            Composition.guidance(Scene(120, 0f, 0f, null, portraitGroup = group), 0f, ShootingMode.PORTRAIT))
    }
}
