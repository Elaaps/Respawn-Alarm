package io.github.elaaps.respawnalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import java.time.ZonedDateTime

enum class AlarmVolumeWarning {
    SILENT,

    LOW,
}

fun alarmVolumeWarning(alarm: ResolvedAlarm, level: Int, maxLevel: Int, muted: Boolean): AlarmVolumeWarning? = when {
    !alarm.soundEnabled || alarm.volume != AlarmVolume.Phone -> null
    muted || level == 0 -> AlarmVolumeWarning.SILENT
    level * AlarmVolume.Fixed.MAX_PERCENT <= maxLevel * LOW_VOLUME_PERCENT -> AlarmVolumeWarning.LOW
    else -> null
}

private const val LOW_VOLUME_PERCENT = 25

object AlarmVolumeCheck {
    fun update(context: Context, next: ScheduledAlarm?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (next == null) {
            alarmManager.cancel(checkIntent(context))
            Notifications(context).cancelAlarmVolumeWarning()
            return
        }
        check(context, next)
        val hours = AlarmStore(context).settings().upcomingNoticeHours ?: GlobalSettings.DEFAULT_UPCOMING_NOTICE_HOURS
        val now = ZonedDateTime.now()
        val at = noticeTime(next.at, hours, now)
        if (at == now) {
            alarmManager.cancel(checkIntent(context))
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), checkIntent(context))
        }
    }

    fun check(context: Context, next: ScheduledAlarm) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val stream = AudioManager.STREAM_ALARM
        val warning = alarmVolumeWarning(
            AlarmStore(context).settings().resolve(next.alarm),
            level = audioManager.getStreamVolume(stream),
            maxLevel = audioManager.getStreamMaxVolume(stream),
            muted = audioManager.isStreamMute(stream),
        )
        val notifications = Notifications(context)
        if (warning == null) notifications.cancelAlarmVolumeWarning() else notifications.alarmVolumeWarning(warning)
    }

    private fun checkIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CHECK,
        Intent(context, UpcomingReceiver::class.java).setAction(UpcomingReceiver.ACTION_CHECK_VOLUME),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST_CHECK = 3
}
