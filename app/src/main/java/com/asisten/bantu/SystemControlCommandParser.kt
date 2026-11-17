package com.asisten.bantu

import java.util.Locale

/**
 * Pure parser for explicit system-control values. It deliberately accepts only
 * a bounded percentage command, not arbitrary text that happens to mention volume.
 */
internal object SystemControlCommandParser {
    private val volumePercentPattern = Regex(
        """^\s*(?:kobra\s+)?(?:tolong\s+)?(?:atur\s+)?(?:volume|suara)\s+(?:(?:ke|menjadi)\s+)?(100|[1-9]?\d)\s*(?:persen|%)\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parseVolumePercent(command: String?): Int? {
        if (command.isNullOrBlank()) return null
        val match = volumePercentPattern.matchEntire(command.trim().lowercase(Locale.ROOT)) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..100 }
    }
}
