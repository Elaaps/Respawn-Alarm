package io.github.elaaps.respawnalarm

import java.time.LocalDate
import java.time.ZonedDateTime

data class ScheduledAlarm(val alarm: Alarm, val at: ZonedDateTime)

fun nextRing(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
    if (!alarm.enabled) return null
    val next = nextTrigger(now, alarm.hour, alarm.minute, alarm.days)
    return if (next.toLocalDate() == alarm.skippedDate) nextTrigger(next, alarm.hour, alarm.minute, alarm.days) else next
}

fun nextAlarm(alarms: List<Alarm>, now: ZonedDateTime): ScheduledAlarm? = alarms
    .mapNotNull { alarm -> nextRing(alarm, now)?.let { ScheduledAlarm(alarm, it) } }
    .minWithOrNull(compareBy<ScheduledAlarm> { it.at }.thenBy { it.alarm.id })

fun afterRing(alarms: List<Alarm>, rangId: Long, today: LocalDate): List<Alarm> = alarms.map { alarm ->
    val enabled = alarm.enabled && !(alarm.id == rangId && alarm.isOneShot)
    val skipped = alarm.skippedDate?.takeIf { it >= today }
    alarm.copy(enabled = enabled, skippedDate = skipped)
}
