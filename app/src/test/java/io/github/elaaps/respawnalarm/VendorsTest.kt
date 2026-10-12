package io.github.elaaps.respawnalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VendorsTest {
    @Test
    fun `backgroundGuideUrl, for a maker that kills apps, points to its page`() {
        val url = backgroundGuideUrl("Xiaomi")

        assertEquals("https://dontkillmyapp.com/xiaomi", url)
    }

    @Test
    fun `backgroundGuideUrl, for a sub-brand, points to its parent's page`() {
        val url = backgroundGuideUrl("HONOR")

        assertEquals("https://dontkillmyapp.com/huawei", url)
    }

    @Test
    fun `backgroundGuideUrl, for Google, needs no guide`() {
        val url = backgroundGuideUrl("Google")

        assertNull(url)
    }
}
