package io.github.elaaps.respawnalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamLevelTest {
    @Test
    fun `streamLevel, at full volume, gives the highest level`() {
        val level = streamLevel(percent = 100, min = 1, max = 7)

        assertEquals(7, level)
    }

    @Test
    fun `streamLevel, between two levels, gives the closest one`() {
        val level = streamLevel(percent = 50, min = 1, max = 15)

        assertEquals(8, level)
    }

    @Test
    fun `streamLevel, below the lowest level, stays on the lowest level`() {
        val level = streamLevel(percent = 10, min = 1, max = 7)

        assertEquals(1, level)
    }
}
