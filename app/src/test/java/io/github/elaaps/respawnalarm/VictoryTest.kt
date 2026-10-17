package io.github.elaaps.respawnalarm

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class VictoryTest {
    private val zone = ZoneId.of("Europe/Paris")

    private fun record(endedAt: String, emergencyStop: Boolean = false) = SessionRecord(
        alarmId = 1,
        startedAt = Instant.parse(endedAt).minusSeconds(600),
        endedAt = Instant.parse(endedAt),
        resets = 0,
        snoozes = 0,
        emergencyStop = emergencyStop,
    )

    @Test
    fun `winStreak, with a win on each of the last three days, counts three`() {
        val records = listOf(record("2026-10-15T05:00:00Z"), record("2026-10-16T05:00:00Z"), record("2026-10-17T05:00:00Z"))

        val streak = winStreak(records, today = LocalDate.of(2026, 10, 17), zone = zone)

        assertEquals(3, streak)
    }

    @Test
    fun `winStreak, with a missed day, counts only the days after it`() {
        val records = listOf(record("2026-10-14T05:00:00Z"), record("2026-10-16T05:00:00Z"), record("2026-10-17T05:00:00Z"))

        val streak = winStreak(records, today = LocalDate.of(2026, 10, 17), zone = zone)

        assertEquals(2, streak)
    }

    @Test
    fun `winStreak, with an emergency stop, breaks the streak`() {
        val records = listOf(record("2026-10-15T05:00:00Z"), record("2026-10-16T05:00:00Z", emergencyStop = true), record("2026-10-17T05:00:00Z"))

        val streak = winStreak(records, today = LocalDate.of(2026, 10, 17), zone = zone)

        assertEquals(1, streak)
    }

    @Test
    fun `winStreak, before today's alarm, keeps the streak up to yesterday`() {
        val records = listOf(record("2026-10-15T05:00:00Z"), record("2026-10-16T05:00:00Z"))

        val streak = winStreak(records, today = LocalDate.of(2026, 10, 17), zone = zone)

        assertEquals(2, streak)
    }

    @Test
    fun `winStreak, with a win just after midnight local time, counts the local day`() {
        val records = listOf(record("2026-10-16T22:30:00Z"), record("2026-10-18T05:00:00Z"))

        val streak = winStreak(records, today = LocalDate.of(2026, 10, 18), zone = zone)

        assertEquals(2, streak)
    }

    @Test
    fun `attempts, with two resets, counts three attempts`() {
        val victory = Victory(upAt = Instant.EPOCH, durationSec = 300, resets = 2, snoozes = 0)

        val attempts = victory.attempts

        assertEquals(3, attempts)
    }

    @Test
    fun `overlayState, while playing, gives the time left and the health left`() {
        val state = overlayState(Session.Phase.PLAYING, remainingMs = 75_000, durationMs = 300_000)

        assertEquals(OverlayState.Fighting(remainingMs = 75_000, health = 0.25f), state)
    }

    @Test
    fun `overlayState, with the counter back at zero, sleeps`() {
        val state = overlayState(Session.Phase.RINGING, remainingMs = 300_000, durationMs = 300_000)

        assertEquals(OverlayState.Asleep, state)
    }

    @Test
    fun `overlayState, while the game loads, sleeps`() {
        val state = overlayState(Session.Phase.LOADING, remainingMs = 300_000, durationMs = 300_000)

        assertEquals(OverlayState.Asleep, state)
    }

    @Test
    fun `overlayState, while snoozed, sleeps`() {
        val state = overlayState(Session.Phase.SNOOZED, remainingMs = 300_000, durationMs = 300_000)

        assertEquals(OverlayState.Asleep, state)
    }

    @Test
    fun `overlayState, once done, is knocked out`() {
        val state = overlayState(Session.Phase.DONE, remainingMs = 0, durationMs = 300_000)

        assertEquals(OverlayState.KnockedOut, state)
    }

    @Test
    fun `filledSegments, with a sliver of health left, keeps one segment`() {
        val filled = filledSegments(health = 0.01f, segments = 10)

        assertEquals(1, filled)
    }

    @Test
    fun `filledSegments, with full health, fills every segment`() {
        val filled = filledSegments(health = 1f, segments = 10)

        assertEquals(10, filled)
    }

    @Test
    fun `filledSegments, with no health, fills none`() {
        val filled = filledSegments(health = 0f, segments = 10)

        assertEquals(0, filled)
    }
}
