package com.asisten.bantu.voiceaccess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetLabelMatcherTest {

    private fun candidate(
        label: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        actionable: Boolean = false,
        resourceId: String = "",
    ) = TargetLabelMatcher.Candidate(
        value = label,
        label = label,
        resourceId = resourceId,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        actionable = actionable
    )

    @Test
    fun substringInsideAnotherWordDoesNotMatch() {
        val result = TargetLabelMatcher.resolve(
            "call",
            listOf(candidate("Recall Settings", 0, 0, 200, 60, actionable = true))
        )
        assertTrue(result is TargetLabelMatcher.Result.NotFound)
    }

    @Test
    fun exactLabelOutranksLongerPartialLabel() {
        val exact = candidate("Settings", 0, 0, 100, 40, actionable = true)
        val partial = candidate("Settings and privacy", 0, 50, 220, 100, actionable = true)

        val result = TargetLabelMatcher.resolve("settings", listOf(partial, exact))

        assertTrue(result is TargetLabelMatcher.Result.Found)
        assertEquals("Settings", (result as TargetLabelMatcher.Result.Found).candidate.value)
    }

    @Test
    fun nearTiedTargetsAtDifferentLocationsAreAmbiguous() {
        val settings = candidate("Wi-Fi settings", 0, 0, 250, 60, actionable = true)
        val network = candidate("Wi-Fi network", 0, 100, 250, 160, actionable = true)

        val result = TargetLabelMatcher.resolve("wifi", listOf(settings, network))

        assertTrue(result is TargetLabelMatcher.Result.Ambiguous)
        assertEquals(2, (result as TargetLabelMatcher.Result.Ambiguous).candidates.size)
    }

    @Test
    fun overlappingLabelAndControlResolveToSmallerActionableControl() {
        val label = candidate("Wi-Fi", 0, 0, 240, 80, actionable = false)
        val control = candidate("Wi-Fi", 12, 12, 80, 68, actionable = true)

        val result = TargetLabelMatcher.resolve("wi fi", listOf(label, control))

        assertTrue(result is TargetLabelMatcher.Result.Found)
        assertEquals(control, (result as TargetLabelMatcher.Result.Found).candidate)
    }

    @Test
    fun resourceIdIsUsedOnlyForActionableNodes() {
        val passive = candidate("", 0, 0, 100, 50, resourceId = "wifi_toggle")
        val actionable = candidate("", 0, 60, 100, 110, actionable = true, resourceId = "wifi_toggle")

        val result = TargetLabelMatcher.resolve("wifi", listOf(passive, actionable))

        assertTrue(result is TargetLabelMatcher.Result.Found)
        assertEquals(actionable, (result as TargetLabelMatcher.Result.Found).candidate)
    }

    @Test
    fun unicodeWordBoundariesAreRespected() {
        val result = TargetLabelMatcher.resolve(
            "panggil",
            listOf(candidate("Panggilan masuk", 0, 0, 200, 60, actionable = true))
        )
        assertTrue(result is TargetLabelMatcher.Result.NotFound)
    }
    @Test
    fun punctuationSeparatedPrefixMatchesAtARealWordBoundary() {
        val wifi = candidate("Wi-Fi settings", 0, 0, 240, 60, actionable = true)
        val result = TargetLabelMatcher.resolve("wifi", listOf(wifi))
        assertTrue(result is TargetLabelMatcher.Result.Found)
        assertEquals(wifi, (result as TargetLabelMatcher.Result.Found).candidate)
    }

}
