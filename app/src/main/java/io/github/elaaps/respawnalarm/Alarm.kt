package io.github.elaaps.respawnalarm

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoField
import kotlin.time.Duration.Companion.minutes

fun <T> ownValue(value: T, default: T?): T? = if (value == default) null else value

sealed interface AlarmVolume {
    data object Phone : AlarmVolume

    data class Fixed(val percent: Int) : AlarmVolume {
        init {
            require(percent in PERCENT_RANGE) { "percent must be in $PERCENT_RANGE, was $percent" }
        }

        companion object {
            const val MAX_PERCENT = 100
            val PERCENT_RANGE = 10..MAX_PERCENT
            val PERCENT_STOPS = (PERCENT_RANGE step 10).toList()

            val MAX = Fixed(MAX_PERCENT)
        }
    }
}

enum class OverlayStyle {
    HEALTH_BAR,

    TIMER,
}

sealed interface Tone {
    data object SystemDefault : Tone

    data class Custom(val uri: String) : Tone
}

data class Snooze(val maxCount: Int, val minutes: Int, val enabled: Boolean = true) {
    init {
        require(maxCount in COUNT_RANGE) { "maxCount must be in $COUNT_RANGE, was $maxCount" }
        require(minutes in MINUTES_RANGE) { "minutes must be in $MINUTES_RANGE, was $minutes" }
    }

    val allowedCount: Int get() = if (enabled) maxCount else 0

    companion object {
        val COUNT_RANGE = 1..5
        val COUNT_CHOICES = COUNT_RANGE.toList()
        val MINUTES_RANGE = 1..30
        val MINUTES_CHOICES = listOf(3, 5, 10, 15, 20, 30)

        val OFF = Snooze(maxCount = 1, minutes = 5, enabled = false)
    }
}

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val label: String = "",
    val skippedDate: LocalDate? = null,
    val targetPackage: String? = null,
    val durationSec: Int? = null,
    val soundEnabled: Boolean? = null,
    val tone: Tone? = null,
    val vibrate: Boolean? = null,
    val volume: AlarmVolume? = null,
    val snooze: Snooze? = null,
) {
    init {
        require(isValidHour(hour)) { "hour must be in 0..23, was $hour" }
        require(isValidMinute(minute)) { "minute must be in 0..59, was $minute" }
        require(durationSec == null || durationSec in GlobalSettings.DURATION_RANGE_SEC) {
            "durationSec must be in ${GlobalSettings.DURATION_RANGE_SEC}, was $durationSec"
        }
    }

    val isOneShot: Boolean get() = days.isEmpty()

    companion object {
        fun isValidHour(hour: Int): Boolean = ChronoField.HOUR_OF_DAY.range().isValidIntValue(hour.toLong())

        fun isValidMinute(minute: Int): Boolean = ChronoField.MINUTE_OF_HOUR.range().isValidIntValue(minute.toLong())
    }
}

