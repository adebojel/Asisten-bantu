package com.asisten.bantu.voiceaccess

import java.util.Locale

/**
 * Pure, app-agnostic screen target matcher.
 *
 * Ranking prevents substring mistakes such as "call" matching "Recall".
 * Near-tied, spatially distinct controls are reported as ambiguous instead of
 * silently selecting the first node in accessibility-tree order.
 */
internal object TargetLabelMatcher {

    private const val SCORE_EXACT_TEXT = 100
    private const val SCORE_EXACT_ID = 90
    private const val SCORE_NORMALIZED = 80
    private const val SCORE_WORD_PREFIX = 60
    private const val SCORE_CONTAINS_TEXT = 40
    private const val SCORE_ID_CONTAINS = 30
    private const val MIN_SCORE = 30
    private const val AMBIGUITY_MARGIN = 10

    data class Candidate<T>(
        val value: T,
        val label: String,
        val resourceId: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val actionable: Boolean
    ) {
        val area: Long
            get() = (right - left).coerceAtLeast(0).toLong() *
                (bottom - top).coerceAtLeast(0).toLong()
    }

    sealed interface Result<out T> {
        data class Found<T>(val candidate: Candidate<T>) : Result<T>
        data class Ambiguous<T>(val candidates: List<Candidate<T>>) : Result<T>
        data object NotFound : Result<Nothing>
    }

    private data class Scored<T>(val candidate: Candidate<T>, val score: Int)

    fun <T> resolve(query: String, candidates: List<Candidate<T>>): Result<T> {
        val q = normalize(query)
        if (q.isEmpty()) return Result.NotFound

        val scored = candidates.mapNotNull { candidate ->
            score(q, candidate)?.let { Scored(candidate, it) }
        }.filter { it.score >= MIN_SCORE }
            .sortedWith(
                compareByDescending<Scored<T>> { it.score }
                    .thenByDescending { it.candidate.actionable }
                    .thenBy { it.candidate.area }
            )

        if (scored.isEmpty()) return Result.NotFound

        val best = scored.first()
        val nearTies = scored.drop(1).filter {
            best.score - it.score < AMBIGUITY_MARGIN
        }
        val distinctRivals = nearTies.filterNot {
            overlaps(best.candidate, it.candidate)
        }

        return if (distinctRivals.isNotEmpty()) {
            Result.Ambiguous(listOf(best.candidate) + distinctRivals.map { it.candidate })
        } else {
            Result.Found(best.candidate)
        }
    }

    private fun <T> score(query: String, candidate: Candidate<T>): Int? {
        val text = normalize(candidate.label)
        val id = normalize(candidate.resourceId.substringAfterLast('/').replace('_', ' '))

        return when {
            text.isNotEmpty() && text == query -> SCORE_EXACT_TEXT
            id.isNotEmpty() && candidate.actionable && id == query -> SCORE_EXACT_ID
            text.isNotEmpty() && strip(text) == strip(query) -> SCORE_NORMALIZED
            text.isNotEmpty() && startsWithWord(text, query) -> SCORE_WORD_PREFIX
            text.isNotEmpty() && containsWord(text, query) -> SCORE_CONTAINS_TEXT
            id.isNotEmpty() && candidate.actionable && containsWord(id, query) -> SCORE_ID_CONTAINS
            else -> null
        }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun strip(value: String): String =
        value.replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun startsWithWord(text: String, query: String): Boolean {
        if (text == query || text.startsWith("$query ")) return true
        val compactQuery = strip(query)
        if (compactQuery.isEmpty() || !strip(text).startsWith(compactQuery)) return false

        // Ignore separators such as the hyphen in "Wi-Fi", but require a real
        // letter/number boundary after the matched characters. This prevents
        // "panggil" from matching the longer word "panggilan".
        var consumed = 0
        var index = 0
        while (index < text.length && consumed < compactQuery.length) {
            if (text[index].isLetterOrDigit()) consumed++
            index++
        }
        return consumed == compactQuery.length &&
            (index >= text.length || !text[index].isLetterOrDigit())
    }

    private fun containsWord(text: String, query: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(query)}(?![\\p{L}\\p{N}])")
            .containsMatchIn(text)

    private fun <T> overlaps(a: Candidate<T>, b: Candidate<T>): Boolean =
        a.left < b.right && b.left < a.right &&
            a.top < b.bottom && b.top < a.bottom
}
