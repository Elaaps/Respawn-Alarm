package io.github.elaaps.respawnalarm

import io.github.elaaps.respawnalarm.Session.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionTest {
    @Test
    fun `tick, before any touch, keeps ringing`() {
        val session = session()

        val phase = session.tick(now = 100, targetInForeground = true, lastTouchAt = null)

        assertEquals(Phase.RINGING, phase)
    }

    @Test
    fun `tick, when touched outside the target app, keeps ringing`() {
        val session = session()

        val phase = session.tick(now = 200, targetInForeground = false, lastTouchAt = 150)

        assertEquals(Phase.RINGING, phase)
    }

    @Test
    fun `tick, when touched in the target app, starts playing`() {
        val session = session()

        val phase = session.tick(now = 300, targetInForeground = true, lastTouchAt = 250)

        assertEquals(Phase.PLAYING, phase)
    }

    @Test
    fun `tick, after the full play time, ends the session`() {
        val session = session()
        session.playContinuously(from = 0, until = 5_000)

        val phase = session.tick(now = 5_000, targetInForeground = true, lastTouchAt = 5_000)

        assertEquals(Phase.DONE, phase)
    }

    @Test
    fun `tick, when the target app leaves the foreground, resets play time and rings`() {
        val session = session()
        session.playContinuously(from = 0, until = 4_000)

        val phase = session.tick(now = 4_000, targetInForeground = false, lastTouchAt = 4_000)

        assertEquals(Phase.RINGING, phase)
        assertEquals(0, session.playedMs)
    }

    @Test
    fun `tick, within the inactivity limit, keeps playing`() {
        val session = session()
        session.playContinuously(from = 0, until = 3_000)

        val phase = session.tick(now = 3_400, targetInForeground = true, lastTouchAt = 2_500)

        assertEquals(Phase.PLAYING, phase)
    }

    @Test
    fun `tick, when no touch comes within the inactivity limit, resets play time and rings`() {
        val session = session()
        session.playContinuously(from = 0, until = 3_000)

        val phase = session.tick(now = 3_500, targetInForeground = true, lastTouchAt = 2_500)

        assertEquals(Phase.RINGING, phase)
        assertEquals(0, session.playedMs)
    }

    @Test
    fun `tick, after a reset, does not end before a full play time`() {
        val session = session()
        session.playContinuously(from = 0, until = 4_000)
        session.tick(now = 4_000, targetInForeground = false, lastTouchAt = 4_000)

        val phase = session.playContinuously(from = 4_100, until = 9_100)

        assertEquals(Phase.PLAYING, phase)
    }

    @Test
    fun `tick, after a late tick, counts one second of play at most`() {
        val session = session()
        session.tick(now = 0, targetInForeground = true, lastTouchAt = 0)
        session.tick(now = 100, targetInForeground = true, lastTouchAt = 100)

        session.tick(now = 60_100, targetInForeground = true, lastTouchAt = 60_050)

        assertEquals(1_100, session.playedMs)
    }

    @Test
    fun `tick, once done, stays done`() {
        val session = session()
        session.playContinuously(from = 0, until = 5_100)

        val phase = session.tick(now = 6_000, targetInForeground = false, lastTouchAt = null)

        assertEquals(Phase.DONE, phase)
    }

    @Test
    fun `constructor, with a zero play time, throws`() {
        val durationMs = 0L

        val create = { Session(durationMs = durationMs, inactivityMs = 1_000) }

        assertThrows(IllegalArgumentException::class.java) { create() }
    }

    @Test
    fun `constructor, with a zero inactivity limit, throws`() {
        val inactivityMs = 0L

        val create = { Session(durationMs = 5_000, inactivityMs = inactivityMs) }

        assertThrows(IllegalArgumentException::class.java) { create() }
    }

    @Test
    fun `snooze, while ringing, silences the alarm`() {
        val session = session(maxSnoozes = 1)

        session.snooze(now = 100)

        assertEquals(Phase.SNOOZED, session.phase)
    }

    @Test
    fun `tick, during a snooze without play, stays snoozed`() {
        val session = session(maxSnoozes = 1)
        session.snooze(now = 0)

        val phase = session.tick(now = 59_000, targetInForeground = false, lastTouchAt = null)

        assertEquals(Phase.SNOOZED, phase)
    }

    @Test
    fun `tick, at the end of a snooze, rings again`() {
        val session = session(maxSnoozes = 1)
        session.snooze(now = 0)

        val phase = session.tick(now = 60_000, targetInForeground = false, lastTouchAt = null)

        assertEquals(Phase.RINGING, phase)
    }

    @Test
    fun `tick, when the user plays during a snooze, starts playing`() {
        val session = session(maxSnoozes = 1)
        session.snooze(now = 0)

        val phase = session.tick(now = 10_000, targetInForeground = true, lastTouchAt = 9_900)

        assertEquals(Phase.PLAYING, phase)
    }

    @Test
    fun `canSnooze, once every snooze is used, is false`() {
        val session = session(maxSnoozes = 1)
        session.snooze(now = 0)
        session.tick(now = 60_000, targetInForeground = false, lastTouchAt = null)

        val canSnooze = session.canSnooze

        assertFalse(canSnooze)
    }

    @Test
    fun `canSnooze, while playing, is false`() {
        val session = session(maxSnoozes = 3)
        session.tick(now = 300, targetInForeground = true, lastTouchAt = 250)

        val canSnooze = session.canSnooze

        assertFalse(canSnooze)
    }

    @Test
    fun `snooze, without any snooze allowed, throws`() {
        val session = session()

        val snooze = { session.snooze(now = 0) }

        assertThrows(IllegalStateException::class.java) { snooze() }
    }

    @Test
    fun `tick, each time play stops before the end, counts a reset`() {
        val session = session()
        session.playContinuously(from = 0, until = 2_000)
        session.tick(now = 2_000, targetInForeground = false, lastTouchAt = 1_500)
        session.playContinuously(from = 3_000, until = 4_000)

        session.tick(now = 4_000, targetInForeground = false, lastTouchAt = 3_500)

        assertEquals(2, session.resets)
    }

    @Test
    fun `tick, when the target app opens without a touch, keeps quiet while it loads`() {
        val session = session(loadingGraceMs = 30_000)

        val phase = session.tick(now = 1_000, targetInForeground = true, lastTouchAt = null)

        assertEquals(Phase.LOADING, phase)
    }

    @Test
    fun `tick, at the first touch while loading, starts playing`() {
        val session = session(loadingGraceMs = 30_000)
        session.tick(now = 1_000, targetInForeground = true, lastTouchAt = null)

        val phase = session.tick(now = 12_000, targetInForeground = true, lastTouchAt = 11_900)

        assertEquals(Phase.PLAYING, phase)
    }

    @Test
    fun `tick, when loading takes longer than the grace, rings again`() {
        val session = session(loadingGraceMs = 30_000)
        session.tick(now = 1_000, targetInForeground = true, lastTouchAt = null)

        val phase = session.tick(now = 31_000, targetInForeground = true, lastTouchAt = null)

        assertEquals(Phase.RINGING, phase)
    }

    @Test
    fun `tick, when leaving the target app while it loads, rings again`() {
        val session = session(loadingGraceMs = 30_000)
        session.tick(now = 1_000, targetInForeground = true, lastTouchAt = null)

        val phase = session.tick(now = 5_000, targetInForeground = false, lastTouchAt = null)

        assertEquals(Phase.RINGING, phase)
    }

    @Test
    fun `tick, when the target app opens a second time, gives no new grace`() {
        val session = session(loadingGraceMs = 30_000)
        session.tick(now = 1_000, targetInForeground = true, lastTouchAt = null)
        session.tick(now = 5_000, targetInForeground = false, lastTouchAt = null)

        val phase = session.tick(now = 6_000, targetInForeground = true, lastTouchAt = null)

        assertEquals(Phase.RINGING, phase)
    }

    @Test
    fun `tick, without a loading grace, keeps ringing in the target app`() {
        val session = session()

        val phase = session.tick(now = 1_000, targetInForeground = true, lastTouchAt = null)

        assertEquals(Phase.RINGING, phase)
    }

    private fun session(maxSnoozes: Int = 0, loadingGraceMs: Long = 0) = Session(
        durationMs = 5_000,
        inactivityMs = 1_000,
        snoozeMs = 60_000,
        maxSnoozes = maxSnoozes,
        loadingGraceMs = loadingGraceMs,
    )

    private fun Session.playContinuously(from: Long, until: Long): Phase {
        var last = phase
        for (now in from until until step 100) {
            last = tick(now = now, targetInForeground = true, lastTouchAt = now - now % 500)
        }
        return last
    }
}
