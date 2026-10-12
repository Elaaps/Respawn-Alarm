package io.github.elaaps.respawnalarm

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class LaunchableApp(val packageName: String, val label: String, val icon: ImageBitmap)

fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(intent, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            LaunchableApp(
                packageName = it.activityInfo.packageName,
                label = it.loadLabel(pm).toString(),
                icon = it.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

fun appLabel(context: Context, packageName: String?): String {
    if (packageName == null) return context.getString(R.string.no_app)
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    return try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}

fun appIcon(context: Context, packageName: String?): ImageBitmap? {
    if (packageName == null) return null
    return try {
        context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

fun launchIntent(context: Context, packageName: String?): Intent? =
    packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }

private const val ICON_PX = 96
