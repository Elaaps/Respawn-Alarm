package io.github.elaaps.respawnalarm

import java.time.DayOfWeek

fun encodeDays(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or dayBit(day) }

fun decodeDays(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and dayBit(it) != 0 }.toSet()

private fun dayBit(day: DayOfWeek): Int = 1 shl day.ordinal
