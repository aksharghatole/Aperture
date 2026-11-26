package com.aperture.photo

import com.aperture.photo.camera.LumaFrame
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class LumaFrameTest {
    @Test fun offCenterCropFollowsAllFourViewfinderRotationsAndMirrors() {
        val expected = mapOf(
            0 to listOf(listOf(12, 13, 14), listOf(22, 23, 24)),
            90 to listOf(listOf(22, 12), listOf(23, 13), listOf(24, 14)),
            180 to listOf(listOf(24, 23, 22), listOf(14, 13, 12)),
            270 to listOf(listOf(14, 24), listOf(13, 23), listOf(12, 22))
        )
        for ((rotation, rows) in expected) {
            val frame = frame(rotation)
            assertTrue(frame.isValid)
            assertEquals(rows, samples(frame))
            assertEquals(rows.map { it.reversed() }, samples(frame, mirrored = true))
        }
    }

    @Test fun centerSamplingAgreesWithUprightCoordinateSpace() {
        assertEquals(23, frame(90).sample(0, 0, 1, 1))
        assertEquals(23, frame(270).sample(0, 0, 1, 1))
    }

    @Test fun rejectsTruncationAndInvalidMetadataBeforeReading() {
        assertFalse(frame(0, limit = 35).isValid)
        assertFalse(frame(45).isValid)
        val overlappingRows = LumaFrame(ByteBuffer.allocate(64), 6, 4, 8, 2, 2, 1, 5, 3, 0)
        assertFalse(overlappingRows.isValid)
        val outsideCrop = LumaFrame(ByteBuffer.allocate(100), 6, 4, 12, 2, 2, 1, 7, 3, 0)
        assertFalse(outsideCrop.isValid)
        assertThrows(IllegalStateException::class.java) { outsideCrop.sample(0, 0, 1, 1) }
    }

    private fun samples(frame: LumaFrame, mirrored: Boolean = false): List<List<Int>> =
        List(frame.height) { y -> List(frame.width) { x -> frame.sample(x, y, frame.width, frame.height, mirrored) } }

    private fun frame(rotation: Int, limit: Int = 65): LumaFrame {
        val buffer = ByteBuffer.allocate(65)
        for (y in 0 until 4) for (x in 0 until 6) buffer.put(3 + y * 15 + x * 2, (y * 10 + x).toByte())
        buffer.position(3)
        buffer.limit(limit)
        return LumaFrame(buffer, 6, 4, 15, 2, 2, 1, 5, 3, rotation)
    }
}
