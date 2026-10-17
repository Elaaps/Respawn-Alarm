package io.github.elaaps.respawnalarm

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTest {
    @Test
    fun `constructor, with an hour out of the day, throws`() {
        val error = assertThrows(IllegalArgumentException::class.java) { Alarm(id = 1, hour = 24, minute = 0) }

        assertTrue(error.message.orEmpty().contains("hour"))
    }

    @Test
    fun `constructor, with a play time out of range, throws`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            Alarm(id = 1, hour = 7, minute = 0, durationSec = 5)
        }

        assertTrue(error.message.orEmpty().contains("durationSec"))
    }

    @Test
    fun `isOneShot, without any day, is true`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0)

        val oneShot = alarm.isOneShot

        assertTrue(oneShot)
    }

    @Test
    fun `isOneShot, with days, is false`() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, days = setOf(DayOfWeek.MONDAY))

        val oneShot = alarm.isOneShot

        assertFalse(oneShot)
    }

    @Test
    fun `resolve, for an alarm without settings of its own, takes every default`() {
        val defaults = GlobalSettings(
            targetPackage = "com.example.game",
            durationSec = 120,
            tone = Tone.Custom("content://tone"),
            vibrate = false,
            volume = VolumeMode.PHONE,
            snooze = Snooze(maxCount = 2, minutes = 3),
        )
        val alarm = Alarm(id = 4, hour = 7, minute = 0)

        val resolved = defaults.resolve(alarm)

        assertEquals(
            ResolvedAlarm(
                alarmId = 4,
                label = "",
                targetPackage = "com.example.game",
                durationSec = 120,
                inactivitySec = 10,
                soundEnabled = true,
                tone = Tone.Custom("content://tone"),
                vibrate = false,
                volume = VolumeMode.PHONE,
                crescendo = false,
                snooze = Snooze(maxCount = 2, minutes = 3),
                loadingGraceSec = 0,
            ),
            resolved,
        )
    }

    @Test
    fun `resolve, with the sound turned off by the alarm, vibrates even when vibration is off`() {
        val defaults = GlobalSettings(vibrate = false)
        val alarm = Alarm(id = 4, hour = 7, minute = 0, soundEnabled = false)

        val resolved = defaults.resolve(alarm)

        assertEquals(listOf(false, true), listOf(resolved.soundEnabled, resolved.vibrate))
    }

    @Test
    fun `resolveDefaults, with the sound turned off, vibrates even when vibration is off`() {
        val defaults = GlobalSettings(soundEnabled = false, vibrate = false)

        val resolved = defaults.resolveDefaults()

        assertTrue(resolved.vibrate)
    }

    @Test
    fun `resolve, for an alarm with settings of its own, keeps them over the defaults`() {
        val defaults = GlobalSettings(targetPackage = "com.example.game", durationSec = 120)
        val alarm = Alarm(
            id = 4,
            hour = 7,
            minute = 0,
            targetPackage = "com.example.other",
            durationSec = 600,
            tone = Tone.SystemDefault,
            vibrate = true,
            volume = VolumeMode.MAX,
            snooze = Snooze.OFF,
        )

        val resolved = defaults.copy(tone = Tone.Custom("content://tone"), vibrate = false).resolve(alarm)

        assertEquals(
            listOf("com.example.other", 600, Tone.SystemDefault, true, VolumeMode.MAX, Snooze.OFF),
            listOf(resolved.targetPackage, resolved.durationSec, resolved.tone, resolved.vibrate, resolved.volume, resolved.snooze),
        )
    }

    @Test
    fun `resolveDefaults, for a test ring, belongs to no alarm`() {
        val defaults = GlobalSettings(targetPackage = "com.example.game")

        val resolved = defaults.resolveDefaults()

        assertNull(resolved.alarmId)
    }

    @Test
    fun `resolve, with the loading time turned off, gives no quiet time`() {
        val defaults = GlobalSettings(loadingGraceEnabled = false, loadingGraceSec = 45)

        val resolved = defaults.resolve(Alarm(id = 1, hour = 7, minute = 0))

        assertEquals(0, resolved.loadingGraceSec)
    }

    @Test
    fun `resolve, with the loading time turned on, gives its length`() {
        val defaults = GlobalSettings(loadingGraceEnabled = true, loadingGraceSec = 45)

        val resolved = defaults.resolve(Alarm(id = 1, hour = 7, minute = 0))

        assertEquals(45, resolved.loadingGraceSec)
    }

    @Test
    fun `ownValue, equal to the default, follows the default`() {
        val stored = ownValue(value = 300, default = 300)

        assertNull(stored)
    }

    @Test
    fun `ownValue, different from the default, is kept`() {
        val stored = ownValue(value = Snooze(maxCount = 1, minutes = 5), default = Snooze.OFF)

        assertEquals(Snooze(maxCount = 1, minutes = 5), stored)
    }

    @Test
    fun `ownValue, without a default to follow, is kept`() {
        val stored = ownValue(value = Snooze.OFF, default = null)

        assertEquals(Snooze.OFF, stored)
    }

    @Test
    fun `snooze, turned off, allows no snooze`() {
        val snooze = Snooze(maxCount = 3, minutes = 5, enabled = false)

        val allowed = snooze.allowedCount

        assertEquals(0, allowed)
    }

    @Test
    fun `snooze, with more snoozes than allowed, throws`() {
        val error = assertThrows(IllegalArgumentException::class.java) { Snooze(maxCount = 9, minutes = 5) }

        assertTrue(error.message.orEmpty().contains("maxCount"))
    }
}
