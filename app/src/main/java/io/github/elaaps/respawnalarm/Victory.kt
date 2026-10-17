package io.github.elaaps.respawnalarm

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

data class Victory(val upAt: Instant, val durationSec: Int, val resets: Int, val snoozes: Int) {
    val attempts: Int get() = resets + 1
}

fun winStreak(records: List<SessionRecord>, today: LocalDate, zone: ZoneId): Int {
    val winDays = records.filterNot { it.emergencyStop }.map { it.endedAt.atZone(zone).toLocalDate() }.toSet()
    var day = if (today in winDays) today else today.minusDays(1)
    var streak = 0
    while (day in winDays) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

sealed interface OverlayState {
    data object Asleep : OverlayState

    data class Fighting(val remainingMs: Long, val health: Float) : OverlayState

    data object KnockedOut : OverlayState
}

fun overlayState(phase: Session.Phase, remainingMs: Long, durationMs: Long): OverlayState = when (phase) {
    Session.Phase.RINGING, Session.Phase.LOADING, Session.Phase.SNOOZED -> OverlayState.Asleep
    Session.Phase.PLAYING -> OverlayState.Fighting(remainingMs, remainingMs.toFloat() / durationMs)
    Session.Phase.DONE -> OverlayState.KnockedOut
}

fun filledSegments(health: Float, segments: Int): Int = ceil(health * segments).toInt().coerceIn(0, segments)

const val HP_SEGMENTS = 10
