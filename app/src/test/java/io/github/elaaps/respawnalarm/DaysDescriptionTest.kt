package io.github.elaaps.respawnalarm

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class DaysDescriptionTest {
    @Test
    fun `describeDays, without any day, is a one-shot alarm`() {
        val description = describeDays(emptySet())

        assertEquals(DaysDescription.Once, description)
    }

    @Test
    fun `describeDays, with all seven days, is every day`() {
        val description = describeDays(DayOfWeek.entries.toSet())

        assertEquals(DaysDescription.EveryDay, description)
    }

    @Test
    fun `describeDays, with Monday to Friday, is weekdays`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        val description = describeDays(days)

        assertEquals(DaysDescription.Weekdays, description)
    }

    @Test
    fun `describeDays, with Saturday and Sunday, is the weekend`() {
        val description = describeDays(setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY))

        assertEquals(DaysDescription.Weekend, description)
    }

    @Test
    fun `describeDays, with any other days, lists them Monday first`() {
        val description = describeDays(setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY))

        assertEquals(DaysDescription.Some(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)), description)
    }
}
