package io.github.elaaps.respawnalarm

import android.content.Context
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

sealed interface DaysDescription {
    data object Once : DaysDescription
    data object EveryDay : DaysDescription
    data object Weekdays : DaysDescription
    data object Weekend : DaysDescription

    data class Some(val days: List<DayOfWeek>) : DaysDescription
}

private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private val WEEKDAYS = DayOfWeek.entries.toSet() - WEEKEND

fun describeDays(days: Set<DayOfWeek>): DaysDescription = when (days) {
    emptySet<DayOfWeek>() -> DaysDescription.Once
    DayOfWeek.entries.toSet() -> DaysDescription.EveryDay
    WEEKDAYS -> DaysDescription.Weekdays
    WEEKEND -> DaysDescription.Weekend
    else -> DaysDescription.Some(days.sorted())
}

fun daysSummary(context: Context, days: Set<DayOfWeek>): String = when (val description = describeDays(days)) {
    DaysDescription.Once -> context.getString(R.string.days_once)
    DaysDescription.EveryDay -> context.getString(R.string.days_every_day)
    DaysDescription.Weekdays -> context.getString(R.string.days_weekdays)
    DaysDescription.Weekend -> context.getString(R.string.days_weekend)
    is DaysDescription.Some -> description.days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}
