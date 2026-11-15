package com.asisten.bantu.voiceaccess

/**
 * Resolves a single word only when Android exposes a real text selection or caret.
 * Multi-word selections and missing selection metadata are rejected: they must
 * never become a paragraph-sized focus rectangle or an assumed first-word box.
 */
internal object FocusedWordRangeResolver {

    fun resolve(text: String, selectionStart: Int, selectionEnd: Int): IntRange? {
        if (text.isEmpty() || selectionStart < 0 || selectionEnd < 0) return null

        if (selectionStart != selectionEnd) {
            val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
            val end = maxOf(selectionStart, selectionEnd).coerceIn(start, text.length)
            if (end <= start) return null

            val selected = text.substring(start, end)
            val words = Regex("[\\p{L}\\p{N}]+").findAll(selected).toList()
            // A line/paragraph selection can contain many words. Refuse rather than
            // outline the entire selection or guess which word the user meant.
            if (words.size != 1) return null

            var wordStart = start + words.single().range.first
            var wordEnd = start + words.single().range.last + 1
            while (wordStart > 0 && text[wordStart - 1].isLetterOrDigit()) wordStart--
            while (wordEnd < text.length && text[wordEnd].isLetterOrDigit()) wordEnd++
            return if (wordEnd > wordStart) wordStart until wordEnd else null
        }

        // A collapsed selection is a caret. Resolve only the word touching that
        // actual caret position; never default a missing caret to character zero.
        val cursor = selectionEnd.coerceIn(0, text.length)
        val index = when {
            cursor < text.length && text[cursor].isLetterOrDigit() -> cursor
            cursor > 0 && text[cursor - 1].isLetterOrDigit() -> cursor - 1
            else -> return null
        }

        var wordStart = index
        var wordEnd = index + 1
        while (wordStart > 0 && text[wordStart - 1].isLetterOrDigit()) wordStart--
        while (wordEnd < text.length && text[wordEnd].isLetterOrDigit()) wordEnd++
        return if (wordEnd > wordStart) wordStart until wordEnd else null
    }
}
