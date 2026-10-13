package io.github.elaaps.respawnalarm

import java.time.LocalDate
import java.time.LocalTime
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

fun afterRing(alarms: List<Alarm>, rangId: Long, now: ZonedDateTime): List<Alarm> {
    val rangAt = alarms.firstOrNull { it.id == rangId }?.let { now.with(LocalTime.of(it.hour, it.minute)) }
    val rang = alarms.filter { it.id == rangId || (rangAt != null && nextRing(it, rangAt.minusMinutes(1)) == rangAt) }
    return alarms.map { alarm ->
        val enabled = alarm.enabled && !(alarm in rang && alarm.isOneShot)
        val skipped = alarm.skippedDate?.takeIf { it >= now.toLocalDate() }
        alarm.copy(enabled = enabled, skippedDate = skipped)
    }
}
