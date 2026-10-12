package io.github.elaaps.respawnalarm

import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import org.junit.Assert.assertEquals
import org.junit.Test

class DayMaskTest {
    @Test
    fun `encodeDays, Monday and Sunday, sets the first and seventh bits`() {
        val days = setOf(MONDAY, SUNDAY)

        val mask = encodeDays(days)

        assertEquals(0b1000001, mask)
    }

    @Test
    fun `encodeDays, no day, gives zero`() {
        val days = emptySet<DayOfWeek>()

        val mask = encodeDays(days)

        assertEquals(0, mask)
    }

    @Test
    fun `decodeDays, an encoded set, gives the same days back`() {
        val days = setOf(MONDAY, WEDNESDAY, SUNDAY)

        val decoded = decodeDays(encodeDays(days))

        assertEquals(days, decoded)
    }

    @Test
    fun `decodeDays, bits beyond Sunday, ignores them`() {
        val mask = 0b10000001

        val days = decodeDays(mask)

        assertEquals(setOf(MONDAY), days)
    }
}
