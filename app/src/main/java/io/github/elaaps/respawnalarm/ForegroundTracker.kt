package io.github.elaaps.respawnalarm

class ForegroundTracker {
    var foregroundPackage: String? = null
        private set

    fun onActivityResumed(packageName: String) {
        foregroundPackage = packageName
    }

    fun onActivityPaused(packageName: String) {
        if (packageName == foregroundPackage) foregroundPackage = null
    }

    fun onScreenOff() {
        foregroundPackage = null
    }
}
