package com.aperture.photo.camera

data class SelfTimerCountdown(val id: Int, val secondsRemaining: Int)

sealed interface SelfTimerStep {
    data class Continue(val countdown: SelfTimerCountdown) : SelfTimerStep
    data object Capture : SelfTimerStep
}

/** Pure countdown transitions so timer behavior can be tested without wall-clock sleeps. */
object SelfTimer {
    fun start(id: Int, seconds: Int): SelfTimerCountdown {
        require(seconds == 3 || seconds == 10) { "Self timer supports 3 or 10 seconds" }
        return SelfTimerCountdown(id, seconds)
    }

    fun advance(countdown: SelfTimerCountdown): SelfTimerStep {
        require(countdown.secondsRemaining in 1..10)
        return if (countdown.secondsRemaining == 1) SelfTimerStep.Capture
        else SelfTimerStep.Continue(countdown.copy(secondsRemaining = countdown.secondsRemaining - 1))
    }
}
