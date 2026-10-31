package io.github.elaaps.respawnalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmVolumeWarningTest {
    private fun alarm(volume: AlarmVolume = AlarmVolume.Phone, soundEnabled: Boolean = true): ResolvedAlarm =
        GlobalSettings(volume = volume, soundEnabled = soundEnabled).resolveDefaults()

    @Test
    fun `alarmVolumeWarning, with the stream muted, warns of a silent alarm`() {
        val warning = alarmVolumeWarning(alarm(), level = 5, maxLevel = 15, muted = true)

        assertEquals(AlarmVolumeWarning.SILENT, warning)
    }

    @Test
    fun `alarmVolumeWarning, with the volume at zero, warns of a silent alarm`() {
        val warning = alarmVolumeWarning(alarm(), level = 0, maxLevel = 15, muted = false)

        assertEquals(AlarmVolumeWarning.SILENT, warning)
    }

    @Test
    fun `alarmVolumeWarning, with the volume on the threshold, warns of a low volume`() {
        val warning = alarmVolumeWarning(alarm(), level = 1, maxLevel = 4, muted = false)

        assertEquals(AlarmVolumeWarning.LOW, warning)
    }

    @Test
    fun `alarmVolumeWarning, with the lowest of seven levels, warns of a low volume`() {
        val warning = alarmVolumeWarning(alarm(), level = 1, maxLevel = 7, muted = false)

        assertEquals(AlarmVolumeWarning.LOW, warning)
    }

    @Test
    fun `alarmVolumeWarning, with the volume just above the threshold, gives no warning`() {
        val warning = alarmVolumeWarning(alarm(), level = 2, maxLevel = 7, muted = false)

        assertNull(warning)
    }

    @Test
    fun `alarmVolumeWarning, with a custom volume, gives no warning`() {
        val warning = alarmVolumeWarning(alarm(volume = AlarmVolume.Fixed(percent = 50)), level = 0, maxLevel = 15, muted = true)

        assertNull(warning)
    }

    @Test
    fun `alarmVolumeWarning, with the sound turned off, gives no warning`() {
        val warning = alarmVolumeWarning(alarm(soundEnabled = false), level = 0, maxLevel = 15, muted = true)

        assertNull(warning)
    }
}
