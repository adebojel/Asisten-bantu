package com.asisten.bantu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SosLocationFreshnessTest {
    @Test public void recentFixIsFreshAtBoundary() {
        long now = 1_800_000_000_000L;
        assertTrue(SosLocationFreshness.isFresh(now, now));
        assertTrue(SosLocationFreshness.isFresh(now, now - 120_000L));
    }

    @Test public void olderFixIsNotFresh() {
        long now = 1_800_000_000_000L;
        assertFalse(SosLocationFreshness.isFresh(now, now - 120_001L));
        assertEquals(3L, SosLocationFreshness.ageMinutes(now, now - 120_001L));
    }

    @Test public void invalidOrFutureTimestampIsNotFresh() {
        long now = 1_800_000_000_000L;
        assertFalse(SosLocationFreshness.isFresh(now, 0L));
        assertFalse(SosLocationFreshness.isFresh(now, -1L));
        assertFalse(SosLocationFreshness.isFresh(now, now + 1L));
        assertEquals(-1L, SosLocationFreshness.ageMinutes(now, 0L));
        assertEquals(-1L, SosLocationFreshness.ageMinutes(now, now + 1L));
    }

    @Test public void ageMinutesRoundsUpToAvoidUnderstatingAge() {
        long now = 1_800_000_000_000L;
        assertEquals(1L, SosLocationFreshness.ageMinutes(now, now - 1L));
        assertEquals(2L, SosLocationFreshness.ageMinutes(now, now - 60_001L));
        assertEquals(0L, SosLocationFreshness.ageMinutes(now, now));
    }
}