data class GlobalSettings(
    val targetPackage: String? = null,
    val durationSec: Int = 300,
    val inactivitySec: Int = 10,
    val soundEnabled: Boolean = true,
    val tone: Tone = Tone.SystemDefault,
    val vibrate: Boolean = true,
    val volume: AlarmVolume = AlarmVolume.Fixed.MAX,
    val crescendo: Boolean = false,
    val snooze: Snooze = Snooze.OFF,
    val loadingGraceEnabled: Boolean = false,
    val loadingGraceSec: Int = DEFAULT_LOADING_GRACE_SEC,
    val overlayEnabled: Boolean = true,
    val overlayStyle: OverlayStyle = OverlayStyle.HEALTH_BAR,
    val victoryOnLeave: Boolean = true,
    val emergencyStopEnabled: Boolean = true,
    val emergencyStopCode: Boolean = false,
    val emergencyStopDelayEnabled: Boolean = false,
    val emergencyStopDelayMin: Int = DEFAULT_EMERGENCY_STOP_DELAY_MIN,
    val upcomingNoticeHours: Int? = DEFAULT_UPCOMING_NOTICE_HOURS,
) {
    init {
        require(durationSec in DURATION_RANGE_SEC) { "durationSec must be in $DURATION_RANGE_SEC, was $durationSec" }
        require(inactivitySec in INACTIVITY_RANGE_SEC) {
            "inactivitySec must be in $INACTIVITY_RANGE_SEC, was $inactivitySec"
        }
        require(loadingGraceSec in LOADING_GRACE_RANGE_SEC) {
            "loadingGraceSec must be in $LOADING_GRACE_RANGE_SEC, was $loadingGraceSec"
        }
        require(emergencyStopDelayMin in EMERGENCY_STOP_DELAY_RANGE_MIN) {
            "emergencyStopDelayMin must be in $EMERGENCY_STOP_DELAY_RANGE_MIN, was $emergencyStopDelayMin"
        }
        require(upcomingNoticeHours == null || upcomingNoticeHours in UPCOMING_NOTICE_RANGE_HOURS) {
            "upcomingNoticeHours must be in $UPCOMING_NOTICE_RANGE_HOURS, was $upcomingNoticeHours"
        }
    }

    fun resolve(alarm: Alarm): ResolvedAlarm = ResolvedAlarm(
        alarmId = alarm.id,
        label = alarm.label,
        targetPackage = alarm.targetPackage ?: targetPackage,
        durationSec = alarm.durationSec ?: durationSec,
        inactivitySec = inactivitySec,
        soundEnabled = alarm.soundEnabled ?: soundEnabled,
        tone = alarm.tone ?: tone,
        vibrate = vibrates(alarm.vibrate ?: vibrate, alarm.soundEnabled ?: soundEnabled),
        volume = alarm.volume ?: volume,
        crescendo = crescendo,
        snooze = alarm.snooze ?: snooze,
        loadingGraceSec = effectiveLoadingGraceSec,
    )

    fun resolveDefaults(): ResolvedAlarm = ResolvedAlarm(
        alarmId = null,
        label = "",
        targetPackage = targetPackage,
        durationSec = durationSec,
        inactivitySec = inactivitySec,
        soundEnabled = soundEnabled,
        tone = tone,
        vibrate = vibrates(vibrate, soundEnabled),
        volume = volume,
        crescendo = crescendo,
        snooze = snooze,
        loadingGraceSec = effectiveLoadingGraceSec,
    )

    val emergencyStopDelayMs: Long get() = if (emergencyStopDelayEnabled) emergencyStopDelayMin.minutes.inWholeMilliseconds else 0

    private val effectiveLoadingGraceSec: Int get() = if (loadingGraceEnabled) loadingGraceSec else 0

    private fun vibrates(vibrate: Boolean, soundEnabled: Boolean): Boolean = vibrate || !soundEnabled

    companion object {
        val DURATION_RANGE_SEC = 30..1800
        val PLAY_TIME_STOPS_SEC = listOf(30, 60, 90, 120, 180, 240, 300, 420, 600, 900, 1200, 1500, 1800)

        val INACTIVITY_RANGE_SEC = 3..60
        val INACTIVITY_CHOICES_SEC = listOf(5, 10, 15, 20, 30, 60)

        val LOADING_GRACE_RANGE_SEC = 5..120
        val LOADING_GRACE_CHOICES_SEC = listOf(15, 30, 45, 60, 90, 120)
        const val DEFAULT_LOADING_GRACE_SEC = 30

        val EMERGENCY_STOP_DELAY_RANGE_MIN = 1..10
        val EMERGENCY_STOP_DELAY_CHOICES_MIN = listOf(1, 2, 3, 5, 7, 10)
        const val DEFAULT_EMERGENCY_STOP_DELAY_MIN = 5

        val UPCOMING_NOTICE_RANGE_HOURS = 1..16
        const val DEFAULT_UPCOMING_NOTICE_HOURS = 8
    }
}

data class ResolvedAlarm(
    val alarmId: Long?,
    val label: String,
    val targetPackage: String?,
    val durationSec: Int,
    val inactivitySec: Int,
    val soundEnabled: Boolean,
    val tone: Tone,
    val vibrate: Boolean,
    val volume: AlarmVolume,
    val crescendo: Boolean,
    val snooze: Snooze,
    val loadingGraceSec: Int,
) {
    val durationMs: Long get() = durationSec * MILLIS_PER_SECOND
    val loadingGraceMs: Long get() = loadingGraceSec * MILLIS_PER_SECOND
    val inactivityMs: Long get() = inactivitySec * MILLIS_PER_SECOND
    val snoozeMs: Long get() = snooze.minutes * MILLIS_PER_MINUTE

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
