package io.github.elaaps.respawnalarm

class Session(
    private val durationMs: Long,
    private val inactivityMs: Long,
    private val snoozeMs: Long = 0,
    maxSnoozes: Int = 0,
    private val loadingGraceMs: Long = 0,
    private val callHoldMs: Long = 0,
) {
    init {
        require(durationMs > 0) { "durationMs must be positive, was $durationMs" }
        require(inactivityMs > 0) { "inactivityMs must be positive, was $inactivityMs" }
        require(maxSnoozes >= 0) { "maxSnoozes must not be negative, was $maxSnoozes" }
        require(maxSnoozes == 0 || snoozeMs > 0) { "snoozeMs must be positive to snooze, was $snoozeMs" }
        require(loadingGraceMs >= 0) { "loadingGraceMs must not be negative, was $loadingGraceMs" }
        require(callHoldMs >= 0) { "callHoldMs must not be negative, was $callHoldMs" }
    }

    enum class Phase { RINGING, LOADING, PLAYING, SNOOZED, ON_CALL, DONE }

    var phase = Phase.RINGING
        private set

    var playedMs = 0L
        private set

    val remainingMs: Long get() = (durationMs - playedMs).coerceAtLeast(0)

    var resets = 0
        private set

    var snoozesLeft = maxSnoozes
        private set

    val canSnooze: Boolean get() = phase == Phase.RINGING && snoozesLeft > 0

    var snoozedUntil = 0L
        private set

    private var lastTickAt: Long? = null
    private var loadingGraceUsed = false
    private var loadingUntil = 0L
    private var callSince: Long? = null
    private var progressKept = false

    fun snooze(now: Long) {
        check(canSnooze) { "cannot snooze in phase $phase with $snoozesLeft snoozes left" }
        snoozesLeft--
        snoozedUntil = now + snoozeMs
        phase = Phase.SNOOZED
    }

    fun tick(now: Long, targetInForeground: Boolean, lastTouchAt: Long?, inCall: Boolean = false): Phase {
        val active = targetInForeground && lastTouchAt != null && now - lastTouchAt < inactivityMs
        val step = lastTickAt?.let { (now - it).coerceIn(0, MAX_STEP_MS) } ?: 0
        lastTickAt = now
        callSince = if (inCall) callSince ?: now else null
        val holdingCall = callSince?.let { now - it < callHoldMs } ?: false

        when (phase) {
            Phase.RINGING -> when {
                active -> startPlaying()
                holdingCall -> phase = Phase.ON_CALL
                targetInForeground && !loadingGraceUsed && loadingGraceMs > 0 -> {
                    loadingGraceUsed = true
                    loadingUntil = now + loadingGraceMs
                    phase = Phase.LOADING
                }
            }
            Phase.LOADING -> when {
                active -> startPlaying()
                holdingCall -> phase = Phase.ON_CALL
                !targetInForeground || now >= loadingUntil -> phase = Phase.RINGING
            }
            Phase.PLAYING -> when {
                active -> {
                    playedMs += step
                    if (playedMs >= durationMs) phase = Phase.DONE
                }
                holdingCall -> {
                    phase = Phase.ON_CALL
                    progressKept = true
                }
                else -> {
                    phase = Phase.RINGING
                    playedMs = 0
                    resets++
                }
            }
            Phase.SNOOZED -> when {
                active -> startPlaying()
                now >= snoozedUntil -> phase = if (holdingCall) Phase.ON_CALL else Phase.RINGING
            }
            Phase.ON_CALL -> when {
                active -> startPlaying()
                !holdingCall -> phase = Phase.RINGING
            }
            Phase.DONE -> Unit
        }
        return phase
    }

    private fun startPlaying() {
        if (!progressKept) playedMs = 0
        progressKept = false
        phase = Phase.PLAYING
    }

    private companion object {
        const val MAX_STEP_MS = 1_000L
    }
}
