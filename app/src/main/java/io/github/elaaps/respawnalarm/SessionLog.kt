package io.github.elaaps.respawnalarm

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class SessionRecord(
    val alarmId: Long,
    val startedAt: Instant,
    val endedAt: Instant,
    val resets: Int,
    val snoozes: Int,
    val emergencyStop: Boolean,
)

fun encodeRecords(records: List<SessionRecord>): String = JSONArray(
    records.takeLast(MAX_RECORDS).map { record ->
        JSONObject()
            .put(KEY_ALARM, record.alarmId)
            .put(KEY_STARTED, record.startedAt.toString())
            .put(KEY_ENDED, record.endedAt.toString())
            .put(KEY_RESETS, record.resets)
            .put(KEY_SNOOZES, record.snoozes)
            .put(KEY_EMERGENCY, record.emergencyStop)
    },
).toString()

fun decodeRecords(json: String): List<SessionRecord> {
    val array = try {
        JSONArray(json)
    } catch (_: JSONException) {
        return emptyList()
    }
    return (0 until array.length()).mapNotNull { index ->
        val obj = array.optJSONObject(index) ?: return@mapNotNull null
        try {
            SessionRecord(
                alarmId = obj.getLong(KEY_ALARM),
                startedAt = Instant.parse(obj.getString(KEY_STARTED)),
                endedAt = Instant.parse(obj.getString(KEY_ENDED)),
                resets = obj.optInt(KEY_RESETS, 0),
                snoozes = obj.optInt(KEY_SNOOZES, 0),
                emergencyStop = obj.optBoolean(KEY_EMERGENCY, false),
            )
        } catch (_: JSONException) {
            null
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

const val MAX_RECORDS = 365

class SessionLog(context: Context) {
    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences("session_log", Context.MODE_PRIVATE)

    fun records(): List<SessionRecord> = decodeRecords(prefs.getString(KEY_RECORDS, null) ?: return emptyList())

    fun add(record: SessionRecord) = prefs.edit { putString(KEY_RECORDS, encodeRecords(records() + record)) }
}

private const val KEY_RECORDS = "records"
private const val KEY_ALARM = "alarm_id"
private const val KEY_STARTED = "started_at"
private const val KEY_ENDED = "ended_at"
private const val KEY_RESETS = "resets"
private const val KEY_SNOOZES = "snoozes"
private const val KEY_EMERGENCY = "emergency_stop"
