package io.github.elaaps.respawnalarm

import kotlin.random.Random

fun randomEmergencyCode(random: Random = Random.Default): String =
    String(CharArray(EMERGENCY_CODE_LENGTH) { EMERGENCY_CODE_ALPHABET.random(random) })

fun groupEmergencyCode(code: String): String = code.chunked(EMERGENCY_CODE_GROUP_LENGTH).joinToString(" ")

fun matchesEmergencyCode(code: String, typed: String): Boolean =
    typed.filterNot { it.isWhitespace() }.equals(code, ignoreCase = true)

private const val EMERGENCY_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val EMERGENCY_CODE_LENGTH = 12
private const val EMERGENCY_CODE_GROUP_LENGTH = 4
