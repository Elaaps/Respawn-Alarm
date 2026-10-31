package io.github.elaaps.respawnalarm

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyCodeTest {
    @Test
    fun `randomEmergencyCode, with any seed, gives 12 characters that cannot be mistaken for others`() {
        val code = randomEmergencyCode(Random(42))

        assertEquals(12, code.length)
        assertTrue(code.none { it in "01IO" })
    }

    @Test
    fun `randomEmergencyCode, called twice, gives two different codes`() {
        val random = Random(42)

        val first = randomEmergencyCode(random)
        val second = randomEmergencyCode(random)

        assertNotEquals(first, second)
    }

    @Test
    fun `groupEmergencyCode, of a 12 character code, splits it in groups of 4`() {
        val grouped = groupEmergencyCode("ABCDEFGHJKLM")

        assertEquals("ABCD EFGH JKLM", grouped)
    }

    @Test
    fun `matchesEmergencyCode, typed in lower case with spaces, matches`() {
        val matches = matchesEmergencyCode("ABCDEFGHJKLM", "abcd efgh jklm ")

        assertTrue(matches)
    }

    @Test
    fun `matchesEmergencyCode, with a wrong character, does not match`() {
        val matches = matchesEmergencyCode("ABCDEFGHJKLM", "ABCDEFGHJKLN")

        assertFalse(matches)
    }
}
