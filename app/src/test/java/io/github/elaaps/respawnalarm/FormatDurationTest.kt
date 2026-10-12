package io.github.elaaps.respawnalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {
    @Test
    fun `formatDuration, whole minutes, shows zero seconds`() {
        val ms = 300_000L

        val text = formatDuration(ms)

        assertEquals("5:00", text)
    }

    @Test
    fun `formatDuration, a started second, rounds it up`() {
        val ms = 29_001L

        val text = formatDuration(ms)

        assertEquals("0:30", text)
    }

    @Test
    fun `formatDuration, nothing left, shows zero`() {
        val ms = 0L

        val text = formatDuration(ms)

        assertEquals("0:00", text)
    }

    @Test
    fun `formatDuration, an hour or more, keeps counting in minutes`() {
        val ms = 3_600_000L

        val text = formatDuration(ms)

        assertEquals("60:00", text)
    }
}
