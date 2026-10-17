package io.github.elaaps.respawnalarm

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatClockTime(context: Context, time: LocalTime): String {
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
    return time.format(DateTimeFormatter.ofPattern(pattern))
}

fun textLocale(context: Context): Locale = Locale.forLanguageTag(context.getString(R.string.text_locale))

@Composable
fun dateFormatter(@StringRes pattern: Int): DateTimeFormatter =
    DateTimeFormatter.ofPattern(stringResource(pattern), textLocale(LocalContext.current))

fun formatDuration(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
