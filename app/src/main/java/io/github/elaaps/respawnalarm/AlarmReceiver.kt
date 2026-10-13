package io.github.elaaps.respawnalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, NO_ALARM).takeIf { it != NO_ALARM }
        SessionService.start(context, SessionService.Mode.ALARM, alarmId)

        if (alarmId != null) {
            val store = AlarmStore(context)
            store.saveAlarms(afterRing(store.alarms(), alarmId, ZonedDateTime.now()))
        }
        AlarmScheduler.schedule(context, from = ZonedDateTime.now().plusMinutes(1))
    }

    companion object {
        const val EXTRA_ALARM_ID = "io.github.elaaps.respawnalarm.ALARM_ID"
        private const val NO_ALARM = -1L
    }
}
