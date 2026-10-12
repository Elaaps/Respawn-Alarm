package io.github.elaaps.respawnalarm

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatClockTime(context: Context, time: LocalTime): String {
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
    return time.format(DateTimeFormatter.ofPattern(pattern))
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
