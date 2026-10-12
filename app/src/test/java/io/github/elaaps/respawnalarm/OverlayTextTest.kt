package io.github.elaaps.respawnalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayTextTest {
    @Test
    fun `overlayText, while playing, shows the time left`() {
        val text = overlayText(Session.Phase.PLAYING, remainingMs = 192_000)

        assertEquals("3:12", text)
    }

    @Test
    fun `overlayText, with the counter back at zero, shows a sleeping face`() {
        val text = overlayText(Session.Phase.RINGING, remainingMs = 300_000)

        assertEquals("💤", text)
    }

    @Test
    fun `overlayText, while the game loads, shows a sleeping face`() {
        val text = overlayText(Session.Phase.LOADING, remainingMs = 300_000)

        assertEquals("💤", text)
    }

    @Test
    fun `overlayText, while snoozed, shows a sleeping face`() {
        val text = overlayText(Session.Phase.SNOOZED, remainingMs = 300_000)

        assertEquals("💤", text)
    }

    @Test
    fun `overlayText, once done, shows a thumbs up`() {
        val text = overlayText(Session.Phase.DONE, remainingMs = 0)

        assertEquals("👍", text)
    }
}
