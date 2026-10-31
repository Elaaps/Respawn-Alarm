package io.github.elaaps.respawnalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in ACTIONS) AlarmScheduler.schedule(context)
        // The system package installer denies full screen notifications on every sideloaded update.
        val fullScreenRevoked = !Permissions.isGranted(context, Permission.FULL_SCREEN)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED && fullScreenRevoked) Notifications(context).fullScreenRevoked()
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
