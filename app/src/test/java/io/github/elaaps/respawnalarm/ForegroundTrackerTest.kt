package io.github.elaaps.respawnalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundTrackerTest {
    @Test
    fun `foregroundPackage, before any event, is unknown`() {
        val tracker = ForegroundTracker()

        val foreground = tracker.foregroundPackage

        assertNull(foreground)
    }

    @Test
    fun `onActivityResumed, after another app, makes the resumed app the foreground`() {
        val tracker = ForegroundTracker()
        tracker.onActivityResumed(LAUNCHER)

        tracker.onActivityResumed(GAME)

        assertEquals(GAME, tracker.foregroundPackage)
    }

    @Test
    fun `onActivityPaused, of the foreground app, leaves no foreground app`() {
        val tracker = ForegroundTracker()
        tracker.onActivityResumed(GAME)

        tracker.onActivityPaused(GAME)

        assertNull(tracker.foregroundPackage)
    }

    @Test
    fun `onActivityPaused, of an app already replaced, keeps the foreground app`() {
        val tracker = ForegroundTracker()
        tracker.onActivityResumed(GAME)

        tracker.onActivityPaused(LAUNCHER)

        assertEquals(GAME, tracker.foregroundPackage)
    }

    @Test
    fun `onScreenOff, while an app is in the foreground, leaves no foreground app`() {
        val tracker = ForegroundTracker()
        tracker.onActivityResumed(GAME)

        tracker.onScreenOff()

        assertNull(tracker.foregroundPackage)
    }

    private companion object {
        const val GAME = "com.geode.launcher"
        const val LAUNCHER = "com.sec.android.app.launcher"
    }
}
