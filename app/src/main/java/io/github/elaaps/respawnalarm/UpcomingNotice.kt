package io.github.elaaps.respawnalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZonedDateTime

fun noticeTime(ringAt: ZonedDateTime, hoursBefore: Int, now: ZonedDateTime): ZonedDateTime {
    val noticeAt = ringAt.minusHours(hoursBefore.toLong())
    return if (noticeAt.isAfter(now)) noticeAt else now
}

object UpcomingNotice {
    fun update(context: Context, next: ScheduledAlarm?) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val notifications = Notifications(context)
        val hours = AlarmStore(context).settings().upcomingNoticeHours
        if (next == null || hours == null) {
            alarmManager.cancel(showIntent(context, next = null))
            notifications.cancelUpcoming()
            return
        }
        val now = ZonedDateTime.now()
        val at = noticeTime(next.at, hours, now)
        if (at == now) {
            alarmManager.cancel(showIntent(context, next = null))
            notifications.upcoming(next)
        } else {
            notifications.cancelUpcoming()
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), showIntent(context, next))
        }
    }

    private fun showIntent(context: Context, next: ScheduledAlarm?): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_SHOW,
        Intent(context, UpcomingReceiver::class.java).setAction(UpcomingReceiver.ACTION_SHOW).apply {
            if (next != null) putExtra(UpcomingReceiver.EXTRA_ALARM_ID, next.alarm.id)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun skipIntent(context: Context, next: ScheduledAlarm): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_SKIP,
        Intent(context, UpcomingReceiver::class.java)
            .setAction(UpcomingReceiver.ACTION_SKIP)
            .putExtra(UpcomingReceiver.EXTRA_ALARM_ID, next.alarm.id)
            .putExtra(UpcomingReceiver.EXTRA_DATE, next.at.toLocalDate().toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST_SHOW = 1
    private const val REQUEST_SKIP = 2
}

class UpcomingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHOW -> AlarmScheduler.nextScheduled(context)?.let { Notifications(context).upcoming(it) }
            ACTION_SKIP -> skip(context, intent)
        }
    }

    private fun skip(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, NO_ALARM).takeIf { it != NO_ALARM } ?: return
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return
        val store = AlarmStore(context)
        val alarm = store.alarm(alarmId) ?: return
        store.put(if (alarm.isOneShot) alarm.copy(enabled = false) else alarm.copy(skippedDate = date))
        Notifications(context).cancelUpcoming()
        AlarmScheduler.schedule(context)
    }

    companion object {
        const val ACTION_SHOW = "io.github.elaaps.respawnalarm.SHOW_UPCOMING"
        const val ACTION_SKIP = "io.github.elaaps.respawnalarm.SKIP_UPCOMING"
        const val EXTRA_ALARM_ID = "io.github.elaaps.respawnalarm.ALARM_ID"
        const val EXTRA_DATE = "io.github.elaaps.respawnalarm.DATE"
        private const val NO_ALARM = -1L
    }
}
