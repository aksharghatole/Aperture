package com.aperture.photo.camera

enum class FocusStatus { PENDING, SUCCESS, FAILED, METERING, METERED, UNSUPPORTED }

data class FocusFeedback(
    val x: Float,
    val y: Float,
    val status: FocusStatus,
    val requestId: Int,
    val autofocus: Boolean
)

/** Keeps asynchronous CameraX focus results attached to the latest visible tap. */
internal class FocusFeedbackTracker {
    private var requestId = 0
    private var active: FocusFeedback? = null

    fun request(x: Float, y: Float, autofocusSupported: Boolean, exposureMeteringSupported: Boolean): FocusFeedback {
        require(x in 0f..1f && y in 0f..1f)
        val autofocus = autofocusSupported
        val status = when {
            autofocus -> FocusStatus.PENDING
            exposureMeteringSupported -> FocusStatus.METERING
            else -> FocusStatus.UNSUPPORTED
        }
        return FocusFeedback(x, y, status, ++requestId, autofocus).also { active = it }
    }

    fun complete(id: Int, successful: Boolean): FocusFeedback? {
        val current = active?.takeIf {
            it.requestId == id && it.status in setOf(FocusStatus.PENDING, FocusStatus.METERING)
        } ?: return null
        val result = when {
            !successful -> FocusStatus.FAILED
            current.autofocus -> FocusStatus.SUCCESS
            else -> FocusStatus.METERED
        }
        return current.copy(status = result)
            .also { active = it }
    }

    fun clear() {
        requestId++
        active = null
    }
}
