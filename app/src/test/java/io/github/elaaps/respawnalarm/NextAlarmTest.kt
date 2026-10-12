package io.github.elaaps.respawnalarm

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextAlarmTest {
    private val now = ZonedDateTime.of(2026, 10, 8, 22, 0, 0, 0, ZoneId.of("Europe/Paris"))

    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, now.zone)

    @Test
    fun `nextAlarm, among several alarms, picks the one that rings first`() {
        val alarms = listOf(
            Alarm(id = 1, hour = 8, minute = 0),
            Alarm(id = 2, hour = 6, minute = 30),
            Alarm(id = 3, hour = 23, minute = 0, days = setOf(DayOfWeek.SUNDAY)),
        )

        val next = nextAlarm(alarms, now)

        assertEquals(ScheduledAlarm(alarms[1], at(day = 9, hour = 6, minute = 30)), next)
    }

    @Test
    fun `nextAlarm, with two alarms at the same time, picks the oldest`() {
        val alarms = listOf(Alarm(id = 5, hour = 7, minute = 0), Alarm(id = 2, hour = 7, minute = 0))

        val next = nextAlarm(alarms, now)

        assertEquals(2L, next?.alarm?.id)
    }

    @Test
    fun `nextAlarm, with the only alarm off, gives nothing`() {
        val alarms = listOf(Alarm(id = 1, hour = 7, minute = 0, enabled = false))

        val next = nextAlarm(alarms, now)

        assertNull(next)
    }

    @Test
    fun `nextAlarm, with no alarm at all, gives nothing`() {
        val next = nextAlarm(emptyList(), now)

        assertNull(next)
    }

    @Test
    fun `nextRing, when the next ring is skipped, gives the one after`() {
        val alarm = Alarm(
            id = 1,
            hour = 7,
            minute = 0,
            days = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
            skippedDate = LocalDate.of(2026, 10, 9),
        )

        val next = nextRing(alarm, now)

        assertEquals(at(day = 12, hour = 7), next)
    }

    @Test
    fun `nextRing, with a skipped date already behind, ignores it`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, skippedDate = LocalDate.of(2026, 10, 1))

        val next = nextRing(alarm, now)

        assertEquals(at(day = 9, hour = 7), next)
    }

    @Test
    fun `afterRing, of a one-shot alarm, turns it off`() {
        val alarms = listOf(Alarm(id = 1, hour = 7, minute = 0))

        val updated = afterRing(alarms, rangId = 1, today = LocalDate.of(2026, 10, 9))

        assertEquals(false, updated.single().enabled)
    }

    @Test
    fun `afterRing, of a repeating alarm, keeps it on`() {
        val alarms = listOf(Alarm(id = 1, hour = 7, minute = 0, days = setOf(DayOfWeek.FRIDAY)))

        val updated = afterRing(alarms, rangId = 1, today = LocalDate.of(2026, 10, 9))

        assertEquals(true, updated.single().enabled)
    }

    @Test
    fun `afterRing, of another alarm, leaves a one-shot alarm on`() {
        val alarms = listOf(Alarm(id = 1, hour = 7, minute = 0), Alarm(id = 2, hour = 8, minute = 0))

        val updated = afterRing(alarms, rangId = 2, today = LocalDate.of(2026, 10, 9))

        assertEquals(true, updated.first().enabled)
    }

    @Test
    fun `afterRing, with a skipped date behind, forgets it and keeps the ones ahead`() {
        val alarms = listOf(
            Alarm(id = 1, hour = 7, minute = 0, days = setOf(DayOfWeek.FRIDAY), skippedDate = LocalDate.of(2026, 10, 2)),
            Alarm(id = 2, hour = 7, minute = 0, days = setOf(DayOfWeek.FRIDAY), skippedDate = LocalDate.of(2026, 10, 16)),
        )

        val updated = afterRing(alarms, rangId = 1, today = LocalDate.of(2026, 10, 9))

        assertEquals(listOf(null, LocalDate.of(2026, 10, 16)), updated.map { it.skippedDate })
    }
}
