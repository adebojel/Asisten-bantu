package com.asisten.bantu.voiceaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusedWordRangeResolverTest {

    @Test
    fun selectedSingleWordReturnsOnlyWordCharacters() {
        assertEquals(5..12, FocusedWordRangeResolver.resolve("Open Settings now", 5, 13))
    }

    @Test
    fun multiWordLineSelectionIsRejected() {
        assertNull(FocusedWordRangeResolver.resolve("Open Settings now", 0, 17))
    }

    @Test
    fun selectionWithPunctuationStillReturnsTheSingleWord() {
        assertEquals(0..7, FocusedWordRangeResolver.resolve("Settings,", 0, 9))
    }

    @Test
    fun missingSelectionAndCaretDoNotAssumeFirstWord() {
        assertNull(FocusedWordRangeResolver.resolve("Open Settings now", -1, -1))
        assertNull(FocusedWordRangeResolver.resolve("Open Settings now", -1, 0))
    }

    @Test
    fun collapsedCaretResolvesTheWordTouchingItsRealPosition() {
        assertEquals(0..7, FocusedWordRangeResolver.resolve("Settings", 4, 4))
        assertEquals(0..7, FocusedWordRangeResolver.resolve("Settings", 8, 8))
        assertEquals(5..12, FocusedWordRangeResolver.resolve("Open Settings now", 5, 5))
    }

    @Test
    fun reversedSelectionStillResolvesTheSameSingleWord() {
        assertEquals(5..12, FocusedWordRangeResolver.resolve("Open Settings now", 13, 5))
    }

    @Test
    fun caretBetweenWordsDoesNotInventAnUnrelatedTarget() {
        assertNull(FocusedWordRangeResolver.resolve("Open  Settings", 5, 5))
    }
}
