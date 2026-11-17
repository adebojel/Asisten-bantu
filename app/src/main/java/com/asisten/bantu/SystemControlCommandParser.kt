package com.asisten.bantu

import java.util.Locale

/**
 * Pure parser for explicit system-control values. It deliberately accepts only
 * a bounded percentage; relative commands continue through the existing volume path.
 */
internal object SystemControlCommandParser {
    private val volumePercentPattern = Regex(
        """(?:atur\s+)?(?:volume|suara)\s+(?:(?:ke|menjadi)\s+)?(100|[1-9]?\d)\s*(?:persen|%)""",
        RegexOption.IGNORE_CASE
    )

    fun parseVolumePercent(command: String?): Int? {
        if (command.isNullOrBlank()) return null
        val match = volumePercentPattern.find(command.lowercase(Locale.ROOT)) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..100 }
    }
}
