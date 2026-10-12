package io.github.elaaps.respawnalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class CrescendoTest {
    @Test
    fun `crescendoLevel, when the alarm starts, is silent`() {
        val level = crescendoLevel(elapsedMs = 0)

        assertEquals(0f, level)
    }

    @Test
    fun `crescendoLevel, halfway through the fade-in, is at half volume`() {
        val level = crescendoLevel(elapsedMs = CRESCENDO_MS / 2)

        assertEquals(0.5f, level)
    }

    @Test
    fun `crescendoLevel, after the fade-in, stays at full volume`() {
        val level = crescendoLevel(elapsedMs = CRESCENDO_MS * 3)

        assertEquals(1f, level)
    }
}
