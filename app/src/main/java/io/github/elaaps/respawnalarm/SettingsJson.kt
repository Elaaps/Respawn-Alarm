package io.github.elaaps.respawnalarm

import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

fun encodeAlarms(alarms: List<Alarm>): String = JSONArray(alarms.map(::alarmToJson)).toString()

fun decodeAlarms(json: String): List<Alarm> {
    val array = try {
        JSONArray(json)
    } catch (_: JSONException) {
        return emptyList()
    }
    return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::alarmFromJson) }
}

fun encodeSettings(settings: GlobalSettings): String = JSONObject().apply {
    putOpt(KEY_TARGET, settings.targetPackage)
    put(KEY_DURATION, settings.durationSec)
    put(KEY_INACTIVITY, settings.inactivitySec)
    put(KEY_SOUND, settings.soundEnabled)
    put(KEY_TONE, toneToJson(settings.tone))
    put(KEY_VIBRATE, settings.vibrate)
    put(KEY_VOLUME, settings.volume.name)
    put(KEY_CRESCENDO, settings.crescendo)
    put(KEY_SNOOZE, snoozeToJson(settings.snooze))
    put(KEY_LOADING_GRACE_ENABLED, settings.loadingGraceEnabled)
    put(KEY_LOADING_GRACE, settings.loadingGraceSec)
    put(KEY_OVERLAY, settings.overlayEnabled)
    put(KEY_OVERLAY_STYLE, settings.overlayStyle.name)
    putOpt(KEY_UPCOMING, settings.upcomingNoticeHours)
}.toString()

fun decodeSettings(json: String): GlobalSettings {
    val defaults = GlobalSettings()
    val obj = try {
        JSONObject(json)
    } catch (_: JSONException) {
        return defaults
    }
    return GlobalSettings(
        targetPackage = obj.stringOrNull(KEY_TARGET),
        durationSec = obj.intOrNull(KEY_DURATION)?.takeIf { it in GlobalSettings.DURATION_RANGE_SEC } ?: defaults.durationSec,
        inactivitySec = obj.intOrNull(KEY_INACTIVITY)?.takeIf { it in GlobalSettings.INACTIVITY_RANGE_SEC }
            ?: defaults.inactivitySec,
        soundEnabled = obj.optBoolean(KEY_SOUND, defaults.soundEnabled),
        tone = obj.optJSONObject(KEY_TONE)?.let(::toneFromJson) ?: defaults.tone,
        vibrate = obj.optBoolean(KEY_VIBRATE, defaults.vibrate),
        volume = obj.volumeOrNull() ?: defaults.volume,
        crescendo = obj.optBoolean(KEY_CRESCENDO, defaults.crescendo),
        snooze = obj.optJSONObject(KEY_SNOOZE)?.let(::snoozeFromJson) ?: defaults.snooze,
        loadingGraceEnabled = obj.optBoolean(KEY_LOADING_GRACE_ENABLED, defaults.loadingGraceEnabled),
        loadingGraceSec = obj.intOrNull(KEY_LOADING_GRACE)?.takeIf { it in GlobalSettings.LOADING_GRACE_RANGE_SEC }
            ?: defaults.loadingGraceSec,
        overlayEnabled = obj.optBoolean(KEY_OVERLAY, defaults.overlayEnabled),
        overlayStyle = obj.stringOrNull(KEY_OVERLAY_STYLE)?.let { name -> OverlayStyle.entries.firstOrNull { it.name == name } }
            ?: defaults.overlayStyle,
        upcomingNoticeHours = if (obj.has(KEY_UPCOMING)) {
            obj.intOrNull(KEY_UPCOMING)?.takeIf { it in GlobalSettings.UPCOMING_NOTICE_RANGE_HOURS }
                ?: defaults.upcomingNoticeHours
        } else {
            null
        },
    )
}

private fun alarmToJson(alarm: Alarm): JSONObject = JSONObject().apply {
    put(KEY_ID, alarm.id)
    put(KEY_HOUR, alarm.hour)
    put(KEY_MINUTE, alarm.minute)
    put(KEY_DAYS, encodeDays(alarm.days))
    put(KEY_ENABLED, alarm.enabled)
    put(KEY_LABEL, alarm.label)
    putOpt(KEY_SKIPPED, alarm.skippedDate?.toString())
    putOpt(KEY_TARGET, alarm.targetPackage)
    putOpt(KEY_DURATION, alarm.durationSec)
    putOpt(KEY_SOUND, alarm.soundEnabled)
    putOpt(KEY_TONE, alarm.tone?.let(::toneToJson))
    putOpt(KEY_VIBRATE, alarm.vibrate)
    putOpt(KEY_VOLUME, alarm.volume?.name)
    putOpt(KEY_SNOOZE, alarm.snooze?.let(::snoozeToJson))
}

