package io.github.elaaps.respawnalarm

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver()

object ForceStopGuard {
    fun isActive(context: Context): Boolean = policyManager(context).isAdminActive(component(context))

    fun enableIntent(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.force_stop_guard_explanation))

    fun disable(context: Context) = policyManager(context).removeActiveAdmin(component(context))

    private fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

    private fun policyManager(context: Context) = context.getSystemService(DevicePolicyManager::class.java)
}
