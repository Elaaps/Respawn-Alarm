package io.github.elaaps.respawnalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayTimeStopsTest {
    @Test
    fun `nearestStopIndex, of a value on a stop, gives that stop`() {
        val index = nearestStopIndex(stops = listOf(30, 60, 120), value = 60)

        assertEquals(1, index)
    }

    @Test
    fun `nearestStopIndex, of a value between two stops, gives the closest one`() {
        val index = nearestStopIndex(stops = listOf(30, 60, 120), value = 100)

        assertEquals(2, index)
    }

    @Test
    fun `nearestStopIndex, of a value beyond the last stop, gives the last stop`() {
        val index = nearestStopIndex(stops = listOf(30, 60, 120), value = 3_600)

        assertEquals(2, index)
    }

    @Test
    fun `PLAY_TIME_STOPS_SEC, for every stop, stays within the allowed play time`() {
        val outside = GlobalSettings.PLAY_TIME_STOPS_SEC.filterNot { it in GlobalSettings.DURATION_RANGE_SEC }

        assertEquals(emptyList<Int>(), outside)
    }

    @Test
    fun `PLAY_TIME_STOPS_SEC, on the slider, puts the default play time in the middle`() {
        val stops = GlobalSettings.PLAY_TIME_STOPS_SEC

        val defaultIndex = stops.indexOf(GlobalSettings().durationSec)

        assertEquals(stops.lastIndex, defaultIndex * 2)
    }
}
