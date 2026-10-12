package io.github.elaaps.respawnalarm

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeTimeTest {
    private val zone = ZoneId.of("Europe/Paris")

    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone)

    @Test
    fun `noticeTime, for an alarm far enough ahead, is the given hours before it`() {
        val notice = noticeTime(ringAt = at(day = 9, hour = 7), hoursBefore = 8, now = at(day = 8, hour = 18))

        assertEquals(at(day = 8, hour = 23), notice)
    }

    @Test
    fun `noticeTime, for an alarm closer than that, is now`() {
        val now = at(day = 9, hour = 1)

        val notice = noticeTime(ringAt = at(day = 9, hour = 7), hoursBefore = 8, now = now)

        assertEquals(now, notice)
    }
}
