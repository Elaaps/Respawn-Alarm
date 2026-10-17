package io.github.elaaps.respawnalarm

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsJsonTest {
    @Test
    fun `decodeAlarms, of encoded alarms, gives them back unchanged`() {
        val alarms = listOf(
            Alarm(id = 1, hour = 7, minute = 30),
            Alarm(
                id = 2,
                hour = 9,
                minute = 5,
                days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                enabled = false,
                label = "Weekend",
                skippedDate = LocalDate.of(2026, 10, 10),
                targetPackage = "com.example.game",
                durationSec = 600,
                tone = Tone.Custom("content://media/tone/3"),
                vibrate = false,
                volume = VolumeMode.PHONE,
                snooze = Snooze(maxCount = 2, minutes = 10),
            ),
        )

        val decoded = decodeAlarms(encodeAlarms(alarms))

        assertEquals(alarms, decoded)
    }

    @Test
    fun `decodeAlarms, with an alarm out of the day, drops only that alarm`() {
        val json = """[{"id":1,"hour":7,"minute":0},{"id":2,"hour":25,"minute":0}]"""

        val decoded = decodeAlarms(json)

        assertEquals(listOf(Alarm(id = 1, hour = 7, minute = 0)), decoded)
    }

    @Test
    fun `decodeAlarms, with an alarm without id, drops it`() {
        val json = """[{"hour":7,"minute":0}]"""

        val decoded = decodeAlarms(json)

        assertEquals(emptyList<Alarm>(), decoded)
    }

    @Test
    fun `decodeAlarms, of text that is not an array, gives no alarm`() {
        val decoded = decodeAlarms("not json")

        assertEquals(emptyList<Alarm>(), decoded)
    }

    @Test
    fun `decodeAlarms, with an unreadable setting, lets the alarm use the default`() {
        val json = """[{"id":1,"hour":7,"minute":0,"duration_sec":1,"volume":"LOUD","skipped_date":"soon"}]"""

        val alarm = decodeAlarms(json).single()

        assertEquals(listOf(null, null, null), listOf(alarm.durationSec, alarm.volume, alarm.skippedDate))
    }

    @Test
    fun `decodeSettings, of encoded settings, gives them back unchanged`() {
        val settings = GlobalSettings(
            targetPackage = "com.example.game",
            durationSec = 90,
            inactivitySec = 15,
            tone = Tone.Custom("content://media/tone/3"),
            vibrate = false,
            volume = VolumeMode.PHONE,
            crescendo = true,
            snooze = Snooze(maxCount = 3, minutes = 7, enabled = false),
            loadingGraceEnabled = true,
            loadingGraceSec = 45,
            overlayEnabled = false,
            upcomingNoticeHours = 10,
        )

        val decoded = decodeSettings(encodeSettings(settings))

        assertEquals(settings, decoded)
    }

    @Test
    fun `decodeSettings, with a snooze saved as zero snoozes, turns snooze off and keeps its length`() {
        val json = """{"snooze":{"max_count":0,"minutes":10}}"""

        val decoded = decodeSettings(json)

        assertEquals(Snooze(maxCount = 1, minutes = 10, enabled = false), decoded.snooze)
    }

    @Test
    fun `decodeSettings, with a snooze saved without its switch, turns snooze on`() {
        val json = """{"snooze":{"max_count":2,"minutes":10}}"""

        val decoded = decodeSettings(json)

        assertEquals(Snooze(maxCount = 2, minutes = 10), decoded.snooze)
    }

    @Test
    fun `decodeSettings, with the upcoming notice turned off, keeps it off`() {
        val settings = GlobalSettings(upcomingNoticeHours = null)

        val decoded = decodeSettings(encodeSettings(settings))

        assertNull(decoded.upcomingNoticeHours)
    }

    @Test
    fun `decodeSettings, with a value out of range, uses its default`() {
        val json = """{"duration_sec":5,"inactivity_sec":12,"upcoming_notice_hours":99}"""

        val decoded = decodeSettings(json)

        assertEquals(
            GlobalSettings(inactivitySec = 12),
            decoded,
        )
    }

    @Test
    fun `decodeSettings, of text that is not an object, gives the defaults`() {
        val decoded = decodeSettings("[]")

        assertEquals(GlobalSettings(upcomingNoticeHours = GlobalSettings.DEFAULT_UPCOMING_NOTICE_HOURS), decoded)
    }
}