private fun alarmFromJson(obj: JSONObject): Alarm? = try {
    Alarm(
        id = obj.getLong(KEY_ID),
        hour = obj.getInt(KEY_HOUR),
        minute = obj.getInt(KEY_MINUTE),
        days = decodeDays(obj.optInt(KEY_DAYS, 0)),
        enabled = obj.optBoolean(KEY_ENABLED, true),
        label = obj.optString(KEY_LABEL, ""),
        skippedDate = obj.stringOrNull(KEY_SKIPPED)?.let(::dateOrNull),
        targetPackage = obj.stringOrNull(KEY_TARGET),
        durationSec = obj.intOrNull(KEY_DURATION)?.takeIf { it in GlobalSettings.DURATION_RANGE_SEC },
        soundEnabled = if (obj.isNull(KEY_SOUND)) null else obj.optBoolean(KEY_SOUND),
        tone = obj.optJSONObject(KEY_TONE)?.let(::toneFromJson),
        vibrate = if (obj.isNull(KEY_VIBRATE)) null else obj.optBoolean(KEY_VIBRATE),
        volume = obj.volumeOrNull(),
        snooze = obj.optJSONObject(KEY_SNOOZE)?.let(::snoozeFromJson),
    )
} catch (_: JSONException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private fun toneToJson(tone: Tone): JSONObject = when (tone) {
    Tone.SystemDefault -> JSONObject().put(KEY_TYPE, TONE_SYSTEM)
    is Tone.Custom -> JSONObject().put(KEY_TYPE, TONE_CUSTOM).put(KEY_URI, tone.uri)
}

private fun toneFromJson(obj: JSONObject): Tone? = when (obj.optString(KEY_TYPE)) {
    TONE_SYSTEM -> Tone.SystemDefault
    TONE_CUSTOM -> obj.stringOrNull(KEY_URI)?.let { Tone.Custom(it) }
    else -> null
}

private fun snoozeToJson(snooze: Snooze): JSONObject =
    JSONObject()
        .put(KEY_SNOOZE_COUNT, snooze.maxCount)
        .put(KEY_SNOOZE_MINUTES, snooze.minutes)
        .put(KEY_SNOOZE_ENABLED, snooze.enabled)

private fun snoozeFromJson(obj: JSONObject): Snooze? {
    val count = obj.intOrNull(KEY_SNOOZE_COUNT) ?: return null
    val minutes = obj.intOrNull(KEY_SNOOZE_MINUTES)?.takeIf { it in Snooze.MINUTES_RANGE } ?: return null
    if (count == LEGACY_SNOOZE_OFF_COUNT) return Snooze.OFF.copy(minutes = minutes)
    if (count !in Snooze.COUNT_RANGE) return null
    return Snooze(maxCount = count, minutes = minutes, enabled = obj.optBoolean(KEY_SNOOZE_ENABLED, true))
}

private fun dateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (_: DateTimeParseException) {
    null
}

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else (opt(key) as? Number)?.toInt()

private fun JSONObject.volumeOrNull(): VolumeMode? =
    stringOrNull(KEY_VOLUME)?.let { name -> VolumeMode.entries.firstOrNull { it.name == name } }

private const val KEY_ID = "id"
private const val KEY_HOUR = "hour"
private const val KEY_MINUTE = "minute"
private const val KEY_DAYS = "days"
private const val KEY_ENABLED = "enabled"
private const val KEY_LABEL = "label"
private const val KEY_SKIPPED = "skipped_date"
private const val KEY_TARGET = "target_package"
private const val KEY_DURATION = "duration_sec"
private const val KEY_INACTIVITY = "inactivity_sec"
private const val KEY_SOUND = "sound_enabled"
private const val KEY_TONE = "tone"
private const val KEY_TYPE = "type"
private const val KEY_URI = "uri"
private const val KEY_VIBRATE = "vibrate"
private const val KEY_VOLUME = "volume"
private const val KEY_CRESCENDO = "crescendo"
private const val KEY_SNOOZE = "snooze"
private const val KEY_SNOOZE_COUNT = "max_count"
private const val KEY_SNOOZE_MINUTES = "minutes"
private const val KEY_SNOOZE_ENABLED = "enabled"
private const val LEGACY_SNOOZE_OFF_COUNT = 0
private const val KEY_OVERLAY = "overlay"
private const val KEY_OVERLAY_STYLE = "overlay_style"
private const val KEY_LOADING_GRACE = "loading_grace_sec"
private const val KEY_LOADING_GRACE_ENABLED = "loading_grace_enabled"
private const val KEY_UPCOMING = "upcoming_notice_hours"
private const val TONE_SYSTEM = "system"
private const val TONE_CUSTOM = "custom"
