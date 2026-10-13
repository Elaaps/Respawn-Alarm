package io.github.elaaps.respawnalarm

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.edit

private const val FIRST_ALARM_ID = 1L

class AlarmStore(context: Context) {
    private val appContext = context.applicationContext

    // Device protected: after a reboot, the alarm must be rescheduled and ring before the first unlock.
    private val prefs: SharedPreferences = appContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun alarms(): List<Alarm> = decodeAlarms(prefs.getString(KEY_ALARMS, null) ?: return emptyList())

    fun alarm(id: Long): Alarm? = alarms().firstOrNull { it.id == id }

    fun put(alarm: Alarm) {
        val alarms = alarms()
        val updated = if (alarms.any { it.id == alarm.id }) {
            alarms.map { if (it.id == alarm.id) alarm else it }
        } else {
            (alarms + alarm).sortedBy { it.id }
        }
        saveAlarms(updated)
    }

    fun saveAlarms(alarms: List<Alarm>) = prefs.edit { putString(KEY_ALARMS, encodeAlarms(alarms)) }

    fun delete(id: Long) = saveAlarms(alarms().filterNot { it.id == id })

    fun deleteAll() = saveAlarms(emptyList())

    fun newId(): Long = (alarms().maxOfOrNull { it.id } ?: (FIRST_ALARM_ID - 1)) + 1

    fun settings(): GlobalSettings = prefs.getString(KEY_SETTINGS, null)?.let(::decodeSettings) ?: GlobalSettings()

    fun saveSettings(settings: GlobalSettings) = prefs.edit { putString(KEY_SETTINGS, encodeSettings(settings)) }

    fun resolve(alarmId: Long?): ResolvedAlarm {
        val settings = settings()
        val alarm = alarmId?.let(::alarm) ?: return settings.resolveDefaults()
        return settings.resolve(alarm)
    }

    var ringingAlarmId: Long?
        get() = if (prefs.contains(KEY_RINGING_ALARM)) prefs.getLong(KEY_RINGING_ALARM, 0) else null
        set(value) = prefs.edit {
            if (value == null) remove(KEY_RINGING_ALARM) else putLong(KEY_RINGING_ALARM, value)
        }

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_DONE, value) }

    fun overlayPosition(): Pair<Int, Int>? {
        if (!prefs.contains(KEY_OVERLAY_X) || !prefs.contains(KEY_OVERLAY_Y)) return null
        return prefs.getInt(KEY_OVERLAY_X, 0) to prefs.getInt(KEY_OVERLAY_Y, 0)
    }

    fun saveOverlayPosition(x: Int, y: Int) = prefs.edit {
        putInt(KEY_OVERLAY_X, x)
        putInt(KEY_OVERLAY_Y, y)
    }

    fun resetOverlayPosition() = prefs.edit {
        remove(KEY_OVERLAY_X)
        remove(KEY_OVERLAY_Y)
    }

    fun suggestedTarget(): String? = DEFAULT_TARGETS.firstOrNull { isInstalled(it) }

    private fun isInstalled(packageName: String): Boolean = try {
        appContext.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        val DEFAULT_TARGETS = listOf("com.geode.launcher", "com.robtopx.geometryjump")

        const val KEY_ALARMS = "alarms"
        const val KEY_SETTINGS = "global_settings"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_RINGING_ALARM = "ringing_alarm_id"
        const val KEY_OVERLAY_X = "overlay_x"
        const val KEY_OVERLAY_Y = "overlay_y"
    }
}
