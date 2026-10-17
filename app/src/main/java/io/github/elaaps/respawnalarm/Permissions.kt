package io.github.elaaps.respawnalarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.net.toUri

enum class Permission(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:DrawableRes val icon: Int,
) {
    NOTIFICATIONS(R.string.perm_notifications_title, R.string.perm_notifications_desc, R.drawable.ic_notifications),
    USAGE_ACCESS(R.string.perm_usage_title, R.string.perm_usage_desc, R.drawable.ic_query_stats),
    OVERLAY(R.string.perm_overlay_title, R.string.perm_overlay_desc, R.drawable.ic_layers),
    FULL_SCREEN(R.string.perm_full_screen_title, R.string.perm_full_screen_desc, R.drawable.ic_fullscreen),
    BATTERY(R.string.perm_battery_title, R.string.perm_battery_desc, R.drawable.ic_battery_alert),
    EXACT_ALARM(R.string.perm_exact_alarm_title, R.string.perm_exact_alarm_desc, R.drawable.ic_alarm),
}

object Permissions {
    fun isGranted(context: Context, permission: Permission): Boolean = when (permission) {
        Permission.NOTIFICATIONS ->
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        Permission.USAGE_ACCESS -> context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
        Permission.OVERLAY -> Settings.canDrawOverlays(context)
        Permission.FULL_SCREEN -> context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        Permission.BATTERY ->
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        Permission.EXACT_ALARM -> context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    @SuppressLint("BatteryLife")
    fun settingsIntent(context: Context, permission: Permission): Intent? {
        val pkg = "package:${context.packageName}".toUri()
        return when (permission) {
            Permission.NOTIFICATIONS -> null
            Permission.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg)
            Permission.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            Permission.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            Permission.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            Permission.EXACT_ALARM -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
        }
    }

    fun openSettings(context: Context, permission: Permission) {
        val intent = settingsIntent(context, permission) ?: return
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            ignoring<ActivityNotFoundException> { context.startActivity(Intent(intent.action)) }
        }
    }
}
