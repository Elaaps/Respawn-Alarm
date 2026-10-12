package io.github.elaaps.respawnalarm

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NextTriggerTest {
    @Test
    fun `nextTrigger, one-shot alarm later today, rings today`() {
        val now = at(day = 7, hour = 6, minute = 0)

        val next = nextTrigger(now, hour = 7, minute = 30, days = emptySet())

        assertEquals(at(day = 7, hour = 7, minute = 30), next)
    }

    @Test
    fun `nextTrigger, one-shot alarm already past today, rings tomorrow`() {
        val now = at(day = 7, hour = 8, minute = 0)

        val next = nextTrigger(now, hour = 7, minute = 30, days = emptySet())

        assertEquals(at(day = 8, hour = 7, minute = 30), next)
    }

    @Test
    fun `nextTrigger, at the exact alarm time, rings the next day`() {
        val now = at(day = 7, hour = 7, minute = 30)

        val next = nextTrigger(now, hour = 7, minute = 30, days = emptySet())

        assertEquals(at(day = 8, hour = 7, minute = 30), next)
    }

    @Test
    fun `nextTrigger, with unchecked days in between, rings on the next checked day`() {
        val now = at(day = 7, hour = 8, minute = 0)

        val next = nextTrigger(now, hour = 7, minute = 30, days = setOf(MONDAY, FRIDAY))

        assertEquals(at(day = 9, hour = 7, minute = 30), next)
    }

    @Test
    fun `nextTrigger, today checked but past, rings on the same day next week`() {
        val now = at(day = 7, hour = 8, minute = 0)

        val next = nextTrigger(now, hour = 7, minute = 30, days = setOf(WEDNESDAY))

        assertEquals(at(day = 14, hour = 7, minute = 30), next)
    }

    @Test
    fun `nextTrigger, today checked and still ahead, rings today`() {
        val now = at(day = 7, hour = 7, minute = 29)

        val next = nextTrigger(now, hour = 7, minute = 30, days = setOf(WEDNESDAY))

        assertEquals(at(day = 7, hour = 7, minute = 30), next)
    }

    @Test
    fun `nextTrigger, alarm time skipped by a DST change, rings after the gap`() {
        val now = ZonedDateTime.of(2027, 3, 27, 23, 0, 0, 0, PARIS)

        val next = nextTrigger(now, hour = 2, minute = 30, days = emptySet())

        assertEquals(ZonedDateTime.of(2027, 3, 28, 3, 30, 0, 0, PARIS), next)
    }

    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, PARIS)

    private companion object {
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    }
}
