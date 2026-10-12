package io.github.elaaps.respawnalarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

object AlarmScheduler {
    @SuppressLint("MissingPermission")
    fun schedule(context: Context, from: ZonedDateTime = ZonedDateTime.now()): ScheduledAlarm? {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val next = next(context, alarmManager, from)
        UpcomingNotice.update(context, next)
        if (next == null) {
            alarmManager.cancel(alarmIntent(context, alarmId = null))
            return null
        }
        val info = AlarmManager.AlarmClockInfo(next.at.toInstant().toEpochMilli(), showIntent(context))
        alarmManager.setAlarmClock(info, alarmIntent(context, next.alarm.id))
        return next
    }

    fun nextScheduled(context: Context): ScheduledAlarm? =
        next(context, context.getSystemService(AlarmManager::class.java), ZonedDateTime.now())

    private fun next(context: Context, alarmManager: AlarmManager, from: ZonedDateTime): ScheduledAlarm? {
        if (!alarmManager.canScheduleExactAlarms()) return null
        val store = AlarmStore(context)
        val settings = store.settings()
        val ringable = store.alarms().filter { settings.resolve(it).targetPackage != null }
        return nextAlarm(ringable, from)
    }

    private fun alarmIntent(context: Context, alarmId: Long?): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlarmReceiver::class.java).apply {
            if (alarmId != null) putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
