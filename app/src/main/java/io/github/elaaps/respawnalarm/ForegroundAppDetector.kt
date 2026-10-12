package io.github.elaaps.respawnalarm

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

class ForegroundAppDetector(context: Context) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private var queriedUntil = System.currentTimeMillis() - INITIAL_LOOKBACK_MS

    private val tracker = ForegroundTracker()

    val foregroundPackage: String? get() = tracker.foregroundPackage

    fun poll(): String? {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(queriedUntil - OVERLAP_MS, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> tracker.onActivityResumed(event.packageName)
                UsageEvents.Event.ACTIVITY_PAUSED -> tracker.onActivityPaused(event.packageName)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> tracker.onScreenOff()
            }
        }
        queriedUntil = now
        return foregroundPackage
    }

    private companion object {
        const val INITIAL_LOOKBACK_MS = 6 * 60 * 60 * 1000L
        const val OVERLAP_MS = 2_000L
    }
}
