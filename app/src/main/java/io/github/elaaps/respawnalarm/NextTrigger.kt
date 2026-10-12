package io.github.elaaps.respawnalarm

import java.time.DayOfWeek
import java.time.ZonedDateTime

fun nextTrigger(now: ZonedDateTime, hour: Int, minute: Int, days: Set<DayOfWeek>): ZonedDateTime {
    val today = now.toLocalDate()
    for (offset in 0L..7L) {
        val date = today.plusDays(offset)
        if (days.isNotEmpty() && date.dayOfWeek !in days) continue
        val candidate = date.atTime(hour, minute).atZone(now.zone)
        if (candidate.isAfter(now)) return candidate
    }
    error("No trigger found for $hour:$minute on $days")
}
