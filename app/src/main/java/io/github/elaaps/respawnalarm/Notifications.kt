package io.github.elaaps.respawnalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalTime

class Notifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                // The service plays the sound itself, so that it can pause it while the game is played.
                setSound(null, null)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, context.getString(R.string.channel_info), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPCOMING,
                context.getString(R.string.channel_upcoming),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun ringing(target: String?, durationMs: Long, snoozesLeft: Int): Notification = alarmBuilder(target)
        .setContentTitle(context.getString(R.string.notif_ringing_title))
        .setContentText(context.getString(R.string.notif_ringing_text, formatDuration(durationMs), appLabel(context, target)))
        .setFullScreenIntent(ringingActivityIntent(), true)
        .addSnoozeAction(snoozesLeft)
        .build()

    fun reset(target: String?, snoozesLeft: Int): Notification = alarmBuilder(target)
        .setContentTitle(context.getString(R.string.notif_reset_title))
        .setContentText(context.getString(R.string.notif_reset_text, appLabel(context, target)))
        .addSnoozeAction(snoozesLeft)
        .build()

    fun snoozed(target: String?, until: LocalTime): Notification = alarmBuilder(target)
        .setContentTitle(context.getString(R.string.notif_snoozed_title))
        .setContentText(context.getString(R.string.notif_snoozed_text, formatClockTime(context, until), appLabel(context, target)))
        .build()

    fun playing(target: String?, remainingMs: Long, durationMs: Long, inactivitySec: Int): Notification = alarmBuilder(target)
        .setContentTitle(context.getString(R.string.notif_playing_title, formatDuration(remainingMs)))
        .setContentText(context.getString(R.string.notif_playing_text, appLabel(context, target), inactivitySec))
        .setProgress(durationMs.toInt(), (durationMs - remainingMs).toInt(), false)
        .build()

    fun diagnostic(foreground: String?, touchCount: Int?, remainingMs: Long): Notification {
        val stop = PendingIntent.getService(
            context,
            0,
            Intent(context, SessionService::class.java).setAction(SessionService.ACTION_STOP_DIAGNOSTIC),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val touches = touchCount?.let { context.getString(R.string.notif_diag_touches, it) }
            ?: context.getString(R.string.notif_diag_no_overlay)
        val app = foreground?.let { "${appLabel(context, it)} ($it)" } ?: context.getString(R.string.notif_diag_unknown)
        return Notification.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.notif_diag_title, formatDuration(remainingMs)))
            .setContentText(touches)
            .setStyle(Notification.BigTextStyle().bigText(touches + "\n" + context.getString(R.string.notif_diag_foreground, app)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_stop), stop).build())
            .build()
    }

    fun interrupted(target: String?) {
        val restart = PendingIntent.getActivity(
            context,
            REQUEST_RESTART,
            Intent(context, RingingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(RingingActivity.EXTRA_RESTART_ALARM, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = alarmBuilder(target)
            .setContentTitle(context.getString(R.string.notif_interrupted_title))
            .setContentText(context.getString(R.string.notif_interrupted_text))
            .setContentIntent(restart)
            .setFullScreenIntent(restart, true)
            .build()
        manager.notify(ID_SESSION, notification)
    }

    fun done(durationMs: Long) {
        val notification = Notification.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.notif_done_title))
            .setContentText(context.getString(R.string.notif_done_text, formatDuration(durationMs)))
            .setAutoCancel(true)
            .setTimeoutAfter(DONE_TIMEOUT_MS)
            .build()
        manager.notify(ID_DONE, notification)
    }

    fun emergencyStopped() {
        val notification = Notification.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.notif_emergency_title))
            .setAutoCancel(true)
            .setTimeoutAfter(DONE_TIMEOUT_MS)
            .build()
        manager.notify(ID_DONE, notification)
    }

    fun upcoming(next: ScheduledAlarm) {
        val ringAtMs = next.at.toInstant().toEpochMilli()
        val untilRingMs = ringAtMs - System.currentTimeMillis()
        if (untilRingMs <= 0) return
        val open = PendingIntent.getActivity(
            context,
            REQUEST_MAIN,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val skip = Notification.Action.Builder(null, context.getString(R.string.action_skip), UpcomingNotice.skipIntent(context, next))
        val notification = Notification.Builder(context, CHANNEL_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.notif_upcoming_title, formatClockTime(context, next.at.toLocalTime())))
            .setContentText(next.alarm.label.ifBlank { daysSummary(context, next.alarm.days) })
            .setWhen(ringAtMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(untilRingMs)
            .setContentIntent(open)
            .addAction(skip.build())
            .build()
        manager.notify(ID_UPCOMING, notification)
    }

    fun cancelUpcoming() = manager.cancel(ID_UPCOMING)

    fun update(notification: Notification) = manager.notify(ID_SESSION, notification)

    private fun alarmBuilder(target: String?): Notification.Builder {
        val builder = Notification.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(ringingActivityIntent())
        launchIntent(context, target)?.let {
            val launch = PendingIntent.getActivity(
                context,
                REQUEST_LAUNCH,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val label = context.getString(R.string.action_launch, appLabel(context, target))
            builder.addAction(Notification.Action.Builder(null, label, launch).build())
        }
        return builder
    }

    private fun Notification.Builder.addSnoozeAction(snoozesLeft: Int): Notification.Builder {
        if (snoozesLeft <= 0) return this
        val snooze = PendingIntent.getService(
            context,
            REQUEST_SNOOZE,
            Intent(context, SessionService::class.java).setAction(SessionService.ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = context.resources.getQuantityString(R.plurals.action_snooze, snoozesLeft, snoozesLeft)
        return addAction(Notification.Action.Builder(null, label, snooze).build())
    }

    private fun ringingActivityIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_RINGING,
        Intent(context, RingingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ID_SESSION = 1
        private const val ID_DONE = 2
        private const val ID_UPCOMING = 3
        private const val CHANNEL_UPCOMING = "upcoming"
        private const val REQUEST_MAIN = 5
        private const val CHANNEL_ALARM = "alarm"
        private const val CHANNEL_INFO = "info"
        private const val REQUEST_RINGING = 1
        private const val REQUEST_LAUNCH = 2
        private const val REQUEST_RESTART = 3
        private const val REQUEST_SNOOZE = 4
        private const val DONE_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
