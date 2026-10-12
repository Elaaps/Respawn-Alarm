package io.github.elaaps.respawnalarm

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLogTest {
    private fun record(alarmId: Long = 1) = SessionRecord(
        alarmId = alarmId,
        startedAt = Instant.parse("2026-10-09T05:00:00Z"),
        endedAt = Instant.parse("2026-10-09T05:07:30Z"),
        resets = 2,
        snoozes = 1,
        emergencyStop = false,
    )

    @Test
    fun `decodeRecords, of encoded records, gives them back unchanged`() {
        val records = listOf(record(alarmId = 1), record(alarmId = 2).copy(emergencyStop = true))

        val decoded = decodeRecords(encodeRecords(records))

        assertEquals(records, decoded)
    }

    @Test
    fun `encodeRecords, beyond the maximum, keeps only the latest`() {
        val records = (1L..(MAX_RECORDS + 5L)).map { record(alarmId = it) }

        val decoded = decodeRecords(encodeRecords(records))

        assertEquals(listOf(6L, MAX_RECORDS + 5L), listOf(decoded.first().alarmId, decoded.last().alarmId))
    }

    @Test
    fun `decodeRecords, with an unreadable record, skips only that one`() {
        val json = """[{"alarm_id":1,"started_at":"yesterday","ended_at":"today"},""" +
            """{"alarm_id":2,"started_at":"2026-10-09T05:00:00Z","ended_at":"2026-10-09T05:07:30Z"}]"""

        val decoded = decodeRecords(json)

        assertEquals(listOf(2L), decoded.map { it.alarmId })
    }
}
